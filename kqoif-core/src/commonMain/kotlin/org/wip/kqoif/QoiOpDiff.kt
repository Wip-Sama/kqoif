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
    companion object : QoiOpCompanion<QoiOpDiff> {
        /** The 2-bit tag for QOI_OP_DIFF (bits 7..6 are `01`). */
        override val TAG: UByte = 0x01u

        /** Total size of the QOI_OP_DIFF chunk in bytes. */
        override val CHUNK_SIZE: Int = 1

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
        override fun matchTag(bytes: ByteArray, offset: Int): Boolean {
            if (bytes.size - offset < CHUNK_SIZE) return false
            val byte = bytes[offset].toUByte().toUInt()
            return (byte and TAG_MASK) == 0x40u
        }

        /**
         * Checks if channel differences [dr], [dg], [db] are within the encodable range (-2..1).
         */
        fun canEncode(dr: Int, dg: Int, db: Int): Boolean {
            return dr in MIN_DIFF..MAX_DIFF && dg in MIN_DIFF..MAX_DIFF && db in MIN_DIFF..MAX_DIFF
        }

        /**
         * Checks if the transition from [prev] to [curr] can be represented as a QOI_OP_DIFF chunk.
         */
        fun canEncode(prev: Color, curr: Color): Boolean {
            if (prev.a != curr.a) return false
            val vr = curr.r - prev.r
            val vg = curr.g - prev.g
            val vb = curr.b - prev.b
            return canEncode(vr, vg, vb)
        }

        /**
         * Writes a 1-byte QOI_OP_DIFF chunk directly into [out] at [offset].
         *
         * @param dr Red channel difference (-2..1).
         * @param dg Green channel difference (-2..1).
         * @param db Blue channel difference (-2..1).
         * @param out Destination byte array.
         * @param offset Starting offset in [out].
         * @return Number of bytes written (1).
         */
        fun writeBytes(dr: Int, dg: Int, db: Int, out: ByteArray, offset: Int = 0): Int {
            require(canEncode(dr, dg, db)) {
                "Channel differences must be in range -2..1 (dr=$dr, dg=$dg, db=$db)."
            }
            require(out.size - offset >= CHUNK_SIZE) {
                "Buffer too short for QOI_OP_DIFF. Expected at least $CHUNK_SIZE byte, but only ${out.size - offset} bytes are available."
            }
            val rBias = (dr + BIAS) and 0x03
            val gBias = (dg + BIAS) and 0x03
            val bBias = (db + BIAS) and 0x03
            out[offset] = (0x40 or (rBias shl 4) or (gBias shl 2) or bBias).toByte()
            return CHUNK_SIZE
        }

        /**
         * Attempts to directly write a QOI_OP_DIFF chunk from [prev] and [curr] into [out] at [offset].
         *
         * @return Number of bytes written (1 if encodable, 0 otherwise).
         */
        fun tryWriteBytes(prev: Color, curr: Color, out: ByteArray, offset: Int = 0): Int {
            if (prev.a != curr.a) return 0
            val vr = curr.r - prev.r
            val vg = curr.g - prev.g
            val vb = curr.b - prev.b
            return if (canEncode(vr, vg, vb)) {
                writeBytes(vr, vg, vb, out, offset)
            } else {
                0
            }
        }

        /**
         * Serializes channel differences into a 1-byte QOI_OP_DIFF chunk.
         */
        fun toBytes(dr: Int, dg: Int, db: Int): ByteArray {
            val result = ByteArray(CHUNK_SIZE)
            writeBytes(dr, dg, db, result, 0)
            return result
        }

        /**
         * Serializes the difference between [prev] and [curr] into a 1-byte QOI_OP_DIFF chunk, or null if not encodable.
         */
        fun toBytes(prev: Color, curr: Color): ByteArray? {
            if (!canEncode(prev, curr)) return null
            return toBytes(curr.r - prev.r, curr.g - prev.g, curr.b - prev.b)
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
        override fun fromBytes(bytes: ByteArray, offset: Int): QoiOpDiff {
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
            return if (canEncode(vr, vg, vb) && prevColor.a == currColor.a) {
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
    override fun toBytes(): ByteArray = Companion.toBytes(dr.toInt(), dg.toInt(), db.toInt())
}
