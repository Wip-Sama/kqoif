package org.wip.kqoif

/**
 * Represents the 2-byte QOI_OP_LUMA chunk.
 *
 * ```
 * ┌─ QOI_OP_LUMA────┬─────────────────┐
 * │     Byte[0]     │     Byte[1]     │
 * │ 7 6 5 4 3 2 1 0 │ 7 6 5 4 3 2 1 0 │
 * │─────┼───────────┼────────┼────────│
 * │ 1 0 │ diff green│ dr - dg│ db - dg│
 * └─────┴───────────┴────────┴────────┘
 * ```
 *
 * - 2-bit tag `0b10`
 * - 6-bit green channel difference from the previous pixel (-32..31) with bias 32
 * - 4-bit red channel difference minus green channel difference (-8..7) with bias 8
 * - 4-bit blue channel difference minus green channel difference (-8..7) with bias 8
 */
data class QoiOpLuma(
    override val tag: UByte,
    val dg: Byte,
    val dr_dg: Byte,
    val db_dg: Byte
) : QoiOp {
    companion object {
        /** The 2-bit tag for QOI_OP_LUMA (bits 7..6 are `10`, i.e. 0x02). */
        val TAG: UByte = 0x02u

        /** Total size of QOI_OP_LUMA chunk in bytes. */
        const val CHUNK_SIZE: Int = 2

        /** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
        const val TAG_MASK: UInt = 0xC0u

        /** Bitmask for the 6-bit dg field (`0b00111111` = `0x3F`). */
        const val DG_MASK: UInt = 0x3Fu

        /** Bitmask for the 4-bit dr_dg field (`0b11110000` = `0xF0`). */
        const val DR_DG_MASK: UInt = 0xF0u

        /** Bitmask for the 4-bit db_dg field (`0b00001111` = `0x0F`). */
        const val DB_DG_MASK: UInt = 0x0Fu

        /** Bias applied to green channel difference when stored (32). */
        const val DG_BIAS: Int = 32

        /** Bias applied to dr_dg and db_dg differences when stored (8). */
        const val DR_DB_BIAS: Int = 8

        /** Minimum green channel difference from the previous pixel (-32). */
        const val MIN_DG: Byte = -32

        /** Maximum green channel difference from the previous pixel (31). */
        const val MAX_DG: Byte = 31

        /** Minimum red and blue channel difference minus green difference (-8). */
        const val MIN_DR_DB: Byte = -8

        /** Maximum red and blue channel difference minus green difference (7). */
        const val MAX_DR_DB: Byte = 7

        /**
         * Checks if the byte at [offset] in [bytes] matches the QOI_OP_LUMA tag `0b10`.
         */
        fun matchTag(bytes: ByteArray, offset: Int = 0): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0x80u
        }

        /**
         * Deserializes a [QoiOpLuma] from a 2-byte sequence starting at [offset].
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpLuma].
         * @throws IllegalArgumentException if fewer than 2 bytes are available or tag is invalid.
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpLuma {
            require(bytes.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_LUMA. Expected at least $CHUNK_SIZE bytes, got ${bytes.size - offset}."
            }
            val tag = ((bytes[offset].toUInt() and TAG_MASK) shr 6).toUByte()
            val dg = ((bytes[offset].toUInt() and DG_MASK).toInt() - DG_BIAS).toByte()
            val dr_dg = (((bytes[offset + 1].toUInt() and DR_DG_MASK) shr 4).toInt() - DR_DB_BIAS).toByte()
            val db_dg = ((bytes[offset + 1].toUInt() and DB_DG_MASK).toInt() - DR_DB_BIAS).toByte()

            return QoiOpLuma(tag, dg, dr_dg, db_dg)
        }

        /**
         * Attempts to construct a [QoiOpLuma] from the differences between [prevColor] and [currColor].
         *
         * @return [QoiOpLuma] if green difference is in `-32..31` and relative red/blue diffs are in `-8..7`, or `null` otherwise.
         */
        fun fromColors(prevColor: Color, currColor: Color): QoiOpLuma? {
            val vr = currColor.r - prevColor.r
            val vg = currColor.g - prevColor.g
            val vb = currColor.b - prevColor.b

            val vgR = vr - vg
            val vgB = vb - vg

            return if (vg in MIN_DG..MAX_DG && vgR in MIN_DR_DB..MAX_DR_DB && vgB in MIN_DR_DB..MAX_DR_DB) {
                QoiOpLuma(dg = vg, dr_dg = vgR, db_dg = vgB)
            } else {
                null
            }
        }
    }

    init {
        require(tag == TAG) {
            "Invalid tag for QOI_OP_LUMA. Expected 0x02 (0b10), but got $tag."
        }
        require(dg in MIN_DG..MAX_DG) { "Green difference must be in range -32..31, got $dg." }
        require(dr_dg in MIN_DR_DB..MAX_DR_DB) { "dr_dg must be in range -8..7, got $dr_dg." }
        require(db_dg in MIN_DR_DB..MAX_DR_DB) { "db_dg must be in range -8..7, got $db_dg." }
    }

    constructor(dg: Byte, dr_dg: Byte, db_dg: Byte) : this(TAG, dg, dr_dg, db_dg)
    constructor(dg: Int, dr_dg: Int, db_dg: Int) : this(TAG, dg.toByte(), dr_dg.toByte(), db_dg.toByte())

    /**
     * Reconstructs the new [Color] by applying luma green and relative differences to [prevColor].
     */
    fun toColor(prevColor: Color): Color {
        val r = (prevColor.r + dg.toInt() + dr_dg.toInt()) and 0xFF
        val g = (prevColor.g + dg.toInt()) and 0xFF
        val b = (prevColor.b + dg.toInt() + db_dg.toInt()) and 0xFF
        return Color(r, g, b, prevColor.a)
    }

    /**
     * Checks if this operation has a valid tag (0x02) and differences within allowed ranges.
     */
    override fun isValid(): Boolean {
        return tag == TAG &&
                dg in MIN_DG..MAX_DG &&
                dr_dg in MIN_DR_DB..MAX_DR_DB &&
                db_dg in MIN_DR_DB..MAX_DR_DB
    }

    /**
     * Serializes this operation into a 2-byte QOI chunk applying the biases (32 for dg, 8 for dr_dg/db_dg).
     */
    override fun toBytes(): ByteArray {
        val byte0 = (((tag.toUInt() and 0x03u) shl 6) or ((dg.toInt() + DG_BIAS).toUInt() and DG_MASK)).toByte()
        val byte1 = ((((dr_dg.toInt() + DR_DB_BIAS).toUInt() and 0x0Fu) shl 4) or ((db_dg.toInt() + DR_DB_BIAS).toUInt() and 0x0Fu)).toByte()
        return byteArrayOf(byte0, byte1)
    }
}
