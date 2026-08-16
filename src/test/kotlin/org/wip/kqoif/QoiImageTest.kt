package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QoiImageTest {

    @Test
    fun testEncodeAndDecodeSinglePixelRgba() {
        val header = QoiHeader(width = 1u, height = 1u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val original = QoiImage(header, listOf(Color(100, 150, 200, 128)))

        val encoded = original.encode()
        assertEquals(27, encoded.size) // 14 (header) + 5 (RGBA) + 8 (terminator)

        val decoded = QoiImage.decode(encoded)
        assertEquals(original.header, decoded.header)
        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeSinglePixelRgb() {
        val header = QoiHeader(width = 1u, height = 1u, channels = QoiHeader.CHANNELS_RGB, colorspace = QoiHeader.COLORSPACE_SRGB)
        val original = QoiImage(header, listOf(Color(100, 150, 200, 255)))

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(original.header, decoded.header)
        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeRuns() {
        // 100 identical pixels (exceeds single max run of 62, tests multiple run chunks)
        val header = QoiHeader(width = 10u, height = 10u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val pixels = List(100) { Color(0, 0, 0, 255) }
        val original = QoiImage(header, pixels)

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(100, decoded.pixels.size)
        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeDiff() {
        val header = QoiHeader(width = 3u, height = 1u, channels = QoiHeader.CHANNELS_RGB, colorspace = QoiHeader.COLORSPACE_SRGB)
        // prevPixel begins at (0, 0, 0, 255)
        // pixel 0: (1, 0, -1) -> diff dr=+1, dg=0, db=-1
        // pixel 1: (2, 1, 0)  -> diff dr=+1, dg=+1, db=+1
        // pixel 2: (0, -1, -2)-> diff dr=-2, dg=-2, db=-2
        val pixels = listOf(
            Color(1, 0, 255, 255),
            Color(2, 1, 0, 255),
            Color(0, 255, 254, 255)
        )
        val original = QoiImage(header, pixels)

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeLuma() {
        val header = QoiHeader(width = 2u, height = 1u, channels = QoiHeader.CHANNELS_RGB, colorspace = QoiHeader.COLORSPACE_SRGB)
        // prevPixel begins at (0, 0, 0, 255)
        // pixel 0: dg = 10, dr_dg = -2 (dr = 8), db_dg = 3 (db = 13)
        // pixel 1: dg = -20, dr_dg = 4 (dr = -16), db_dg = -5 (db = -25)
        val pixels = listOf(
            Color(8, 10, 13, 255),
            Color(248, 246, 244, 255)
        )
        val original = QoiImage(header, pixels)

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeIndex() {
        val header = QoiHeader(width = 4u, height = 1u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val colorA = Color(12, 34, 56, 78)
        val colorB = Color(90, 12, 34, 56)
        // Alternating pattern: A, B, A, B -> second A and second B must hit index lookup
        val pixels = listOf(colorA, colorB, colorA, colorB)
        val original = QoiImage(header, pixels)

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testEncodeAndDecodeAllOperationsCombined() {
        val header = QoiHeader(width = 8u, height = 2u, channels = QoiHeader.CHANNELS_RGBA, colorspace = QoiHeader.COLORSPACE_SRGB)
        val c1 = Color(50, 60, 70, 255)
        val c2 = Color(51, 60, 69, 255)  // DIFF from c1
        val c3 = Color(65, 75, 76, 255)  // LUMA from c2
        val c4 = Color(200, 100, 50, 100) // RGBA (alpha change)
        val pixels = listOf(
            c1, c1, c1, // RUN
            c2,         // DIFF
            c3,         // LUMA
            c4,         // RGBA
            c1,         // INDEX (matches c1 from earlier)
            c4,         // INDEX (matches c4 from earlier)
            Color(10, 20, 30, 255), // RGB
            Color(10, 20, 30, 255),
            Color(10, 20, 30, 255), // RUN
            Color(11, 20, 30, 255), // DIFF
            Color(25, 35, 36, 255), // LUMA
            c1,                     // INDEX
            Color(255, 0, 0, 128),  // RGBA
            Color(255, 0, 0, 128)   // RUN
        )
        assertEquals(16, pixels.size)
        val original = QoiImage(header, pixels)

        val encoded = original.encode()
        val decoded = QoiImage.decode(encoded)

        assertEquals(original.header, decoded.header)
        assertEquals(original.pixels, decoded.pixels)
    }

    @Test
    fun testDecodeInvalidTerminatorThrowsException() {
        val header = QoiHeader(width = 1u, height = 1u, channels = 4u, colorspace = 0u)
        val original = QoiImage(header, listOf(Color(10, 20, 30, 40)))
        val encoded = original.encode()

        // Corrupt terminator byte
        encoded[encoded.size - 1] = 0x00

        assertFailsWith<IllegalArgumentException> {
            QoiImage.decode(encoded)
        }
    }

    @Test
    fun testDecodeBufferTooShortThrowsException() {
        val shortBytes = byteArrayOf(0x71, 0x6F, 0x69, 0x66)
        assertFailsWith<IllegalArgumentException> {
            QoiImage.decode(shortBytes)
        }
    }

    @Test
    fun testInvalidImageEncodeThrowsException() {
        val header = QoiHeader(width = 2u, height = 2u, channels = 4u, colorspace = 0u)
        // 3 pixels instead of 4
        val image = QoiImage(header, listOf(Color(0, 0, 0, 255), Color(0, 0, 0, 255), Color(0, 0, 0, 255)))

        assertFailsWith<IllegalArgumentException> {
            image.encode()
        }
    }
}
