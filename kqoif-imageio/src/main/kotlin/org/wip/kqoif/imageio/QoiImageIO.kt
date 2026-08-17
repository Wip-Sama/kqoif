package org.wip.kqoif.imageio

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.wip.kqoif.Color
import org.wip.kqoif.QoiEncoderStrategy
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage
import org.wip.kqoif.QoiRollingDecoder
import org.wip.kqoif.QoiRollingEncoder
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.imageio.ImageIO

/**
 * Extension to convert a standard Java [BufferedImage] into a [QoiImage].
 *
 * @param colorspace Color space for the QOI header (0 for sRGB with linear alpha, 1 for linear).
 * @return [QoiImage] representing the converted pixel data.
 */
fun BufferedImage.toQoiImage(colorspace: UByte = QoiHeader.COLORSPACE_SRGB): QoiImage {
    val width = this.width.toUInt()
    val height = this.height.toUInt()
    val hasAlpha = this.colorModel.hasAlpha()
    val channels = if (hasAlpha) QoiHeader.CHANNELS_RGBA else QoiHeader.CHANNELS_RGB

    val header = QoiHeader(
        width = width,
        height = height,
        channels = channels,
        colorspace = colorspace
    )

    val totalPixels = this.width * this.height
    val pixels = ArrayList<Color>(totalPixels)

    for (y in 0 until this.height) {
        for (x in 0 until this.width) {
            val argb = this.getRGB(x, y)
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            pixels.add(Color(r, g, b, if (hasAlpha) a else 255))
        }
    }

    return QoiImage(header, pixels)
}

/**
 * Encodes a [BufferedImage] directly into a QOI byte array using rolling scanline streaming.
 * Avoids creating intermediate lists of [Color] pixel objects.
 *
 * @param strategy The encoding strategy ([QoiEncoderStrategy.DIRECT] or [QoiEncoderStrategy.OBJECT]).
 * @param colorspace Color space for the QOI header (0 for sRGB with linear alpha, 1 for linear).
 * @return Byte array containing the encoded QOI image.
 */
fun BufferedImage.toQoiRollingBytes(
    strategy: QoiEncoderStrategy = QoiEncoderStrategy.DIRECT,
    colorspace: UByte = QoiHeader.COLORSPACE_SRGB
): ByteArray {
    val width = this.width.toUInt()
    val height = this.height.toUInt()
    val hasAlpha = this.colorModel.hasAlpha()
    val channels = if (hasAlpha) QoiHeader.CHANNELS_RGBA else QoiHeader.CHANNELS_RGB

    val header = QoiHeader(width, height, channels, colorspace)
    val buffer = Buffer()
    val encoder = QoiRollingEncoder(header, buffer, strategy)

    val w = this.width
    val h = this.height
    val row = IntArray(w)

    for (y in 0 until h) {
        this.getRGB(0, y, w, 1, row, 0, w)
        for (x in 0 until w) {
            val argb = row[x]
            val a = if (hasAlpha) (argb ushr 24) and 0xFF else 255
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            encoder.encodePixel(r, g, b, a)
        }
    }

    encoder.finish()
    return buffer.readByteArray()
}

/**
 * Extension to convert a [QoiImage] into a standard Java [BufferedImage].
 *
 * @return [BufferedImage] with TYPE_INT_ARGB (for 4-channel images) or TYPE_INT_RGB (for 3-channel images).
 */
fun QoiImage.toBufferedImage(): BufferedImage {
    val isRgba = this.channels == QoiHeader.CHANNELS_RGBA
    val imageType = if (isRgba) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    val w = this.width.toInt()
    val h = this.height.toInt()
    val bufferedImage = BufferedImage(w, h, imageType)

    for (index in pixels.indices) {
        val pixel = pixels[index]
        val x = index % w
        val y = index / w
        val argb = (pixel.a shl 24) or (pixel.r shl 16) or (pixel.g shl 8) or pixel.b
        bufferedImage.setRGB(x, y, argb)
    }

    return bufferedImage
}

/**
 * Utility for reading and writing QOI images and converting between standard formats (PNG, JPG, BMP, WEBP, TIFF).
 */
object QoiImageIO {

