package org.wip.kqoif

/**
 * Represents the 1-byte QOI_OP_INDEX chunk.
 *
 * An index operation references a previously seen pixel stored in an array of 64 entries.
 *
 * ```
 * ┌─ QOI_OP_INDEX ──┐
 * │     Byte[0]     │
 * │ 7 6 5 4 3 2 1 0 │
 * │─────┼───────────│
 * │ 0 0 │   index   │
 * └─────────────────┘
 * ```
 *
 * - 2-bit tag `0b00` (bits 7..6)
 * - 6-bit index `0..63` (bits 5..0)
 */
data class QoiOpIndex(
    override val tag: UByte,
    val index: UByte
) : QoiOp {
    companion object : QoiOpCompanion<QoiOpIndex> {
        /** The 2-bit tag for QOI_OP_INDEX (bits 7..6 are `00`). */
        override val TAG: UByte = 0x00u

        /** Total size of the QOI_OP_INDEX chunk in bytes. */
        override val CHUNK_SIZE: Int = 1

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Bitmask for the 6-bit index (`0b00111111` = `0x3F`). */
        const val INDEX_MASK: UInt = 0x3Fu

        /** Maximum allowed index value (63). */
        val MAX_INDEX: UByte = 63u

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_INDEX tag `0b00`.
         * Excludes `0xFE` (RGB) and `0xFF` (RGBA).
         */
        override fun matchTag(bytes: ByteArray, offset: Int): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0x00u && byte != 0xFEu && byte != 0xFFu
        }

        /**
         * Writes a 1-byte QOI_OP_INDEX chunk directly into [out] at [offset].
         *
         * @param index Index value (0..63).
         * @param out Destination byte array.
         * @param offset Starting offset in [out].
         * @return Number of bytes written (1).
         */
        fun writeBytes(index: Int, out: ByteArray, offset: Int = 0): Int {
            require(index in 0..63) { "Index value must be between 0 and 63, but got $index." }
            require(out.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_INDEX. Expected at least $CHUNK_SIZE byte, but only ${out.size - offset} bytes are available."
            }
            out[offset] = (index and INDEX_MASK.toInt()).toByte()
            return CHUNK_SIZE
        }

        /**
         * Writes a 1-byte QOI_OP_INDEX chunk directly into [out] at [offset].
         */
        fun writeBytes(index: UByte, out: ByteArray, offset: Int = 0): Int = writeBytes(index.toInt(), out, offset)

        /**
         * Writes a 1-byte QOI_OP_INDEX chunk for [color]'s hash index directly into [out] at [offset].
         */
        fun writeBytes(color: Color, out: ByteArray, offset: Int = 0): Int = writeBytes(color.toHash(), out, offset)

        /**
         * Serializes an index (0..63) into a 1-byte QOI_OP_INDEX chunk.
         */
        fun toBytes(index: Int): ByteArray {
            val result = ByteArray(CHUNK_SIZE)
            writeBytes(index, result, 0)
            return result
        }

        /**
         * Serializes an index (0..63) into a 1-byte QOI_OP_INDEX chunk.
         */
        fun toBytes(index: UByte): ByteArray = toBytes(index.toInt())

        /**
         * Serializes [color]'s hash index into a 1-byte QOI_OP_INDEX chunk.
         */
        fun toBytes(color: Color): ByteArray = toBytes(color.toHash())

        /**
         * Deserializes a [QoiOpIndex] from a single byte in [bytes] starting at [offset].
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpIndex].
         * @throws IllegalArgumentException if fewer than 1 byte is available or tag is invalid.
         */
        override fun fromBytes(bytes: ByteArray, offset: Int): QoiOpIndex {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_INDEX. Expected at least $CHUNK_SIZE byte, but only ${bytes.size - offset} bytes are available."
            }
            val byte = bytes[offset].toUByte()
            val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
            val index = (byte.toUInt() and INDEX_MASK).toUByte()

            return QoiOpIndex(tag, index)
        }

        /**
         * Computes the 6-bit hash index position (0..63) for a given color with RGBA channels.
         */
        fun indexPosition(red: Int, green: Int, blue: Int, alpha: Int): UInt {
            return ((red * 3 + green * 5 + blue * 7 + alpha * 11) % 64).toUInt()
        }

        /**
         * Computes the 6-bit hash index position (0..63) for a given color with RGBA channels.
         */
        fun indexPosition(red: UByte, green: UByte, blue: UByte, alpha: UByte): UInt {
            return (red * 3u + green * 5u + blue * 7u + alpha * 11u).mod(64u)
        }

        /**
         * Computes the 6-bit hash index position (0..63) for a given color with RGBA channels.
         */
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
    override fun isValid(): Boolean {
        return tag == TAG && index <= MAX_INDEX
    }

    /**
     * Serializes this operation into a 1-byte QOI chunk.
     */
    override fun toBytes(): ByteArray = Companion.toBytes(index.toInt())
}
