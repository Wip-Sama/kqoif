package org.wip.kqoif

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QoiRollingTest {

    @Test
    fun testRollingEncoderDirectMatchesImageEncode() {
        val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val pixels = listOf(
            Color(10, 20, 30, 255), Color(10, 20, 30, 255), Color(11, 20, 30, 255), Color(25, 35, 36, 255),
            Color(200, 100, 50, 128), Color(200, 100, 50, 128), Color(10, 20, 30, 255), Color(0, 0, 0, 255),
            Color(0, 0, 0, 255), Color(0, 0, 0, 255), Color(0, 0, 0, 255), Color(0, 0, 0, 255),
            Color(255, 255, 255, 255), Color(255, 0, 0, 255), Color(0, 255, 0, 255), Color(0, 0, 255, 255)
        )
        val image = QoiImage(header, pixels)
        val expectedBytes = image.encode(QoiEncoderStrategy.DIRECT)

        val buffer = Buffer()
        val encoder = QoiRollingEncoder(header, buffer, QoiEncoderStrategy.DIRECT)
        pixels.forEach { encoder.encodePixel(it) }
        encoder.finish()

        val rollingBytes = buffer.readByteArray()
        assertTrue(expectedBytes.contentEquals(rollingBytes), "Rolling DIRECT encoding must match in-memory DIRECT encoding")
    }

    @Test
    fun testRollingEncoderObjectMatchesImageEncode() {
        val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val pixels = listOf(
            Color(10, 20, 30, 255), Color(10, 20, 30, 255), Color(11, 20, 30, 255), Color(25, 35, 36, 255),
            Color(200, 100, 50, 128), Color(200, 100, 50, 128), Color(10, 20, 30, 255), Color(0, 0, 0, 255),
            Color(0, 0, 0, 255), Color(0, 0, 0, 255), Color(0, 0, 0, 255), Color(0, 0, 0, 255),
            Color(255, 255, 255, 255), Color(255, 0, 0, 255), Color(0, 255, 0, 255), Color(0, 0, 255, 255)
        )
        val image = QoiImage(header, pixels)
        val expectedBytes = image.encode(QoiEncoderStrategy.OBJECT)

        val buffer = Buffer()
        val encoder = QoiRollingEncoder(header, buffer, QoiEncoderStrategy.OBJECT)
        pixels.forEach { encoder.encodePixel(it) }
        encoder.finish()

        val rollingBytes = buffer.readByteArray()
        assertTrue(expectedBytes.contentEquals(rollingBytes), "Rolling OBJECT encoding must match in-memory OBJECT encoding")
    }

    @Test
    fun testRollingDecoderMatchesDecodedPixels() {
        val header = QoiHeader(width = 3u, height = 2u, channels = QoiHeader.CHANNELS_RGB, colorspace = QoiHeader.COLORSPACE_SRGB)
        val pixels = listOf(
            Color(100, 150, 200, 255),
            Color(101, 150, 200, 255),
            Color(115, 160, 205, 255),
            Color(100, 150, 200, 255),
            Color(100, 150, 200, 255),
            Color(50, 60, 70, 255)
        )
        val image = QoiImage(header, pixels)
        val encodedBytes = image.encode()

        val buffer = Buffer()
        buffer.write(encodedBytes)

        val decoder = QoiRollingDecoder(buffer)
        assertEquals(header, decoder.header)

        val decodedPixels = mutableListOf<Color>()
        decoder.decodeEachPixel { _, _, r, g, b, a ->
            decodedPixels.add(Color(r, g, b, a))
        }

        assertEquals(pixels.size, decodedPixels.size)
        assertEquals(pixels, decodedPixels)
    }

    @Test
    fun testRollingEncoderLongRuns() {
        val header = QoiHeader(width = 10u, height = 10u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val pixels = List(100) { Color(42, 42, 42, 255) }
        val image = QoiImage(header, pixels)
        val directBytes = image.encode(QoiEncoderStrategy.DIRECT)
        val objectBytes = image.encode(QoiEncoderStrategy.OBJECT)

        assertTrue(directBytes.contentEquals(objectBytes))

        val buffer = Buffer()
        val encoder = QoiRollingEncoder(header, buffer, QoiEncoderStrategy.DIRECT)
        pixels.forEach { encoder.encodePixel(it) }
        encoder.finish()

        val rollingBytes = buffer.readByteArray()
        assertTrue(directBytes.contentEquals(rollingBytes))
    }
}
