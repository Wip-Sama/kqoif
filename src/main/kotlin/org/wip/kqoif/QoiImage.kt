package org.wip.kqoif

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.util.logging.Logger

/**
 * Represents an RGBA Color used in QOI images.
 *
 * @property r Red channel (0..255).
 * @property g Green channel (0..255).
 * @property b Blue channel (0..255).
 * @property a Alpha channel (0..255, defaults to 255 for opaque).
 */
data class Color(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int = 255
) {
    /**
     * Computes the 6-bit hash index position (0..63) for this color in the QOI running array.
     */
    fun toHash(): Int {
        return ((r and 0xFF) * 3 + (g and 0xFF) * 5 + (b and 0xFF) * 7 + (a and 0xFF) * 11) % 64
    }

    /**
     * Converts this color to a [QoiOpRgb] chunk.
     */
    fun toQoiRgb(): QoiOpRgb {
        return QoiOpRgb(r.toUByte(), g.toUByte(), b.toUByte())
    }

    /**
     * Converts this color to a [QoiOpRgba] chunk.
     */
    fun toQoiRgba(): QoiOpRgba {
        return QoiOpRgba(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte())
    }
}

/**
 * Represents a decoded or in-memory QOI image consisting of a [header] and a list of [pixels].
 *
 * @property header The 14-byte QOI metadata header.
 * @property pixels The pixel color data in row-major order.
 */
