package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpRunTest {

    @Test
    fun testConstructorAndProperties() {
        val opUByte = QoiOpRun(run = 10u.toUByte())
        assertEquals(3u.toUByte(), opUByte.tag)
        assertEquals(10u.toUByte(), opUByte.run)
        assertTrue(opUByte.isValid())

        val opInt = QoiOpRun(run = 10)
        assertEquals(3u.toUByte(), opInt.tag)
        assertEquals(10u.toUByte(), opInt.run)
        assertTrue(opInt.isValid())
    }

    @Test
    fun testSerializationWithBias() {
        // run = 1 -> stored as 0 (run - 1) -> 0b11_000000 = 0xC0 (192)
        val opMin = QoiOpRun(run = 1u)
        val minBytes = opMin.toBytes()
        assertEquals(1, minBytes.size)
        assertEquals(0xC0.toByte(), minBytes[0])

        // run = 62 -> stored as 61 (run - 1) -> 0b11_111101 = 0xFD (253)
        val opMax = QoiOpRun(run = 62u)
        val maxBytes = opMax.toBytes()
        assertEquals(1, maxBytes.size)
        assertEquals(0xFD.toByte(), maxBytes[0])

        // run = 33 -> stored as 32 (run - 1) -> 0b11_100000 = 0xE0 (224)
        val opMid = QoiOpRun(run = 33u)
        val midBytes = opMid.toBytes()
        assertEquals(1, midBytes.size)
        assertEquals(0xE0.toByte(), midBytes[0])
    }

    @Test
    fun testDeserializationWithBias() {
        val opMin = QoiOpRun.fromBytes(byteArrayOf(0xC0.toByte()))
        assertEquals(3u.toUByte(), opMin.tag)
        assertEquals(1u.toUByte(), opMin.run)

        val opMax = QoiOpRun.fromBytes(byteArrayOf(0xFD.toByte()))
        assertEquals(3u.toUByte(), opMax.tag)
        assertEquals(62u.toUByte(), opMax.run)

        val opMid = QoiOpRun.fromBytes(byteArrayOf(0xE0.toByte()))
        assertEquals(3u.toUByte(), opMid.tag)
        assertEquals(33u.toUByte(), opMid.run)
    }

    @Test
    fun testAllValidRunsRoundtrip() {
        for (run in 1..62) {
            val original = QoiOpRun(run = run)
            val bytes = original.toBytes()
            val deserialized = QoiOpRun.fromBytes(bytes)

            assertEquals(original, deserialized)
            assertEquals(original.hashCode(), deserialized.hashCode())
            assertTrue(deserialized.isValid())
        }
    }

    @Test
    fun testInvalidRunLengthThrowsException() {
        // Run length 0 is invalid
        assertFailsWith<IllegalArgumentException> { QoiOpRun(run = 0) }

        // Run length 63 is invalid
        assertFailsWith<IllegalArgumentException> { QoiOpRun(run = 63) }

        // Run length > 63 is invalid
        assertFailsWith<IllegalArgumentException> { QoiOpRun(run = 100) }
    }

    @Test
    fun testIllegalStoredValuesInRunChunkThrowException() {
        // 0xFE (stored 62) is QOI_OP_RGB tag
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf(0xFE.toByte()))
        }

        // 0xFF (stored 63) is QOI_OP_RGBA tag
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf(0xFF.toByte()))
        }
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun(tag = 0u.toUByte(), run = 10u.toUByte())
        }
    }

    @Test
    fun testDeserializationWithWrongTagThrowsException() {
        // Tag 00 (INDEX)
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf(0x00))
        }
        // Tag 01 (DIFF)
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf(0b01000000.toByte()))
        }
        // Tag 10 (LUMA)
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf(0b10000000.toByte()))
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpRun.fromBytes(byteArrayOf())
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpRun(run = 15)
        val op2 = QoiOpRun(run = 15)
        val op3 = QoiOpRun(run = 16)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }
}
