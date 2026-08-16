package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpRgbTest {

    @Test
    fun testConstructorAndSerialization() {
        val op = QoiOpRgb(red = 255, green = 128, blue = 64)
        assertEquals(0xFEu.toUByte(), op.tag)
        assertEquals(255u.toUByte(), op.red)
        assertEquals(128u.toUByte(), op.green)
        assertEquals(64u.toUByte(), op.blue)
        assertTrue(op.isValid())

        val bytes = op.toBytes()
        assertEquals(4, bytes.size)
        assertEquals(0xFE.toByte(), bytes[0])
        assertEquals(255.toByte(), bytes[1])
        assertEquals(128.toByte(), bytes[2])
        assertEquals(64.toByte(), bytes[3])
    }

    @Test
    fun testDeserialization() {
        val raw = byteArrayOf(0xFE.toByte(), 0x10.toByte(), 0x20.toByte(), 0x30.toByte())
        val op = QoiOpRgb.fromBytes(raw)
        assertEquals(0xFEu.toUByte(), op.tag)
        assertEquals(0x10u.toUByte(), op.red)
        assertEquals(0x20u.toUByte(), op.green)
        assertEquals(0x30u.toUByte(), op.blue)
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRgb(tag = 0xFFu.toUByte(), red = 0u.toUByte(), green = 0u.toUByte(), blue = 0u.toUByte())
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRgb.fromBytes(byteArrayOf(0xFE.toByte(), 0x01.toByte()))
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpRgb(10, 20, 30)
        val op2 = QoiOpRgb(10, 20, 30)
        val op3 = QoiOpRgb(10, 20, 31)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }
}
