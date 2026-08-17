package org.wip.kqoif

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Streaming / rolling QOI encoder that processes pixels on-the-fly and writes binary chunks
 * directly to a [Sink] without buffering entire images in memory.
 *
 * Supports both [QoiEncoderStrategy.DIRECT] (transitive zero-allocation byte writing)
 * and [QoiEncoderStrategy.OBJECT] (AST object generation).
 *
 * @property header The 14-byte QOI metadata header.
 * @property sink Destination I/O sink where binary QOI bytes are streamed.
 * @property strategy The encoding strategy to use.
 */
class QoiRollingEncoder(
    val header: QoiHeader,
    private val sink: Sink,
    private val strategy: QoiEncoderStrategy = QoiEncoderStrategy.DIRECT
) : AutoCloseable {

    private val seenR: IntArray = IntArray(64)
    private val seenG: IntArray = IntArray(64)
    private val seenB: IntArray = IntArray(64)
    private val seenA: IntArray = IntArray(64)
    private var prevR: Int = 0
    private var prevG: Int = 0
    private var prevB: Int = 0
    private var prevA: Int = 255
    private var run: Int = 0
    private var isFinished: Boolean = false
    private val tempBuffer: ByteArray = ByteArray(14)

    init {
        require(header.isValid()) { "Invalid QOI header for rolling encoder." }
        if (strategy == QoiEncoderStrategy.DIRECT) {
            val written = QoiHeader.writeBytes(header, tempBuffer, 0)
            sink.write(tempBuffer, 0, written)
        } else {
            sink.write(header.toBytes())
        }
    }

    constructor(
        width: UInt,
        height: UInt,
        channels: UByte = QoiHeader.CHANNELS_RGBA,
        colorspace: UByte = QoiHeader.COLORSPACE_SRGB,
        sink: Sink,
        strategy: QoiEncoderStrategy = QoiEncoderStrategy.DIRECT
    ) : this(QoiHeader(width, height, channels, colorspace), sink, strategy)

    /**
     * Encodes a single pixel given its red, green, blue, and alpha channel integer values (0..255).
     */
    fun encodePixel(r: Int, g: Int, b: Int, a: Int = 255) {
        check(!isFinished) { "Cannot encode pixel after encoder has finished." }

        if (strategy == QoiEncoderStrategy.DIRECT) {
            encodePixelDirect(r, g, b, a)
        } else {
            encodePixelObject(r, g, b, a)
        }
    }

    /**
     * Encodes a single [Color] pixel.
     */
    fun encodePixel(pixel: Color) {
        encodePixel(pixel.r, pixel.g, pixel.b, pixel.a)
    }

    private fun encodePixelDirect(r: Int, g: Int, b: Int, a: Int) {
        if (prevR == r && prevG == g && prevB == b && prevA == a) {
            run++
            if (run == QoiOpRun.MAX_RUN.toInt()) {
                val len = QoiOpRun.writeBytes(run, tempBuffer, 0)
                sink.write(tempBuffer, 0, len)
                run = 0
            }
            return
        }

        if (run > 0) {
            val len = QoiOpRun.writeBytes(run, tempBuffer, 0)
            sink.write(tempBuffer, 0, len)
            run = 0
        }

        val hash = (r * 3 + g * 5 + b * 7 + a * 11) and 63
        if (seenR[hash] == r && seenG[hash] == g && seenB[hash] == b && seenA[hash] == a) {
            val len = QoiOpIndex.writeBytes(hash, tempBuffer, 0)
            sink.write(tempBuffer, 0, len)
            prevR = r; prevG = g; prevB = b; prevA = a
            return
        }
        seenR[hash] = r; seenG[hash] = g; seenB[hash] = b; seenA[hash] = a

        if (a == prevA) {
            val vr = r - prevR
            val vg = g - prevG
            val vb = b - prevB

            if (QoiOpDiff.canEncode(vr, vg, vb)) {
                val len = QoiOpDiff.writeBytes(vr, vg, vb, tempBuffer, 0)
                sink.write(tempBuffer, 0, len)
                prevR = r; prevG = g; prevB = b; prevA = a
                return
            }

            val dr_dg = vr - vg
            val db_dg = vb - vg
            if (QoiOpLuma.canEncode(vg, dr_dg, db_dg)) {
                val len = QoiOpLuma.writeBytes(vg, dr_dg, db_dg, tempBuffer, 0)
                sink.write(tempBuffer, 0, len)
                prevR = r; prevG = g; prevB = b; prevA = a
                return
            }
        }

        if (header.channels == QoiHeader.CHANNELS_RGBA) {
            val len = QoiOpRgba.writeBytes(r, g, b, a, tempBuffer, 0)
            sink.write(tempBuffer, 0, len)
        } else {
            val len = QoiOpRgb.writeBytes(r, g, b, tempBuffer, 0)
            sink.write(tempBuffer, 0, len)
        }
        prevR = r; prevG = g; prevB = b; prevA = a
    }

    private fun encodePixelObject(r: Int, g: Int, b: Int, a: Int) {
        if (prevR == r && prevG == g && prevB == b && prevA == a) {
            run++
            if (run == QoiOpRun.MAX_RUN.toInt()) {
                sink.write(QoiOpRun(run = run).toBytes())
                run = 0
            }
            return
        }

        if (run > 0) {
            sink.write(QoiOpRun(run = run).toBytes())
            run = 0
        }

        val hash = (r * 3 + g * 5 + b * 7 + a * 11) and 63
        if (seenR[hash] == r && seenG[hash] == g && seenB[hash] == b && seenA[hash] == a) {
            sink.write(QoiOpIndex(index = hash).toBytes())
            prevR = r; prevG = g; prevB = b; prevA = a
            return
        }
        seenR[hash] = r; seenG[hash] = g; seenB[hash] = b; seenA[hash] = a

        if (a == prevA) {
            val vr = r - prevR
            val vg = g - prevG
            val vb = b - prevB

            if (QoiOpDiff.canEncode(vr, vg, vb)) {
                sink.write(QoiOpDiff(dr = vr, dg = vg, db = vb).toBytes())
                prevR = r; prevG = g; prevB = b; prevA = a
                return
            }
            val dr_dg = vr - vg
            val db_dg = vb - vg
            if (QoiOpLuma.canEncode(vg, dr_dg, db_dg)) {
                sink.write(QoiOpLuma(dg = vg, dr_dg = dr_dg, db_dg = db_dg).toBytes())
                prevR = r; prevG = g; prevB = b; prevA = a
                return
            }
        }

        if (header.channels == QoiHeader.CHANNELS_RGBA) {
            sink.write(QoiOpRgba(red = r, green = g, blue = b, alpha = a).toBytes())
        } else {
            sink.write(QoiOpRgb(red = r, green = g, blue = b).toBytes())
        }
        prevR = r; prevG = g; prevB = b; prevA = a
    }


    /**
     * Finishes encoding: flushes any remaining run-length chunk and writes the 8-byte QOI terminator.
     */
    fun finish() {
        if (isFinished) return
        isFinished = true

        if (run > 0) {
            if (strategy == QoiEncoderStrategy.DIRECT) {
                val len = QoiOpRun.writeBytes(run, tempBuffer, 0)
                sink.write(tempBuffer, 0, len)
            } else {
                sink.write(QoiOpRun(run = run).toBytes())
            }
            run = 0
        }

        sink.write(QoiImage.TERMINATOR)
        sink.flush()
    }

    override fun close() {
        finish()
    }
}

