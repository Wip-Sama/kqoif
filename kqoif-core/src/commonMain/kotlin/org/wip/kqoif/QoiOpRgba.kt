package org.wip.kqoif

/**
 * Represents the 5-byte QOI_OP_RGBA chunk.
 *
 * ```
 * ┌─ QOI_OP_RGBA ───┬────────┬────────┬────────┬─────────┐
 * │     Byte[0]     │ Byte[1]│ Byte[2]│ Byte[3]│ Byte[4] │
 * │ 7 6 5 4 3 2 1 0 │ 7 .. 0 │ 7 .. 0 │ 7 .. 0 │ 7 .. 0  │
 * │─────────────────┼────────┼────────┼────────┼─────────│
 * │ 1 1 1 1 1 1 1 1 │  red   │ green  │  blue  │  alpha  │
 * └─────────────────┴────────┴────────┴────────┴─────────┘
 * ```
 */
data class QoiOpRgba(
    override val tag: UByte,
    val red: UByte,
    val green: UByte,
    val blue: UByte,
    val alpha: UByte
) : QoiOp {
    companion object : QoiOpCompanion<QoiOpRgba> {
        /** The tag for QOI_OP_RGBA, which is `11111111` in binary (0xFF). */
        override val TAG: UByte = 0xFFu

        /** Total size of QOI_OP_RGBA chunk in bytes. */
        override val CHUNK_SIZE: Int = 5

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_RGBA tag `0xFF`.
         */
        override fun matchTag(bytes: ByteArray, offset: Int): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            return bytes[offset] == 0xFF.toByte()
        }

        /**
         * Writes a 5-byte QOI_OP_RGBA chunk directly into [out] at [offset].
         *
         * @param red Red channel value (0..255).
         * @param green Green channel value (0..255).
         * @param blue Blue channel value (0..255).
         * @param alpha Alpha channel value (0..255).
         * @param out Destination byte array.
         * @param offset Starting offset in [out].
         * @return Number of bytes written (5).
         */
        fun writeBytes(red: Int, green: Int, blue: Int, alpha: Int, out: ByteArray, offset: Int = 0): Int {
            require(out.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RGBA. Expected at least $CHUNK_SIZE bytes, but only ${out.size - offset} bytes are available."
            }
            out[offset] = 0xFF.toByte()
            out[offset + 1] = (red and 0xFF).toByte()
            out[offset + 2] = (green and 0xFF).toByte()
            out[offset + 3] = (blue and 0xFF).toByte()
            out[offset + 4] = (alpha and 0xFF).toByte()
            return CHUNK_SIZE
        }

        /**
         * Writes a 5-byte QOI_OP_RGBA chunk directly into [out] at [offset].
         */
        fun writeBytes(red: UByte, green: UByte, blue: UByte, alpha: UByte, out: ByteArray, offset: Int = 0): Int {
            return writeBytes(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt(), out, offset)
        }

        /**
         * Writes a 5-byte QOI_OP_RGBA chunk for [color] directly into [out] at [offset].
         */
        fun writeBytes(color: Color, out: ByteArray, offset: Int = 0): Int {
            return writeBytes(color.r, color.g, color.b, color.a, out, offset)
        }

        /**
         * Serializes RGBA channels into a 5-byte QOI_OP_RGBA chunk.
         */
        fun toBytes(red: Int, green: Int, blue: Int, alpha: Int): ByteArray {
            val result = ByteArray(CHUNK_SIZE)
            writeBytes(red, green, blue, alpha, result, 0)
            return result
        }

        /**
         * Serializes RGBA channels into a 5-byte QOI_OP_RGBA chunk.
         */
        fun toBytes(red: UByte, green: UByte, blue: UByte, alpha: UByte): ByteArray {
            return toBytes(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt())
        }

        /**
         * Serializes [color] into a 5-byte QOI_OP_RGBA chunk.
         */
        fun toBytes(color: Color): ByteArray = toBytes(color.r, color.g, color.b, color.a)

        /**
         * Deserializes a [QoiOpRgba] chunk from [bytes] at [offset].
         *
         * @param bytes Raw byte array containing the chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpRgba].
         */
        override fun fromBytes(bytes: ByteArray, offset: Int): QoiOpRgba {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RGBA. Expected at least $CHUNK_SIZE bytes, but only ${bytes.size - offset} bytes are available."
            }
            val tag = bytes[offset].toUByte()
            val red = bytes[offset + 1].toUByte()
            val green = bytes[offset + 2].toUByte()
            val blue = bytes[offset + 3].toUByte()
            val alpha = bytes[offset + 4].toUByte()

            return QoiOpRgba(tag, red, green, blue, alpha)
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_RGBA. Expected 0xFF, but got $tag."
        }
    }

    constructor(red: UByte, green: UByte, blue: UByte, alpha: UByte) : this(TAG, red, green, blue, alpha)
    constructor(red: Int, green: Int, blue: Int, alpha: Int) : this(TAG, red.toUByte(), green.toUByte(), blue.toUByte(), alpha.toUByte())

    /**
     * Converts this RGBA chunk to a [Color].
     */
    fun toColor(): Color {
        return Color(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt())
    }

    /**
     * Checks if this operation has a valid tag (0xFF).
     */
    override fun isValid(): Boolean {
        return tag == TAG
    }

    /**
     * Serializes this operation into a 5-byte QOI chunk.
     */
    override fun toBytes(): ByteArray = Companion.toBytes(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt())
}
