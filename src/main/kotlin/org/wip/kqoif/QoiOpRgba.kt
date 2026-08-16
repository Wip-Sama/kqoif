package org.wip.kqoif

import java.util.logging.Logger

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
    companion object {
        private val logger: Logger = Logger.getLogger(QoiOpRgba::class.java.name)

        /** The tag for QOI_OP_RGBA, which is `11111111` in binary (0xFF). */
        val TAG: UByte = 0xFFu

        /** Total size of QOI_OP_RGBA chunk in bytes. */
        const val CHUNK_SIZE: Int = 5

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_RGBA tag `0xFF`.
         */
        fun matchTag(bytes: ByteArray, offset: Int = 0): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            return bytes[offset] == 0xFF.toByte()
        }

        /**
         * Deserializes a [QoiOpRgba] chunk from [bytes] at [offset].
         *
         * @param bytes Raw byte array containing the chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpRgba].
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpRgba {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RGBA. Expected at least $CHUNK_SIZE bytes, but only ${bytes.size - offset} bytes are available."
            }
            val tag = bytes[offset].toUByte()
            val red = bytes[offset + 1].toUByte()
            val green = bytes[offset + 2].toUByte()
            val blue = bytes[offset + 3].toUByte()
            val alpha = bytes[offset + 4].toUByte()

            val op = QoiOpRgba(tag, red, green, blue, alpha)
            if (!op.isValid()) {
                logger.warning("Deserialized QOI_OP_RGBA contains invalid tag: $op")
            }
            return op
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
    override fun toBytes(): ByteArray {
        val result = ByteArray(CHUNK_SIZE)
        result[0] = this.tag.toByte()
        result[1] = this.red.toByte()
        result[2] = this.green.toByte()
        result[3] = this.blue.toByte()
        result[4] = this.alpha.toByte()
        return result
    }
}