/**
 * Streaming / rolling QOI decoder that reads pixels sequentially from a [Source]
 * without constructing a full pixel list in memory.
 */
class QoiRollingDecoder(
    private val source: Source
) {
    /** The decoded image header. */
    val header: QoiHeader

    init {
        val headerBytes = source.readByteArray(QoiHeader.CHUNK_SIZE)
        header = QoiHeader.fromBytes(headerBytes, 0)
        require(header.isValid()) { "Invalid QOI header in stream." }
    }

    /**
     * Decodes pixels sequentially and invokes [onPixel] for every pixel coordinate `(x, y)` and `(r, g, b, a)`.
     */
    fun decodeEachPixel(onPixel: (x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) -> Unit) {
        val totalPixels = header.width.toInt() * header.height.toInt()
        val width = header.width.toInt()
        val seenPixels: Array<Color> = Array(64) { Color(0, 0, 0, 0) }
        var prevPixel = Color(0, 0, 0, 255)
        var pixelIndex = 0

        val chunkBuffer = ByteArray(5)

        while (pixelIndex < totalPixels) {
            val firstByte = source.readByte()
            val firstUByte = firstByte.toUByte()
            val firstUInt = firstUByte.toUInt()

            if (firstByte == 0xFE.toByte()) { // QOI_OP_RGB
                val r = source.readByte().toInt() and 0xFF
                val g = source.readByte().toInt() and 0xFF
                val b = source.readByte().toInt() and 0xFF
                val pixel = Color(r, g, b, prevPixel.a)
                val x = pixelIndex % width
                val y = pixelIndex / width
                onPixel(x, y, pixel.r, pixel.g, pixel.b, pixel.a)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                pixelIndex++
            } else if (firstByte == 0xFF.toByte()) { // QOI_OP_RGBA
                val r = source.readByte().toInt() and 0xFF
                val g = source.readByte().toInt() and 0xFF
                val b = source.readByte().toInt() and 0xFF
                val a = source.readByte().toInt() and 0xFF
                val pixel = Color(r, g, b, a)
                val x = pixelIndex % width
                val y = pixelIndex / width
                onPixel(x, y, pixel.r, pixel.g, pixel.b, pixel.a)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                pixelIndex++
            } else {
                val tag = (firstUInt and 0xC0u) shr 6
                when (tag) {
                    0x00u -> { // QOI_OP_INDEX
                        val index = (firstUInt and 0x3Fu).toInt()
                        val pixel = seenPixels[index]
                        val x = pixelIndex % width
                        val y = pixelIndex / width
                        onPixel(x, y, pixel.r, pixel.g, pixel.b, pixel.a)
                        prevPixel = pixel
                        pixelIndex++
                    }
                    0x01u -> { // QOI_OP_DIFF
                        val dr = (((firstUInt shr 4) and 0x03u).toInt() - 2)
                        val dg = (((firstUInt shr 2) and 0x03u).toInt() - 2)
                        val db = ((firstUInt and 0x03u).toInt() - 2)
                        val r = (prevPixel.r + dr) and 0xFF
                        val g = (prevPixel.g + dg) and 0xFF
                        val b = (prevPixel.b + db) and 0xFF
                        val pixel = Color(r, g, b, prevPixel.a)
                        val x = pixelIndex % width
                        val y = pixelIndex / width
                        onPixel(x, y, pixel.r, pixel.g, pixel.b, pixel.a)
                        seenPixels[pixel.toHash()] = pixel
                        prevPixel = pixel
                        pixelIndex++
                    }
                    0x02u -> { // QOI_OP_LUMA
                        val secondByte = source.readByte().toUByte().toUInt()
                        val dg = ((firstUInt and 0x3Fu).toInt() - 32)
                        val dr_dg = (((secondByte shr 4) and 0x0Fu).toInt() - 8)
                        val db_dg = ((secondByte and 0x0Fu).toInt() - 8)
                        val r = (prevPixel.r + dg + dr_dg) and 0xFF
                        val g = (prevPixel.g + dg) and 0xFF
                        val b = (prevPixel.b + dg + db_dg) and 0xFF
                        val pixel = Color(r, g, b, prevPixel.a)
                        val x = pixelIndex % width
                        val y = pixelIndex / width
                        onPixel(x, y, pixel.r, pixel.g, pixel.b, pixel.a)
                        seenPixels[pixel.toHash()] = pixel
                        prevPixel = pixel
                        pixelIndex++
                    }
                    0x03u -> { // QOI_OP_RUN
                        val runLength = ((firstUInt and 0x3Fu).toInt() + 1)
                        for (k in 0 until runLength) {
                            val x = pixelIndex % width
                            val y = pixelIndex / width
                            onPixel(x, y, prevPixel.r, prevPixel.g, prevPixel.b, prevPixel.a)
                            pixelIndex++
                        }
                    }
                }
            }
        }

        // Read and verify 8-byte terminator
        val termBytes = source.readByteArray(QoiImage.TERMINATOR.size)
        require(termBytes.contentEquals(QoiImage.TERMINATOR)) {
            "Stream does not end with expected QOI terminator."
        }
    }
}
