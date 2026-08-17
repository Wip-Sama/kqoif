package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpRgbaTest {

    @Test
    fun testConstructorAndSerialization() {
        val op = QoiOpRgba(red = 255, green = 128, blue = 64, alpha = 32)
        assertEquals(0xFFu.toUByte(), op.tag)
        assertEquals(255u.toUByte(), op.red)
        assertEquals(128u.toUByte(), op.green)
        assertEquals(64u.toUByte(), op.blue)
        assertEquals(32u.toUByte(), op.alpha)
        assertTrue(op.isValid())

        val bytes = op.toBytes()
        assertEquals(5, bytes.size)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(255.toByte(), bytes[1])
        assertEquals(128.toByte(), bytes[2])
        assertEquals(64.toByte(), bytes[3])
        assertEquals(32.toByte(), bytes[4])
    }

    @Test
    fun testDeserialization() {
        val raw = byteArrayOf(0xFF.toByte(), 0x10.toByte(), 0x20.toByte(), 0x30.toByte(), 0x40.toByte())
        val op = QoiOpRgba.fromBytes(raw)
        assertEquals(0xFFu.toUByte(), op.tag)
        assertEquals(0x10u.toUByte(), op.red)
        assertEquals(0x20u.toUByte(), op.green)
        assertEquals(0x30u.toUByte(), op.blue)
        assertEquals(0x40u.toUByte(), op.alpha)
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRgba(tag = 0xFEu.toUByte(), red = 0u.toUByte(), green = 0u.toUByte(), blue = 0u.toUByte(), alpha = 0u.toUByte())
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRgba.fromBytes(byteArrayOf(0xFF.toByte(), 0x01.toByte(), 0x02.toByte()))
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpRgba(10, 20, 30, 40)
        val op2 = QoiOpRgba(10, 20, 30, 40)
        val op3 = QoiOpRgba(10, 20, 30, 41)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }

    @Test
    fun testCompanionInterfaceAndDirectSerialization() {
        val companion: QoiOpCompanion<QoiOpRgba> = QoiOpRgba
        assertEquals(0xFFu.toUByte(), companion.TAG)
        assertEquals(5, companion.CHUNK_SIZE)
        assertTrue(companion.matchTag(byteArrayOf(0xFF.toByte(), 0, 0, 0, 0)))

        val out = ByteArray(7)
        val written = QoiOpRgba.writeBytes(red = 10, green = 20, blue = 30, alpha = 40, out = out, offset = 1)
        assertEquals(5, written)
        assertEquals(0xFF.toByte(), out[1])
        assertEquals(10.toByte(), out[2])
        assertEquals(20.toByte(), out[3])
        assertEquals(30.toByte(), out[4])
        assertEquals(40.toByte(), out[5])

        val directBytes = QoiOpRgba.toBytes(10, 20, 30, 40)
        assertEquals(5, directBytes.size)
        assertEquals(0xFF.toByte(), directBytes[0])

        val colorBytes = QoiOpRgba.toBytes(Color(10, 20, 30, 40))
        assertEquals(5, colorBytes.size)
        assertEquals(0xFF.toByte(), colorBytes[0])
    }
}

