package org.wip.kqoif

/**
 * Represents the 1-byte QOI_OP_DIFF chunk.
 *
 * ```
 * ┌─ QOI_OP_DIFF ───┐
 * │     Byte[0]     │
 * │ 7 6 5 4 3 2 1 0 │
 * │─────┼───┼───┼───│
 * │ 0 1 │ dr│ dg│ db│
 * └─────────────────┘
 * ```
 *
 * Values dr, dg, db are differences from the previous pixel (-2..1).
 * Stored with a bias of 2:
 * - dr (-2..1) stored as (dr + 2) in bits [5..4]
 * - dg (-2..1) stored as (dg + 2) in bits [3..2]
 * - db (-2..1) stored as (db + 2) in bits [1..0]
 */
data class QoiOpDiff(
    override val tag: UByte,
    val dr: Byte,
    val dg: Byte,
    val db: Byte
) : QoiOp {
    companion object {
        /** The 2-bit tag for QOI_OP_DIFF (bits 7..6 are `01`). */
        val TAG: UByte = 0x01u

        /** Total size of the QOI_OP_DIFF chunk in bytes. */
        const val CHUNK_SIZE: Int = 1

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Storage bias for channel differences (2). */
        const val BIAS: Int = 2

        /** Minimum channel difference (-2). */
        const val MIN_DIFF: Byte = -2

        /** Maximum channel difference (1). */
        const val MAX_DIFF: Byte = 1

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_DIFF tag `0b01`.
         */
        fun matchTag(bytes: ByteArray, offset: Int = 0): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0x40u
        }

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
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_DIFF. Expected at least $CHUNK_SIZE byte, but only ${bytes.size - offset} bytes are available."
            }
            val byte = bytes[offset].toUByte()
            val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
            val dr = (((byte.toUInt() shr 4) and 0x03u).toInt() - BIAS).toByte()
            val dg = (((byte.toUInt() shr 2) and 0x03u).toInt() - BIAS).toByte()
            val db = ((byte.toUInt() and 0x03u).toInt() - BIAS).toByte()

            return QoiOpDiff(tag, dr, dg, db)
        }

        /**
         * Attempts to construct a [QoiOpDiff] from the difference between [prevColor] and [currColor].
         *
         * @return [QoiOpDiff] if all channel differences (R, G, B) are in `-2..1`, or `null` otherwise.
         */
        fun fromColors(prevColor: Color, currColor: Color): QoiOpDiff? {
            val vr = currColor.r - prevColor.r
            val vg = currColor.g - prevColor.g
            val vb = currColor.b - prevColor.b
            return if (vr in MIN_DIFF..MAX_DIFF && vg in MIN_DIFF..MAX_DIFF && vb in MIN_DIFF..MAX_DIFF) {
                QoiOpDiff(dr = vr, dg = vg, db = vb)
            } else {
                null
            }
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
     * Reconstructs the new [Color] by applying differences to [prevColor] with 8-bit wraparound.
     */
    fun toColor(prevColor: Color): Color {
        val r = (prevColor.r + dr.toInt()) and 0xFF
        val g = (prevColor.g + dg.toInt()) and 0xFF
        val b = (prevColor.b + db.toInt()) and 0xFF
        return Color(r, g, b, prevColor.a)
    }

    /**
     * Checks if this operation has a valid tag (0x01) and all differences are in -2..1.
     */
    override fun isValid(): Boolean {
        return tag == TAG &&
                dr in MIN_DIFF..MAX_DIFF &&
                dg in MIN_DIFF..MAX_DIFF &&
                db in MIN_DIFF..MAX_DIFF
    }

    /**
     * Serializes this operation into a 1-byte QOI chunk adding the bias (2).
     */
    override fun toBytes(): ByteArray {
        val rBias = (dr.toInt() + BIAS) and 0x03
        val gBias = (dg.toInt() + BIAS) and 0x03
        val bBias = (db.toInt() + BIAS) and 0x03
        val rawByte = (((tag.toUInt() and 0x03u) shl 6) or (rBias.toUInt() shl 4) or (gBias.toUInt() shl 2) or bBias.toUInt()).toByte()
        return byteArrayOf(rawByte)
    }
}
