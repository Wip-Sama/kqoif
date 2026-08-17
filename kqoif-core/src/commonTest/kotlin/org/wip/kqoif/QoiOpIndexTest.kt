package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QoiOpIndexTest {

    @Test
    fun testConstructorWithUByteAndInt() {
        val opUByte = QoiOpIndex(index = 42u.toUByte())
        assertEquals(0u.toUByte(), opUByte.tag)
        assertEquals(42u.toUByte(), opUByte.index)
        assertTrue(opUByte.isValid())

        val opInt = QoiOpIndex(index = 42)
        assertEquals(0u.toUByte(), opInt.tag)
        assertEquals(42u.toUByte(), opInt.index)
        assertTrue(opInt.isValid())
    }

    @Test
    fun testSerializationToByte() {
        val opZero = QoiOpIndex(index = 0u)
        val bytesZero = opZero.toBytes()
        assertEquals(1, bytesZero.size)
        assertEquals(0x00.toByte(), bytesZero[0])

        val opMax = QoiOpIndex(index = 63u)
        val bytesMax = opMax.toBytes()
        assertEquals(1, bytesMax.size)
        assertEquals(0x3F.toByte(), bytesMax[0])

        val opCustom = QoiOpIndex(index = 42u)
        val bytesCustom = opCustom.toBytes()
        assertEquals(1, bytesCustom.size)
        assertEquals(0x2A.toByte(), bytesCustom[0])
    }

    @Test
    fun testDeserializationFromByte() {
        val rawZero = byteArrayOf(0x00)
        val opZero = QoiOpIndex.fromBytes(rawZero)
        assertEquals(0u.toUByte(), opZero.tag)
        assertEquals(0u.toUByte(), opZero.index)

        val rawMax = byteArrayOf(0x3F)
        val opMax = QoiOpIndex.fromBytes(rawMax)
        assertEquals(0u.toUByte(), opMax.tag)
        assertEquals(63u.toUByte(), opMax.index)

        val rawCustom = byteArrayOf(0x2A)
        val opCustom = QoiOpIndex.fromBytes(rawCustom)
        assertEquals(0u.toUByte(), opCustom.tag)
        assertEquals(42u.toUByte(), opCustom.index)
    }

    @Test
    fun testDeserializationWithOffset() {
        val buffer = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x15.toByte(), 0xAA.toByte())
        val op = QoiOpIndex.fromBytes(buffer, offset = 2)
        assertEquals(0u.toUByte(), op.tag)
        assertEquals(0x15u.toUByte(), op.index)
    }

    @Test
    fun testRoundtripAllValidIndices() {
        for (i in 0..63) {
            val original = QoiOpIndex(index = i)
            val bytes = original.toBytes()
            val deserialized = QoiOpIndex.fromBytes(bytes)

            assertEquals(original, deserialized)
            assertEquals(original.hashCode(), deserialized.hashCode())
            assertTrue(deserialized.isValid())
        }
    }

    @Test
    fun testInvalidIndexThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex(index = 64u.toUByte())
        }
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex(index = 100)
        }
    }

    @Test
    fun testInvalidTagThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex(tag = 1u.toUByte(), index = 10u.toUByte())
        }
    }

    @Test
    fun testDeserializationWithNonZeroTagThrowsException() {
        // Tag 01 (QOI_OP_DIFF, bits 7..6 = 01)
        val diffByte = byteArrayOf(0b01000000.toByte())
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex.fromBytes(diffByte)
        }

        // Tag 10 (QOI_OP_LUMA, bits 7..6 = 10)
        val lumaByte = byteArrayOf(0b10000000.toByte())
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex.fromBytes(lumaByte)
        }

        // Tag 11 (QOI_OP_RUN, bits 7..6 = 11)
        val runByte = byteArrayOf(0b11000000.toByte())
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex.fromBytes(runByte)
        }
    }

    @Test
    fun testBufferTooShortThrowsException() {
        val emptyBuffer = byteArrayOf()
        assertFailsWith<IllegalArgumentException> {
            QoiOpIndex.fromBytes(emptyBuffer)
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val op1 = QoiOpIndex(index = 10u)
        val op2 = QoiOpIndex(index = 10u)
        val op3 = QoiOpIndex(index = 11u)

        assertEquals(op1, op2)
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1, op3)
    }

    @Test
    fun testCompanionInterfaceAndDirectSerialization() {
        val companion: QoiOpCompanion<QoiOpIndex> = QoiOpIndex
        assertEquals(0u.toUByte(), companion.TAG)
        assertEquals(1, companion.CHUNK_SIZE)
        assertTrue(companion.matchTag(byteArrayOf(0x00)))

        val out = ByteArray(5)
        val written = QoiOpIndex.writeBytes(index = 42, out = out, offset = 1)
        assertEquals(1, written)
        assertEquals(42.toByte(), out[1])

        val bytesInt = QoiOpIndex.toBytes(42)
        assertEquals(1, bytesInt.size)
        assertEquals(42.toByte(), bytesInt[0])

        val color = Color(10, 20, 30, 40)
        val writtenColor = QoiOpIndex.writeBytes(color = color, out = out, offset = 3)
        assertEquals(1, writtenColor)
        assertEquals(color.toHash().toByte(), out[3])

        val colorBytes = QoiOpIndex.toBytes(color)
        assertEquals(1, colorBytes.size)
        assertEquals(color.toHash().toByte(), colorBytes[0])
    }
}