data class QoiImage(
    val header: QoiHeader,
    val pixels: List<Color>
) {
    companion object {
        private val logger: Logger = Logger.getLogger(QoiImage::class.java.name)

        /** The standard 8-byte end marker padding for QOI files (`0x00` x7 + `0x01`). */
        val TERMINATOR: ByteArray = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)

        /**
         * Validates that the [bytes] array meets basic plausibility checks for a QOI byte stream.
         *
         * @throws IllegalArgumentException if the buffer is too short or lacks the standard terminator.
         */
        fun byteArrayIsPlausible(bytes: ByteArray) {
            require(bytes.size >= QoiHeader.HEADER_SIZE + TERMINATOR.size) {
                "Byte array is too short to be a valid QOI image. Expected at least ${QoiHeader.HEADER_SIZE + TERMINATOR.size} bytes, got ${bytes.size}."
            }
            val hasTerminator = bytes.copyOfRange(bytes.size - TERMINATOR.size, bytes.size).contentEquals(TERMINATOR)
            require(hasTerminator) {
                "Byte array does not end with the expected QOI terminator."
            }
        }

        /**
         * Decodes a raw QOI binary [bytes] array into a [QoiImage].
         *
         * @param bytes The raw QOI byte array.
         * @return The decoded [QoiImage].
         * @throws IllegalArgumentException if the byte stream is malformed or invalid.
         */
        fun decode(bytes: ByteArray): QoiImage {
            byteArrayIsPlausible(bytes)

            var offset = 0
            val header = QoiHeader.fromBytes(bytes, offset)
            offset += QoiHeader.HEADER_SIZE

            val totalPixels = header.width.toInt() * header.height.toInt()
            val pixels = ArrayList<Color>(totalPixels)
            val seenPixels: Array<Color> = Array(64) { Color(0, 0, 0, 0) }
            var prevPixel = Color(0, 0, 0, 255)

            while (pixels.size < totalPixels && offset <= bytes.size - TERMINATOR.size) {
                if (QoiOpRgb.matchTag(bytes, offset)) {
                    val op = QoiOpRgb.fromBytes(bytes, offset)
                    offset += QoiOpRgb.CHUNK_SIZE
                    val pixel = op.toColor(prevPixel)
                    pixels.add(pixel)
                    seenPixels[pixel.toHash()] = pixel
                    prevPixel = pixel
                } else if (QoiOpRgba.matchTag(bytes, offset)) {
                    val op = QoiOpRgba.fromBytes(bytes, offset)
                    offset += QoiOpRgba.CHUNK_SIZE
                    val pixel = op.toColor()
                    pixels.add(pixel)
                    seenPixels[pixel.toHash()] = pixel
                    prevPixel = pixel
                } else if (QoiOpIndex.matchTag(bytes, offset)) {
                    val op = QoiOpIndex.fromBytes(bytes, offset)
                    offset += QoiOpIndex.CHUNK_SIZE
                    val pixel = seenPixels[op.index.toInt()]
                    pixels.add(pixel)
                    prevPixel = pixel
                } else if (QoiOpDiff.matchTag(bytes, offset)) {
                    val op = QoiOpDiff.fromBytes(bytes, offset)
                    offset += QoiOpDiff.CHUNK_SIZE
                    val pixel = op.toColor(prevPixel)
                    pixels.add(pixel)
                    seenPixels[pixel.toHash()] = pixel
                    prevPixel = pixel
                } else if (QoiOpLuma.matchTag(bytes, offset)) {
                    val op = QoiOpLuma.fromBytes(bytes, offset)
                    offset += QoiOpLuma.CHUNK_SIZE
                    val pixel = op.toColor(prevPixel)
                    pixels.add(pixel)
                    seenPixels[pixel.toHash()] = pixel
                    prevPixel = pixel
                } else if (QoiOpRun.matchTag(bytes, offset)) {
                    val op = QoiOpRun.fromBytes(bytes, offset)
                    offset += QoiOpRun.CHUNK_SIZE
                    repeat(op.run.toInt()) {
                        pixels.add(prevPixel)
                    }
                } else {
                    throw IllegalArgumentException("Unknown QOI chunk tag at offset $offset (byte: 0x%02X).".format(bytes[offset]))
                }
            }

            require(pixels.size == totalPixels) {
                "Decoded pixel count (${pixels.size}) does not match header dimensions (${header.width}x${header.height} = $totalPixels)."
            }

            return QoiImage(header, pixels)
        }
    }

    /** Image height in pixels from the header. */
    val height: UInt get() = header.height

    /** Image width in pixels from the header. */
    val width: UInt get() = header.width

    /** Image channel count (3 for RGB, 4 for RGBA). */
    val channels: UByte get() = header.channels

    /** Image colorspace (0 for sRGB, 1 for Linear). */
    val colorspace: UByte get() = header.colorspace

    /**
     * Checks if this image is valid according to header specifications and pixel count.
     */
    fun isValid(): Boolean {
        return header.isValid() && pixels.size == header.width.toInt() * header.height.toInt()
    }

    /**
     * Encodes this image into a raw QOI binary [ByteArray].
     *
     * @return Byte array containing the encoded QOI image.
     * @throws IllegalArgumentException if the image is malformed or pixel count doesn't match dimensions.
     */
    fun encode(): ByteArray {
        require(isValid()) {
            "Malformed QOI image: expected ${header.width.toInt() * header.height.toInt()} pixels, got ${pixels.size}."
        }

        val buffer = Buffer()
        buffer.write(header.toBytes())

        val seenPixels: Array<Color> = Array(64) { Color(0, 0, 0, 0) }
        var prevPixel = Color(0, 0, 0, 255)
        var run = 0

        pixels.forEach { pixel ->
            if (prevPixel == pixel) {
                run++
                if (run == QoiOpRun.MAX_RUN.toInt()) {
                    buffer.write(QoiOpRun(run = run).toBytes())
                    run = 0
                }
                return@forEach
            } else {
                if (run > 0) {
                    buffer.write(QoiOpRun(run = run).toBytes())
                    run = 0
                }
            }

            seenPixels[pixel.toHash()].let { seenPixel ->
                if (seenPixel == pixel) {
                    buffer.write(QoiOpIndex(index = pixel.toHash()).toBytes())
                    prevPixel = pixel
                    return@forEach
                }
            }
            seenPixels[pixel.toHash()] = pixel

            if (pixel.a == prevPixel.a) {
                QoiOpDiff.fromColors(prevPixel, pixel)?.let {
                    buffer.write(it.toBytes())
                    prevPixel = pixel
                    return@forEach
                }
                QoiOpLuma.fromColors(prevPixel, pixel)?.let {
                    buffer.write(it.toBytes())
                    prevPixel = pixel
                    return@forEach
                }
            }

            if (header.channels == QoiHeader.CHANNELS_RGBA) {
                buffer.write(pixel.toQoiRgba().toBytes())
            } else {
                buffer.write(pixel.toQoiRgb().toBytes())
            }
            prevPixel = pixel
        }

        if (run > 0) {
            buffer.write(QoiOpRun(run = run).toBytes())
        }

        buffer.write(TERMINATOR)
        return buffer.readByteArray()
    }

    /**
     * Decodes a raw QOI binary [bytes] array into a [QoiImage].
     */
    fun decode(bytes: ByteArray): QoiImage = Companion.decode(bytes)
}