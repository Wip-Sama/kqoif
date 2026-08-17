package org.wip.kqoif.imageio

import org.wip.kqoif.Color
import org.wip.kqoif.QoiEncoderStrategy
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QoiImageIORollingTest {

    @Test
    fun testRollingBytesMatchesInMemoryEncode() {
        val width = 8
        val height = 8
        val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = ((x * 30) shl 24) or ((x * 20) shl 16) or ((y * 20) shl 8) or ((x + y) * 10)
                buffered.setRGB(x, y, argb)
            }
        }

        val qoiImage = buffered.toQoiImage()
        val expectedDirect = qoiImage.encode(QoiEncoderStrategy.DIRECT)
        val expectedObject = qoiImage.encode(QoiEncoderStrategy.OBJECT)

        val rollingDirect = buffered.toQoiRollingBytes(QoiEncoderStrategy.DIRECT)
        val rollingObject = buffered.toQoiRollingBytes(QoiEncoderStrategy.OBJECT)

        assertTrue(expectedDirect.contentEquals(rollingDirect), "Rolling direct bytes must match in-memory direct bytes")
        assertTrue(expectedObject.contentEquals(rollingObject), "Rolling object bytes must match in-memory object bytes")
        assertTrue(rollingDirect.contentEquals(rollingObject), "Rolling direct and object bytes must be identical")
    }

    @Test
    fun testConvertRollingPngToQoiAndBack() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_rolling_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val pngFile = File(tempDir, "input.png")
            val qoiDirectFile = File(tempDir, "output_direct.qoi")
            val qoiObjectFile = File(tempDir, "output_object.qoi")
            val reconstructedPngFile = File(tempDir, "reconstructed.png")

            val buffered = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val a = 255
                    val r = (x * 15) and 0xFF
                    val g = (y * 15) and 0xFF
                    val b = ((x + y) * 7) and 0xFF
                    buffered.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            ImageIO.write(buffered, "png", pngFile)

            // Convert PNG -> QOI (Direct)
            QoiImageIO.convertRolling(pngFile, qoiDirectFile, QoiEncoderStrategy.DIRECT)
            assertTrue(qoiDirectFile.exists())

            // Convert PNG -> QOI (Object)
            QoiImageIO.convertRolling(pngFile, qoiObjectFile, QoiEncoderStrategy.OBJECT)
            assertTrue(qoiObjectFile.exists())

            // Confirm byte-for-byte exact equality between DIRECT and OBJECT rolling conversion
            val directBytes = qoiDirectFile.readBytes()
            val objectBytes = qoiObjectFile.readBytes()
            assertTrue(directBytes.contentEquals(objectBytes), "DIRECT and OBJECT rolling conversion must be byte-for-byte identical")

            // Convert QOI -> PNG
            QoiImageIO.convertRolling(qoiDirectFile, reconstructedPngFile)
            assertTrue(reconstructedPngFile.exists())

            val reloaded = ImageIO.read(reconstructedPngFile)
            assertEquals(16, reloaded.width)
            assertEquals(16, reloaded.height)
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    assertEquals(buffered.getRGB(x, y), reloaded.getRGB(x, y), "Pixel mismatch at ($x, $y)")
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