    /**
     * Reads an image file. If the file has a `.qoi` extension, it decodes using QOI format.
     * Otherwise, it reads using [ImageIO] and converts to [QoiImage].
     *
     * @param file The image file to read.
     * @return The decoded [QoiImage].
     */
    fun read(file: File): QoiImage {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        val extension = file.extension.lowercase()

        return if (extension == "qoi") {
            val bytes = FileInputStream(file).use { it.readAllBytes() }
            QoiImage.decode(bytes)
        } else {
            val buffered = ImageIO.read(file) ?: throw IllegalArgumentException("Unsupported or unreadable image format: ${file.name}")
            buffered.toQoiImage()
        }
    }

    /**
     * Writes a [QoiImage] to disk. If the output file has a `.qoi` extension, it encodes to binary QOI
     * using the specified [strategy].
     * Otherwise, it writes using [ImageIO] with the file extension format (e.g. `png`, `jpg`, `bmp`).
     *
     * @param image The [QoiImage] to write.
     * @param file The destination file.
     * @param strategy The encoding strategy ([QoiEncoderStrategy.DIRECT] or [QoiEncoderStrategy.OBJECT]).
     */
    fun write(
        image: QoiImage,
        file: File,
        strategy: QoiEncoderStrategy = QoiEncoderStrategy.DIRECT
    ) {
        val extension = file.extension.lowercase()
        if (extension == "qoi") {
            val bytes = image.encode(strategy)
            FileOutputStream(file).use { it.write(bytes) }
        } else {
            val buffered = image.toBufferedImage()
            val formatName = if (extension.isNotEmpty()) extension else "png"
            val success = ImageIO.write(buffered, formatName, file)
            require(success) { "Failed to write image format '$formatName' to ${file.absolutePath}" }
        }
    }

    /**
     * Converts between image formats using rolling scanline streaming to minimize memory consumption.
     *
     * @param input Input image file.
     * @param output Destination image file.
     * @param strategy Encoding strategy to use for QOI output ([QoiEncoderStrategy.DIRECT] or [QoiEncoderStrategy.OBJECT]).
     */
    fun convertRolling(
        input: File,
        output: File,
        strategy: QoiEncoderStrategy = QoiEncoderStrategy.DIRECT
    ) {
        require(input.exists()) { "Input file does not exist: ${input.absolutePath}" }
        val inExt = input.extension.lowercase()
        val outExt = output.extension.lowercase()

        if (inExt != "qoi" && outExt == "qoi") {
            val buffered = ImageIO.read(input) ?: throw IllegalArgumentException("Unsupported or unreadable image format: ${input.name}")
            val qoiBytes = buffered.toQoiRollingBytes(strategy = strategy)
            FileOutputStream(output).use { it.write(qoiBytes) }
        } else if (inExt == "qoi" && outExt != "qoi") {
            val bytes = FileInputStream(input).use { it.readAllBytes() }
            val buffer = Buffer()
            buffer.write(bytes)
            val decoder = QoiRollingDecoder(buffer)
            val isRgba = decoder.header.channels == QoiHeader.CHANNELS_RGBA
            val imageType = if (isRgba) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            val w = decoder.header.width.toInt()
            val h = decoder.header.height.toInt()
            val bufferedImage = BufferedImage(w, h, imageType)

            decoder.decodeEachPixel { x, y, r, g, b, a ->
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                bufferedImage.setRGB(x, y, argb)
            }

            val formatName = if (outExt.isNotEmpty()) outExt else "png"
            val success = ImageIO.write(bufferedImage, formatName, output)
            require(success) { "Failed to write image format '$formatName' to ${output.absolutePath}" }
        } else if (inExt == "qoi" && outExt == "qoi") {
            val image = read(input)
            write(image, output, strategy)
        } else {
            val buffered = ImageIO.read(input) ?: throw IllegalArgumentException("Unsupported or unreadable image format: ${input.name}")
            val formatName = if (outExt.isNotEmpty()) outExt else "png"
            val success = ImageIO.write(buffered, formatName, output)
            require(success) { "Failed to write image format '$formatName' to ${output.absolutePath}" }
        }
    }
}
