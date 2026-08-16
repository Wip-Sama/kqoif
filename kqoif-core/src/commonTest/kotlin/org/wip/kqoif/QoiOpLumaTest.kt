package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpLumaTest {

    @Test
    fun testConstructorAndProperties() {
        val opByte = QoiOpLuma(dg = (-10).toByte(), dr_dg = (-4).toByte(), db_dg = 3.toByte())
        assertEquals(2u.toUByte(), opByte.tag)
        assertEquals((-10).toByte(), opByte.dg)
        assertEquals((-4).toByte(), opByte.dr_dg)
        assertEquals(3.toByte(), opByte.db_dg)
        assertTrue(opByte.isValid())

        val opInt = QoiOpLuma(dg = -10, dr_dg = -4, db_dg = 3)
        assertEquals(2u.toUByte(), opInt.tag)
        assertEquals((-10).toByte(), opInt.dg)
        assertEquals((-4).toByte(), opInt.dr_dg)
        assertEquals(3.toByte(), opInt.db_dg)
        assertTrue(opInt.isValid())
    }

    @Test
    fun testSerializationWithBias() {
        val minOp = QoiOpLuma(dg = -32, dr_dg = -8, db_dg = -8)
        val minBytes = minOp.toBytes()
        assertEquals(2, minBytes.size)
        assertEquals(0x80.toByte(), minBytes[0])
        assertEquals(0x00.toByte(), minBytes[1])

        val maxOp = QoiOpLuma(dg = 31, dr_dg = 7, db_dg = 7)
        val maxBytes = maxOp.toBytes()
        assertEquals(2, maxBytes.size)
        assertEquals(0xBF.toByte(), maxBytes[0])
        assertEquals(0xFF.toByte(), maxBytes[1])

        val zeroOp = QoiOpLuma(dg = 0, dr_dg = 0, db_dg = 0)
        val zeroBytes = zeroOp.toBytes()
        assertEquals(2, zeroBytes.size)
        assertEquals(0xA0.toByte(), zeroBytes[0])
        assertEquals(0x88.toByte(), zeroBytes[1])

        val customOp = QoiOpLuma(dg = 10, dr_dg = -3, db_dg = 4)
        val customBytes = customOp.toBytes()
        assertEquals(2, customBytes.size)
        assertEquals(0xAA.toByte(), customBytes[0])
        assertEquals(0x5C.toByte(), customBytes[1])
    }

    @Test
    fun testDeserializationWithBias() {
        val opMin = QoiOpLuma.fromBytes(byteArrayOf(0x80.toByte(), 0x00.toByte()))
        assertEquals((-32).toByte(), opMin.dg)
        assertEquals((-8).toByte(), opMin.dr_dg)
        assertEquals((-8).toByte(), opMin.db_dg)

        val opMax = QoiOpLuma.fromBytes(byteArrayOf(0xBF.toByte(), 0xFF.toByte()))
        assertEquals(31.toByte(), opMax.dg)
        assertEquals(7.toByte(), opMax.dr_dg)
        assertEquals(7.toByte(), opMax.db_dg)

        val opZero = QoiOpLuma.fromBytes(byteArrayOf(0xA0.toByte(), 0x88.toByte()))
        assertEquals(0.toByte(), opZero.dg)
        assertEquals(0.toByte(), opZero.dr_dg)
        assertEquals(0.toByte(), opZero.db_dg)

        val opCustom = QoiOpLuma.fromBytes(byteArrayOf(0xAA.toByte(), 0x5C.toByte()))
        assertEquals(10.toByte(), opCustom.dg)
        assertEquals((-3).toByte(), opCustom.dr_dg)
        assertEquals(4.toByte(), opCustom.db_dg)
    }

    @Test
    fun testDeserializationWithOffset() {
        val prefix = byteArrayOf(0x01, 0x02, 0x03)
        val lumaBytes = QoiOpLuma(dg = 5, dr_dg = -2, db_dg = 3).toBytes()
        val combined = prefix + lumaBytes

        val parsed = QoiOpLuma.fromBytes(combined, offset = 3)
        assertEquals(5.toByte(), parsed.dg)
        assertEquals((-2).toByte(), parsed.dr_dg)
        assertEquals(3.toByte(), parsed.db_dg)
    }

    @Test
    fun testRoundtripCombinations() {
        for (dg in -32..31 step 7) {
            for (dr_dg in -8..7 step 3) {
                for (db_dg in -8..7 step 3) {
                    val original = QoiOpLuma(dg = dg, dr_dg = dr_dg, db_dg = db_dg)
                    val bytes = original.toBytes()
                    val deserialized = QoiOpLuma.fromBytes(bytes)

                    assertEquals(original, deserialized)
                    assertEquals(original.hashCode(), deserialized.hashCode())
                    assertTrue(deserialized.isValid())
                }
            }
        }
    }

    @Test
    fun testOutOfRangeValuesThrowException() {
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = -33, dr_dg = 0, db_dg = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = 32, dr_dg = 0, db_dg = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = 0, dr_dg = -9, db_dg = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = 0, dr_dg = 8, db_dg = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = 0, dr_dg = 0, db_dg = -9) }
        assertFailsWith<IllegalArgumentException> { QoiOpLuma(dg = 0, dr_dg = 0, db_dg = 8) }
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma(tag = 0u.toUByte(), dg = 0.toByte(), dr_dg = 0.toByte(), db_dg = 0.toByte())
        }
    }

    @Test
    fun testDeserializationWrongTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma.fromBytes(byteArrayOf(0x00, 0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma.fromBytes(byteArrayOf(0b01000000.toByte(), 0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma.fromBytes(byteArrayOf(0b11000000.toByte(), 0x00))
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma.fromBytes(byteArrayOf())
        }
        assertFailsWith<IllegalArgumentException> {
            QoiOpLuma.fromBytes(byteArrayOf(0xA0.toByte()))
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpLuma(dg = 5, dr_dg = -2, db_dg = 3)
        val op2 = QoiOpLuma(dg = 5, dr_dg = -2, db_dg = 3)
        val op3 = QoiOpLuma(dg = 5, dr_dg = -2, db_dg = 4)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }
}
