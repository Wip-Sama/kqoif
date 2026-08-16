package org.wip.kqoif.imageio

import org.wip.kqoif.Color
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage
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
 * Utility for reading and writing QOI images and converting between standard formats (PNG, JPG, BMP).
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
     * Writes a [QoiImage] to disk. If the output file has a `.qoi` extension, it encodes to binary QOI.
     * Otherwise, it writes using [ImageIO] with the file extension format (e.g. `png`, `jpg`, `bmp`).
     *
     * @param image The [QoiImage] to write.
     * @param file The destination file.
     */
    fun write(image: QoiImage, file: File) {
        val extension = file.extension.lowercase()
        if (extension == "qoi") {
            val bytes = image.encode()
            FileOutputStream(file).use { it.write(bytes) }
        } else {
            val buffered = image.toBufferedImage()
            val formatName = if (extension.isNotEmpty()) extension else "png"
            val success = ImageIO.write(buffered, formatName, file)
            require(success) { "Failed to write image format '$formatName' to ${file.absolutePath}" }
        }
    }
}
