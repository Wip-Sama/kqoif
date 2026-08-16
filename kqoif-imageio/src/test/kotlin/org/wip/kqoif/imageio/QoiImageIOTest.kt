package org.wip.kqoif.imageio

import org.wip.kqoif.Color
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QoiImageIOTest {

    @Test
    fun testBufferedImageToQoiImageAndBack() {
        val width = 4
        val height = 4
        val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = (0x80 shl 24) or (x * 50 shl 16) or (y * 50 shl 8) or 100
                buffered.setRGB(x, y, argb)
            }
        }

        val qoiImage = buffered.toQoiImage()
        assertEquals(width.toUInt(), qoiImage.width)
        assertEquals(height.toUInt(), qoiImage.height)
        assertEquals(QoiHeader.CHANNELS_RGBA, qoiImage.channels)

        val reconstructed = qoiImage.toBufferedImage()
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(buffered.getRGB(x, y), reconstructed.getRGB(x, y))
            }
        }
    }

    @Test
    fun testFileReadWriteRoundtrip() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val header = QoiHeader(width = 2u, height = 2u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = listOf(
                Color(255, 0, 0, 255),
                Color(0, 255, 0, 255),
                Color(0, 0, 255, 255),
                Color(255, 255, 0, 128)
            )
            val original = QoiImage(header, pixels)

            val qoiFile = File(tempDir, "sample.qoi")
            val pngFile = File(tempDir, "sample.png")

            // Write to QOI and read back
            QoiImageIO.write(original, qoiFile)
            assertTrue(qoiFile.exists())
            val readQoi = QoiImageIO.read(qoiFile)
            assertEquals(original.pixels, readQoi.pixels)

            // Convert QOI to PNG
            QoiImageIO.write(readQoi, pngFile)
            assertTrue(pngFile.exists())

            // Read PNG back and compare pixels
            val readPng = QoiImageIO.read(pngFile)
            assertEquals(original.pixels, readPng.pixels)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
