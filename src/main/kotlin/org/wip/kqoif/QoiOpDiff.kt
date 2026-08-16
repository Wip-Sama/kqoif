package org.wip.kqoif

import java.util.logging.Logger

/**
 * Represents the 1-byte QOI_OP_DIFF chunk.
 * - 2-bit tag b01
 * - 2-bit red channel difference from the previous pixel -2..1
 * - 2-bit green channel difference from the previous pixel -2..1
 * - 2-bit blue channel difference from the previous pixel -2..1
 *
 * The difference to the current channel values are using a wraparound
 * operation, so 1 - 2 will result in 255, while 255 + 1 will result
 * in 0.
 *
 * Values are stored as unsigned integers with a bias of 2. E.g. -2
 * is stored as 0 (b00). 1 is stored as 3 (b11).
 *
 * The alpha value remains unchanged from the previous pixel.
 *
 * ┌─ QOI_OP_DIFF ───┐
 * │     Byte[0]     │
 * │ 7 6 5 4 3 2 1 0 │
 * │─────┼───┼───┼───│
 * │ 0 1 │dr │dg │db │
 * └─────┴───┴───┴───┘
 */
data class QoiOpDiff(
    val tag: UByte,
    val dr: Byte,
    val dg: Byte,
    val db: Byte
) {
    companion object {
        private val logger: Logger = Logger.getLogger(QoiOpDiff::class.java.name)

        /** The 2-bit tag for QOI_OP_DIFF (bits 7..6 are `01`). */
        val TAG: UByte = 0x01u

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Storage bias for channel differences (2). */
        const val BIAS: Int = 2

        /** Minimum channel difference (-2). */
        const val MIN_DIFF: Byte = -2

        /** Maximum channel difference (1). */
        const val MAX_DIFF: Byte = 1

        /**
         * Deserializes a [QoiOpDiff] from a single byte in [bytes] starting at [offset].
         * Subtracts the bias (2) to decode actual channel differences.
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpDiff].
         * @throws IllegalArgumentException if fewer than 1 byte is available or tag/diff is invalid.
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpDiff {
            require(bytes.size - offset >= 1) {
                "Buffer too short for QOI_OP_DIFF. Expected at least 1 byte, but only ${bytes.size - offset} bytes are available."
            }
            val byte = bytes[offset].toUByte()
            val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
            val dr = (((byte.toUInt() shr 4) and 0x03u).toInt() - BIAS).toByte()
            val dg = (((byte.toUInt() shr 2) and 0x03u).toInt() - BIAS).toByte()
            val db = ((byte.toUInt() and 0x03u).toInt() - BIAS).toByte()

            val op = QoiOpDiff(tag, dr, dg, db)
            if (!op.isValid()) {
                logger.warning("Deserialized QOI_OP_DIFF contains invalid values: $op")
            }
            return op
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_DIFF. Expected 0x01 (0b01), but got $tag."
        }
        require(dr in MIN_DIFF..MAX_DIFF) { "Red difference must be in range -2..1, got $dr." }
        require(dg in MIN_DIFF..MAX_DIFF) { "Green difference must be in range -2..1, got $dg." }
        require(db in MIN_DIFF..MAX_DIFF) { "Blue difference must be in range -2..1, got $db." }
    }

    constructor(dr: Byte, dg: Byte, db: Byte) : this(TAG, dr, dg, db)
    constructor(dr: Int, dg: Int, db: Int) : this(TAG, dr.toByte(), dg.toByte(), db.toByte())

    /**
     * Checks if this operation has a valid tag (0x01) and all differences are in -2..1.
     */
    fun isValid(): Boolean {
        return tag == TAG &&
                dr in MIN_DIFF..MAX_DIFF &&
                dg in MIN_DIFF..MAX_DIFF &&
                db in MIN_DIFF..MAX_DIFF
    }

    /**
     * Serializes this operation into a 1-byte QOI chunk adding the bias (2).
     */
    fun toBytes(): ByteArray {
        val rBias = (dr.toInt() + BIAS) and 0x03
        val gBias = (dg.toInt() + BIAS) and 0x03
        val bBias = (db.toInt() + BIAS) and 0x03
        val rawByte = (((tag.toUInt() and 0x03u) shl 6) or (rBias.toUInt() shl 4) or (gBias.toUInt() shl 2) or bBias.toUInt()).toByte()
        return byteArrayOf(rawByte)
    }
}