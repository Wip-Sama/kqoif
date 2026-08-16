package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpDiffTest {

    @Test
    fun testConstructorAndProperties() {
        val op = QoiOpDiff(dr = -2, dg = 0, db = 1)
        assertEquals(1u.toUByte(), op.tag)
        assertEquals((-2).toByte(), op.dr)
        assertEquals(0.toByte(), op.dg)
        assertEquals(1.toByte(), op.db)
        assertTrue(op.isValid())
    }

    @Test
    fun testSerializationWithBias() {
        // dr=-2, dg=-2, db=-2 -> stored: 0b01_00_00_00 = 0x40 (64)
        val minDiff = QoiOpDiff(dr = -2, dg = -2, db = -2)
        val minBytes = minDiff.toBytes()
        assertEquals(1, minBytes.size)
        assertEquals(0x40.toByte(), minBytes[0])

        // dr=1, dg=1, db=1 -> stored: 0b01_11_11_11 = 0x7F (127)
        val maxDiff = QoiOpDiff(dr = 1, dg = 1, db = 1)
        val maxBytes = maxDiff.toBytes()
        assertEquals(1, maxBytes.size)
        assertEquals(0x7F.toByte(), maxBytes[0])

        // dr=0, dg=0, db=0 -> stored: 0b01_10_10_10 = 0x6A (106)
        val zeroDiff = QoiOpDiff(dr = 0, dg = 0, db = 0)
        val zeroBytes = zeroDiff.toBytes()
        assertEquals(1, zeroBytes.size)
        assertEquals(0x6A.toByte(), zeroBytes[0])

        // dr=-1, dg=0, db=1 -> stored: 0b01_01_10_11 = 0x5B (91)
        val mixedDiff = QoiOpDiff(dr = -1, dg = 0, db = 1)
        val mixedBytes = mixedDiff.toBytes()
        assertEquals(1, mixedBytes.size)
        assertEquals(0x5B.toByte(), mixedBytes[0])
    }

    @Test
    fun testDeserializationWithBias() {
        val opMin = QoiOpDiff.fromBytes(byteArrayOf(0x40))
        assertEquals((-2).toByte(), opMin.dr)
        assertEquals((-2).toByte(), opMin.dg)
        assertEquals((-2).toByte(), opMin.db)

        val opMax = QoiOpDiff.fromBytes(byteArrayOf(0x7F))
        assertEquals(1.toByte(), opMax.dr)
        assertEquals(1.toByte(), opMax.dg)
        assertEquals(1.toByte(), opMax.db)

        val opZero = QoiOpDiff.fromBytes(byteArrayOf(0x6A))
        assertEquals(0.toByte(), opZero.dr)
        assertEquals(0.toByte(), opZero.dg)
        assertEquals(0.toByte(), opZero.db)

        val opMixed = QoiOpDiff.fromBytes(byteArrayOf(0x5B))
        assertEquals((-1).toByte(), opMixed.dr)
        assertEquals(0.toByte(), opMixed.dg)
        assertEquals(1.toByte(), opMixed.db)
    }

    @Test
    fun testAllCombinationsRoundtrip() {
        for (dr in -2..1) {
            for (dg in -2..1) {
                for (db in -2..1) {
                    val original = QoiOpDiff(dr = dr, dg = dg, db = db)
                    val bytes = original.toBytes()
                    val deserialized = QoiOpDiff.fromBytes(bytes)

                    assertEquals(original, deserialized)
                    assertEquals(original.hashCode(), deserialized.hashCode())
                    assertTrue(deserialized.isValid())
                }
            }
        }
    }

    @Test
    fun testInvalidDifferencesThrowException() {
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = -3, dg = 0, db = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = 2, dg = 0, db = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = 0, dg = -3, db = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = 0, dg = 2, db = 0) }
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = 0, dg = 0, db = -3) }
        assertFailsWith<IllegalArgumentException> { QoiOpDiff(dr = 0, dg = 0, db = 2) }
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff(tag = 0u.toUByte(), dr = 0.toByte(), dg = 0.toByte(), db = 0.toByte())
        }
    }

    @Test
    fun testDeserializationWithWrongTagThrowsException() {
        // Tag 00 (INDEX)
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf(0x00))
        }
        // Tag 10 (LUMA)
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf(0b10000000.toByte()))
        }
        // Tag 11 (RUN)
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf(0b11000000.toByte()))
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf())
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpDiff(-1, 0, 1)
        val op2 = QoiOpDiff(-1, 0, 1)
        val op3 = QoiOpDiff(-1, 1, 1)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }
}
