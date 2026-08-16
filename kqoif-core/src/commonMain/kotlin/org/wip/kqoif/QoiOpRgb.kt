package org.wip.kqoif

/**
 * Represents the 4-byte QOI_OP_RGB chunk.
 *
 * ```
 * ┌─ QOI_OP_RGB ────┬────────┬────────┬─────────┐
 * │     Byte[0]     │ Byte[1]│ Byte[2]│ Byte[3] │
 * │ 7 6 5 4 3 2 1 0 │ 7 .. 0 │ 7 .. 0 │ 7 .. 0  │
 * │─────────────────┼────────┼────────┼─────────│
 * │ 1 1 1 1 1 1 1 0 │  red   │ green  │  blue   │
 * └─────────────────┴────────┴────────┴─────────┘
 * ```
 */
data class QoiOpRgb(
    override val tag: UByte,
    val red: UByte,
    val green: UByte,
    val blue: UByte
) : QoiOp {
    companion object {
        /** The tag for QOI_OP_RGB, which is `11111110` in binary (0xFE). */
        val TAG: UByte = 0xFEu

        /** Total size of QOI_OP_RGB chunk in bytes. */
        const val CHUNK_SIZE: Int = 4

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_RGB tag `0xFE`.
         */
        fun matchTag(bytes: ByteArray, offset: Int = 0): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            return bytes[offset] == 0xFE.toByte()
        }

        /**
         * Deserializes a [QoiOpRgb] chunk from [bytes] at [offset].
         *
         * @param bytes Raw byte array containing the chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpRgb].
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpRgb {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RGB. Expected at least $CHUNK_SIZE bytes, but only ${bytes.size - offset} bytes are available."
            }
            val tag = bytes[offset].toUByte()
            val red = bytes[offset + 1].toUByte()
            val green = bytes[offset + 2].toUByte()
            val blue = bytes[offset + 3].toUByte()

            return QoiOpRgb(tag, red, green, blue)
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_RGB. Expected 0xFE, but got $tag."
        }
    }

    constructor(red: UByte, green: UByte, blue: UByte) : this(TAG, red, green, blue)
    constructor(red: Int, green: Int, blue: Int) : this(TAG, red.toUByte(), green.toUByte(), blue.toUByte())

    /**
     * Converts this RGB chunk to a [Color] preserving the alpha from [prevColor].
     */
    fun toColor(prevColor: Color = Color(0, 0, 0, 255)): Color {
        return Color(red.toInt(), green.toInt(), blue.toInt(), prevColor.a)
    }

    /**
     * Checks if this operation has a valid tag (0xFE).
     */
    override fun isValid(): Boolean {
        return tag == TAG
    }

    /**
     * Serializes this operation into a 4-byte QOI chunk.
     */
    override fun toBytes(): ByteArray {
        val result = ByteArray(CHUNK_SIZE)
        result[0] = this.tag.toByte()
        result[1] = this.red.toByte()
        result[2] = this.green.toByte()
        result[3] = this.blue.toByte()
        return result
    }
}
