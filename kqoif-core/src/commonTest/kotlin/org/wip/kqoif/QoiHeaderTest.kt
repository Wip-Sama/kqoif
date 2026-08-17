package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiHeaderTest {

    @Test
    fun testDefaultMagicInitialization() {
        val header = QoiHeader(
            width = 1920u,
            height = 1080u,
            channels = QoiHeader.CHANNELS_RGBA,
            colorspace = QoiHeader.COLORSPACE_SRGB
        )

        assertEquals(1920u, header.width)
        assertEquals(1080u, header.height)
        assertEquals(4u.toUByte(), header.channels)
        assertEquals(0u.toUByte(), header.colorspace)
        assertTrue(header.magic.contentEquals(byteArrayOf(0x71, 0x6F, 0x69, 0x66)))
        assertTrue(header.isValid())
    }

    @Test
    fun testSerializationToBytes() {
        val header = QoiHeader(
            width = 800u, // 0x00000320
            height = 600u, // 0x00000258
            channels = 3u.toUByte(),
            colorspace = 1u.toUByte()
        )

        val bytes = header.toBytes()
        assertEquals(14, bytes.size)

        // Magic: "qoif"
        assertEquals(0x71.toByte(), bytes[0])
        assertEquals(0x6F.toByte(), bytes[1])
        assertEquals(0x69.toByte(), bytes[2])
        assertEquals(0x66.toByte(), bytes[3])

        // Width: 800 (BE: 0x00, 0x00, 0x03, 0x20)
        assertEquals(0x00.toByte(), bytes[4])
        assertEquals(0x00.toByte(), bytes[5])
        assertEquals(0x03.toByte(), bytes[6])
        assertEquals(0x20.toByte(), bytes[7])

        // Height: 600 (BE: 0x00, 0x00, 0x02, 0x58)
        assertEquals(0x00.toByte(), bytes[8])
        assertEquals(0x00.toByte(), bytes[9])
        assertEquals(0x02.toByte(), bytes[10])
        assertEquals(0x58.toByte(), bytes[11])

        // Channels: 3 (RGB)
        assertEquals(3.toByte(), bytes[12])

        // Colorspace: 1 (Linear)
        assertEquals(1.toByte(), bytes[13])
    }

    @Test
    fun testDeserializationFromBytes() {
        val rawBytes = byteArrayOf(
            0x71, 0x6F, 0x69, 0x66, // "qoif"
            0x00, 0x00, 0x04, 0x00, // width = 1024
            0x00, 0x00, 0x03, 0x00, // height = 768
            0x04,                   // channels = 4 (RGBA)
            0x00                    // colorspace = 0 (sRGB)
        )

        val header = QoiHeader.fromBytes(rawBytes)
        assertEquals(1024u, header.width)
        assertEquals(768u, header.height)
        assertEquals(4u.toUByte(), header.channels)
        assertEquals(0u.toUByte(), header.colorspace)
        assertTrue(header.isValid())
    }

    @Test
    fun testDeserializationWithOffset() {
        val prefix = byteArrayOf(0x01, 0x02, 0x03)
        val headerBytes = QoiHeader(
            width = 100u,
            height = 200u,
            channels = QoiHeader.CHANNELS_RGB,
            colorspace = QoiHeader.COLORSPACE_LINEAR
        ).toBytes()
        val combined = prefix + headerBytes

        val parsed = QoiHeader.fromBytes(combined, offset = 3)
        assertEquals(100u, parsed.width)
        assertEquals(200u, parsed.height)
        assertEquals(3u.toUByte(), parsed.channels)
        assertEquals(1u.toUByte(), parsed.colorspace)
    }

    @Test
    fun testRoundtripSerialization() {
        val original = QoiHeader(
            width = 3840u,
            height = 2160u,
            channels = QoiHeader.CHANNELS_RGBA,
            colorspace = QoiHeader.COLORSPACE_SRGB
        )

        val bytes = original.toBytes()
        val deserialized = QoiHeader.fromBytes(bytes)

        assertEquals(original, deserialized)
        assertEquals(original.hashCode(), deserialized.hashCode())
    }

    @Test
    fun testValidationRules() {
        // Valid header
        val validHeader = QoiHeader(
            width = 100u,
            height = 100u,
            channels = QoiHeader.CHANNELS_RGB,
            colorspace = QoiHeader.COLORSPACE_SRGB
        )
        assertTrue(validHeader.isValid())

        // Invalid magic
        val invalidMagic = QoiHeader(
            magic = byteArrayOf(0x00, 0x00, 0x00, 0x00),
            width = 100u,
            height = 100u,
            channels = 3u.toUByte(),
            colorspace = 0u.toUByte()
        )
        assertFalse(invalidMagic.isValid())

        // Zero width
        val zeroWidth = QoiHeader(
            width = 0u,
            height = 100u,
            channels = 3u.toUByte(),
            colorspace = 0u.toUByte()
        )
        assertFalse(zeroWidth.isValid())

        // Zero height
        val zeroHeight = QoiHeader(
            width = 100u,
            height = 0u,
            channels = 3u.toUByte(),
            colorspace = 0u.toUByte()
        )
        assertFalse(zeroHeight.isValid())

        // Invalid channels (must be 3 or 4)
        val invalidChannels = QoiHeader(
            width = 100u,
            height = 100u,
            channels = 2u.toUByte(),
            colorspace = 0u.toUByte()
        )
        assertFalse(invalidChannels.isValid())

        // Invalid colorspace (must be 0 or 1)
        val invalidColorspace = QoiHeader(
            width = 100u,
            height = 100u,
            channels = 3u.toUByte(),
            colorspace = 2u.toUByte()
        )
        assertFalse(invalidColorspace.isValid())
    }

    @Test
    fun testInvalidBufferLengthThrowsException() {
        val tooShort = byteArrayOf(0x71, 0x6F, 0x69, 0x66)
        assertFailsWith<IllegalArgumentException> {
            QoiHeader.fromBytes(tooShort)
        }
    }

    @Test
    fun testInvalidMagicLengthThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiHeader(
                magic = byteArrayOf(0x71, 0x6F),
                width = 100u,
                height = 100u,
                channels = 4u.toUByte(),
                colorspace = 0u.toUByte()
            )
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val header1 = QoiHeader(width = 500u, height = 400u, channels = 4u, colorspace = 0u)
        val header2 = QoiHeader(width = 500u, height = 400u, channels = 4u, colorspace = 0u)
        val header3 = QoiHeader(width = 500u, height = 401u, channels = 4u, colorspace = 0u)

        assertEquals(header1, header2)
        assertEquals(header1.hashCode(), header2.hashCode())
        assertNotEquals(header1, header3)
    }

    @Test
    fun testToStringContainsReadableInfo() {
        val header = QoiHeader(width = 800u, height = 600u, channels = 4u, colorspace = 0u)
        val str = header.toString()
        assertTrue(str.contains("qoif"))
        assertTrue(str.contains("800"))
        assertTrue(str.contains("600"))
    }

    @Test
    fun testTypeAliasCompatibility() {
        val aliasInstance: qoiHeader = qoiHeader(width = 300u, height = 200u, channels = 3u, colorspace = 1u)
        assertEquals(300u, aliasInstance.width)
        assertEquals(200u, aliasInstance.height)
    }

    @Test
    fun testCompanionInterfaceAndWriteBytes() {
        val companion: QoiChunkCompanion<QoiHeader> = QoiHeader
        assertEquals(14, companion.CHUNK_SIZE)

        val out = ByteArray(20)
        val written = QoiHeader.writeBytes(
            width = 800u,
            height = 600u,
            channels = QoiHeader.CHANNELS_RGB,
            colorspace = QoiHeader.COLORSPACE_LINEAR,
            out = out,
            offset = 3
        )
        assertEquals(14, written)

        val parsed = companion.fromBytes(out, offset = 3)
        assertEquals(800u, parsed.width)
        assertEquals(600u, parsed.height)
        assertEquals(QoiHeader.CHANNELS_RGB, parsed.channels)
        assertEquals(QoiHeader.COLORSPACE_LINEAR, parsed.colorspace)
    }
}

