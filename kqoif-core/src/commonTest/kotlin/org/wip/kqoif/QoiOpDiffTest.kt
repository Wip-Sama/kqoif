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
        val minDiff = QoiOpDiff(dr = -2, dg = -2, db = -2)
        val minBytes = minDiff.toBytes()
        assertEquals(1, minBytes.size)
        assertEquals(0x40.toByte(), minBytes[0])

        val maxDiff = QoiOpDiff(dr = 1, dg = 1, db = 1)
        val maxBytes = maxDiff.toBytes()
        assertEquals(1, maxBytes.size)
        assertEquals(0x7F.toByte(), maxBytes[0])

        val zeroDiff = QoiOpDiff(dr = 0, dg = 0, db = 0)
        val zeroBytes = zeroDiff.toBytes()
        assertEquals(1, zeroBytes.size)
        assertEquals(0x6A.toByte(), zeroBytes[0])

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
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf(0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            QoiOpDiff.fromBytes(byteArrayOf(0b10000000.toByte()))
        }
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

    @Test
    fun testCompanionInterfaceAndDirectSerialization() {
        val companion: QoiOpCompanion<QoiOpDiff> = QoiOpDiff
        assertEquals(1u.toUByte(), companion.TAG)
        assertEquals(1, companion.CHUNK_SIZE)
        assertTrue(companion.matchTag(byteArrayOf(0x40)))

        assertTrue(QoiOpDiff.canEncode(-2, 0, 1))
        assertTrue(!QoiOpDiff.canEncode(-3, 0, 1))

        val p1 = Color(10, 20, 30, 255)
        val p2 = Color(9, 20, 31, 255)
        val pAlphaDiff = Color(9, 20, 31, 254)
        val pFar = Color(20, 20, 30, 255)

        assertTrue(QoiOpDiff.canEncode(p1, p2))
        assertTrue(!QoiOpDiff.canEncode(p1, pAlphaDiff))
        assertTrue(!QoiOpDiff.canEncode(p1, pFar))

        val out = ByteArray(5)
        val written = QoiOpDiff.writeBytes(dr = -1, dg = 0, db = 1, out = out, offset = 2)
        assertEquals(1, written)
        assertEquals(0x5B.toByte(), out[2])

        val tryWritten = QoiOpDiff.tryWriteBytes(p1, p2, out, 2)
        assertEquals(1, tryWritten)
        assertEquals(0x5B.toByte(), out[2])

        val tryFailed = QoiOpDiff.tryWriteBytes(p1, pFar, out, 2)
        assertEquals(0, tryFailed)

        val bytesDirect = QoiOpDiff.toBytes(dr = -1, dg = 0, db = 1)
        assertEquals(1, bytesDirect.size)
        assertEquals(0x5B.toByte(), bytesDirect[0])

        val bytesColor = QoiOpDiff.toBytes(p1, p2)
        assertEquals(1, bytesColor?.size)
        assertEquals(0x5B.toByte(), bytesColor?.get(0))
    }
}

