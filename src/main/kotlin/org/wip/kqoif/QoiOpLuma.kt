package org.wip.kqoif

import java.util.logging.Logger

/**
 * Represents the 2-byte QOI_OP_LUMA chunk.
 * - 2-bit tag `0b10`
 * - 6-bit green channel difference from the previous pixel (-32..31)
 * - 4-bit red channel difference minus green channel difference (-8..7)
 * - 4-bit blue channel difference minus green channel difference (-8..7)
 *
 * The green channel is used to indicate the general direction of
 * change and is encoded in 6 bits. The red and blue channels (dr
 * and db) base their diffs off of the green channel difference:
 *  dr_dg = (cur_px.r - prev_px.r) - (cur_px.g - prev_px.g)
 *  db_dg = (cur_px.b - prev_px.b) - (cur_px.g - prev_px.g)
 *
 * Values are stored as unsigned integers with a bias of 32 for the
 * green channel and a bias of 8 for the red and blue channel.
 *
 * ┌─ QOI_OP_LUMA────┬─────────────────┐
 * │     Byte[0]     │     Byte[1]     │
 * │ 7 6 5 4 3 2 1 0 │ 7 6 5 4 3 2 1 0 │
 * │─────┼───────────┼────────┼────────│
 * │ 1 0 │ diff green│ dr - dg│ db - dg│
 * └─────┴───────────┴────────┴────────┘
 */
data class QoiOpLuma(
    val tag: UByte,
    val dg: Byte,
    val dr_dg: Byte,
    val db_dg: Byte
) {
    companion object {
        private val logger: Logger = Logger.getLogger(QoiOpLuma::class.java.name)

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
            val byte0 = bytes[offset].toUByte()
            val byte1 = bytes[offset + 1].toUByte()

            val tag = ((byte0.toUInt() and TAG_MASK) shr 6).toUByte()
            val dg = ((byte0.toUInt() and DG_MASK).toInt() - DG_BIAS).toByte()
            val dr_dg = (((byte1.toUInt() and DR_DG_MASK) shr 4).toInt() - DR_DB_BIAS).toByte()
            val db_dg = ((byte1.toUInt() and DB_DG_MASK).toInt() - DR_DB_BIAS).toByte()

            val op = QoiOpLuma(tag, dg, dr_dg, db_dg)
            if (!op.isValid()) {
                logger.warning("Deserialized QOI_OP_LUMA contains invalid values: $op")
            }
            return op
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
     * Checks if this operation has a valid tag (0x02) and differences within allowed ranges.
     */
    fun isValid(): Boolean {
        return tag == TAG &&
                dg in MIN_DG..MAX_DG &&
                dr_dg in MIN_DR_DB..MAX_DR_DB &&
                db_dg in MIN_DR_DB..MAX_DR_DB
    }

    /**
     * Serializes this operation into a 2-byte QOI chunk applying the biases (32 for dg, 8 for dr_dg/db_dg).
     */
    fun toBytes(): ByteArray {
        val byte0 = (((tag.toUInt() and 0x03u) shl 6) or ((dg.toInt() + DG_BIAS).toUInt() and DG_MASK)).toByte()
        val byte1 = ((((dr_dg.toInt() + DR_DB_BIAS).toUInt() and 0x0Fu) shl 4) or ((db_dg.toInt() + DR_DB_BIAS).toUInt() and 0x0Fu)).toByte()
        return byteArrayOf(byte0, byte1)
    }
}