package org.wip.kqoif

/**
 * Represents the 1-byte QOI_OP_RUN chunk.
 * - 2-bit tag `0b11`
 * - 6-bit run-length repeating the previous pixel: 1..62
 *
 * The run-length is stored with a bias of -1 (encoded as `run - 1`).
 * Note that run-lengths 63 and 64 (b111110 and b111111) are illegal as they are
 * occupied by the QOI_OP_RGB and QOI_OP_RGBA tags.
 *
 * ```
 * ┌─ QOI_OP_RUN ────┐
 * │     Byte[0]     │
 * │ 7 6 5 4 3 2 1 0 │
 * │─────┼───────────│
 * │ 1 1 │    run    │
 * └─────────────────┘
 * ```
 */
data class QoiOpRun(
    override val tag: UByte,
    val run: UByte
) : QoiOp {
    companion object : QoiOpCompanion<QoiOpRun> {
        /** The 2-bit tag for QOI_OP_RUN (bits 7..6 are `11`, i.e. 0x03). */
        override val TAG: UByte = 0x03u

        /** Total size of the QOI_OP_RUN chunk in bytes. */
        override val CHUNK_SIZE: Int = 1

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Bitmask for the 6-bit run field (`0b00111111` = `0x3F`). */
        const val RUN_MASK: UInt = 0x3Fu

        /** Bias applied to run length when stored (-1). */
        const val BIAS: Int = -1

        /** Minimum run length (1 pixel). */
        val MIN_RUN: UByte = 1u

        /** Maximum run length (62 pixels). Stored values 62 and 63 are illegal as they collide with QOI_OP_RGB / RGBA. */
        val MAX_RUN: UByte = 62u

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_RUN tag `0b11`.
         * Excludes `0xFE` (RGB) and `0xFF` (RGBA).
         */
        override fun matchTag(bytes: ByteArray, offset: Int): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0xC0u && byte != 0xFEu && byte != 0xFFu
        }

        /**
         * Writes a 1-byte QOI_OP_RUN chunk directly into [out] at [offset].
         *
         * @param run Run length between 1 and 62.
         * @param out Destination byte array.
         * @param offset Starting offset in [out].
         * @return Number of bytes written (1).
         */
        fun writeBytes(run: Int, out: ByteArray, offset: Int = 0): Int {
            require(run in 1..62) { "Run length must be between 1 and 62, but got $run." }
            require(out.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RUN. Expected at least $CHUNK_SIZE byte, but only ${out.size - offset} bytes are available."
            }
            val stored = (run + BIAS) and RUN_MASK.toInt()
            out[offset] = (0xC0 or stored).toByte()
            return CHUNK_SIZE
        }

        /**
         * Writes a 1-byte QOI_OP_RUN chunk directly into [out] at [offset].
         */
        fun writeBytes(run: UByte, out: ByteArray, offset: Int = 0): Int = writeBytes(run.toInt(), out, offset)

        /**
         * Serializes a run length (1..62) into a 1-byte QOI_OP_RUN chunk.
         */
        fun toBytes(run: Int): ByteArray {
            val result = ByteArray(CHUNK_SIZE)
            writeBytes(run, result, 0)
            return result
        }

        /**
         * Serializes a run length (1..62) into a 1-byte QOI_OP_RUN chunk.
         */
        fun toBytes(run: UByte): ByteArray = toBytes(run.toInt())

        /**
         * Deserializes a [QoiOpRun] from a single byte in [bytes] starting at [offset].
         * Unbiases the stored value (+1) to decode the actual run length.
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpRun].
         * @throws IllegalArgumentException if fewer than 1 byte is available or tag/run is invalid.
         */
        override fun fromBytes(bytes: ByteArray, offset: Int): QoiOpRun {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_RUN. Expected at least $CHUNK_SIZE byte, but only ${bytes.size - offset} bytes are available."
            }
            val byte = bytes[offset].toUByte()
            val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
            val stored = (byte.toUInt() and RUN_MASK).toInt()
            val run = (stored - BIAS).toUByte() // stored + 1

            return QoiOpRun(tag, run)
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_RUN. Expected 0x03 (0b11), but got $tag."
        }
        require(run in MIN_RUN..MAX_RUN) {
            "Run length must be between 1 and 62, but got $run."
        }
    }

    constructor(run: UByte) : this(TAG, run)
    constructor(run: Int) : this(TAG, run.toUByte())

    /**
     * Checks if this operation has a valid tag (0x03) and run length in 1..62.
     */
    override fun isValid(): Boolean {
        return tag == TAG && run in MIN_RUN..MAX_RUN
    }

    /**
     * Serializes this operation into a 1-byte QOI chunk applying bias -1 (stored as `run - 1`).
     */
    override fun toBytes(): ByteArray = Companion.toBytes(run.toInt())
}
