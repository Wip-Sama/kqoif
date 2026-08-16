package org.wip.kqoif

import java.util.logging.Logger

/**
 * Represents the 1-byte QOI_OP_INDEX chunk.
┌─ QOI_OP_INDEX ──┐
│     Byte[0]     │
│ 7 6 5 4 3 2 1 0 │
│─────┼───────────│
│ 0 0 │   index   │
└─────────────────┘
 */
data class QoiOpIndex(
    val tag: UByte,
    val index: UByte
) {
    companion object {
        private val logger: Logger = Logger.getLogger(QoiOpIndex::class.java.name)

        /** The 2-bit tag for QOI_OP_INDEX (bits 7..6 are `00`). */
        val TAG: UByte = 0x00u

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Bitmask for the 6-bit index (`0b00111111` = `0x3F`). */
        const val INDEX_MASK: UInt = 0x3Fu

        /** Maximum allowed index value (63).
         * Could be implied since we're working with 6 bits 2^6=64 */
        val MAX_INDEX: UByte = 63u

        /**
         * Deserializes a [QoiOpIndex] from a single byte in [bytes] starting at [offset].
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpIndex].
         * @throws IllegalArgumentException if fewer than 1 byte is available or tag is invalid.
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpIndex {
            require(bytes.size - offset >= 1) {
                "Buffer too short for QOI_OP_INDEX. Expected at least 1 byte, but only ${bytes.size - offset} bytes are available."
            }
            val byte = bytes[offset].toUByte()
            val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
            val index = (byte.toUInt() and INDEX_MASK).toUByte()

            val op = QoiOpIndex(tag, index)
            if (!op.isValid()) {
                logger.warning("Deserialized QOI_OP_INDEX contains invalid values: $op")
            }
            return op
        }

        fun indexPosition(red: Int, green: Int, blue: Int, alpha: Int): UInt {
            return ((red * 3 + green * 5 + blue * 7 + alpha * 11) % 64).toUInt()
        }

        fun indexPosition(red: UByte, green: UByte, blue: UByte, alpha: UByte): UInt {
            return (red * 3u + green * 5u + blue * 7u + alpha * 11u).mod(64u)
        }

        fun indexPosition(red: UInt, green: UInt, blue: UInt, alpha: UInt): UInt {
            return (red * 3u + green * 5u + blue * 7u + alpha * 11u).mod(64u)
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_INDEX. Expected 0x00, but got $tag."
        }
        require(index <= MAX_INDEX) {
            "Index value must be between 0 and 63, but got $index."
        }
    }

    constructor(index: UByte) : this(TAG, index)
    constructor(index: Int) : this(TAG, index.toUByte())

    /**
     * Checks if this operation has a valid tag (0x00) and index (0..63).
     */
    fun isValid(): Boolean {
        return tag == TAG && index <= MAX_INDEX
    }

    /**
     * Serializes this operation into a 1-byte QOI chunk.
     */
    fun toBytes(): ByteArray {
        val rawByte = (((tag.toUInt() and 0x03u) shl 6) or (index.toUInt() and INDEX_MASK)).toByte()
        return byteArrayOf(rawByte)
    }
}
