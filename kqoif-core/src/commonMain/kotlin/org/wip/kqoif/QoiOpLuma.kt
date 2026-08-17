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
    companion object : QoiOpCompanion<QoiOpLuma> {
        /** The 2-bit tag for QOI_OP_LUMA (bits 7..6 are `10`, i.e. 0x02). */
        override val TAG: UByte = 0x02u

        /** Total size of QOI_OP_LUMA chunk in bytes. */
        override val CHUNK_SIZE: Int = 2

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
        override fun matchTag(bytes: ByteArray, offset: Int): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0x80u
        }

        /**
         * Checks if luma differences [dg], [dr_dg], [db_dg] are within their valid encodable ranges.
         */
        fun canEncode(dg: Int, dr_dg: Int, db_dg: Int): Boolean {
            return dg in MIN_DG..MAX_DG && dr_dg in MIN_DR_DB..MAX_DR_DB && db_dg in MIN_DR_DB..MAX_DR_DB
        }

        /**
         * Checks if the transition from [prev] to [curr] can be represented as a QOI_OP_LUMA chunk.
         */
        fun canEncode(prev: Color, curr: Color): Boolean {
            if (prev.a != curr.a) return false
            val vr = curr.r - prev.r
            val vg = curr.g - prev.g
            val vb = curr.b - prev.b
            val vgR = vr - vg
            val vgB = vb - vg
            return canEncode(vg, vgR, vgB)
        }

        /**
         * Writes a 2-byte QOI_OP_LUMA chunk directly into [out] at [offset].
         *
         * @param dg Green channel difference (-32..31).
         * @param dr_dg Red minus green difference (-8..7).
         * @param db_dg Blue minus green difference (-8..7).
         * @param out Destination byte array.
         * @param offset Starting offset in [out].
         * @return Number of bytes written (2).
         */
        fun writeBytes(dg: Int, dr_dg: Int, db_dg: Int, out: ByteArray, offset: Int = 0): Int {
            require(canEncode(dg, dr_dg, db_dg)) {
                "Luma differences out of range: dg=$dg, dr_dg=$dr_dg, db_dg=$db_dg."
            }
            require(out.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_LUMA. Expected at least $CHUNK_SIZE bytes, got ${out.size - offset}."
            }
            out[offset] = (0x80 or ((dg + DG_BIAS) and DG_MASK.toInt())).toByte()
            out[offset + 1] = ((((dr_dg + DR_DB_BIAS) and 0x0F) shl 4) or ((db_dg + DR_DB_BIAS) and 0x0F)).toByte()
            return CHUNK_SIZE
        }

        /**
         * Attempts to directly write a QOI_OP_LUMA chunk from [prev] and [curr] into [out] at [offset].
         *
         * @return Number of bytes written (2 if encodable, 0 otherwise).
         */
        fun tryWriteBytes(prev: Color, curr: Color, out: ByteArray, offset: Int = 0): Int {
            if (prev.a != curr.a) return 0
            val vr = curr.r - prev.r
            val vg = curr.g - prev.g
            val vb = curr.b - prev.b
            val vgR = vr - vg
            val vgB = vb - vg
            return if (canEncode(vg, vgR, vgB)) {
                writeBytes(vg, vgR, vgB, out, offset)
            } else {
                0
            }
        }

        /**
         * Serializes luma differences into a 2-byte QOI_OP_LUMA chunk.
         */
        fun toBytes(dg: Int, dr_dg: Int, db_dg: Int): ByteArray {
            val result = ByteArray(CHUNK_SIZE)
            writeBytes(dg, dr_dg, db_dg, result, 0)
            return result
        }

        /**
         * Serializes the difference between [prev] and [curr] into a 2-byte QOI_OP_LUMA chunk, or null if not encodable.
         */
        fun toBytes(prev: Color, curr: Color): ByteArray? {
            if (!canEncode(prev, curr)) return null
            val vr = curr.r - prev.r
            val vg = curr.g - prev.g
            val vb = curr.b - prev.b
            return toBytes(vg, vr - vg, vb - vg)
        }

        /**
         * Deserializes a [QoiOpLuma] from a 2-byte sequence starting at [offset].
         *
         * @param bytes Byte array containing the serialized QOI chunk.
         * @param offset Starting offset in [bytes].
         * @return The deserialized [QoiOpLuma].
         * @throws IllegalArgumentException if fewer than 2 bytes are available or tag is invalid.
         */
        override fun fromBytes(bytes: ByteArray, offset: Int): QoiOpLuma {
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

            return if (canEncode(vg, vgR, vgB) && prevColor.a == currColor.a) {
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
    override fun toBytes(): ByteArray = Companion.toBytes(dg.toInt(), dr_dg.toInt(), db_dg.toInt())
}
