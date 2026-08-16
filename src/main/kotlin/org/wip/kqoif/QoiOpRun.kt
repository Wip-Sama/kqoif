package org.wip.kqoif

import java.util.logging.Logger

/**
 * Represents the 1-byte QOI_OP_RUN chunk.
┌─ QOI_OP_RUN ────┐
│     Byte[0]     │
│ 7 6 5 4 3 2 1 0 │
│─────┼───────────│
│ 1 1 │    run    │
└─────────────────┘
 */
data class QoiOpRun(
	val tag: UByte,
	val index: UByte
) {
	companion object {
		private val logger: Logger = Logger.getLogger(QoiOpRun::class.java.name)

		/** The 2-bit tag for QOI_OP_INDEX (bits 7..6 are `11`). */
		val TAG: UByte = 0x11u

		/** Bitmask for the 2-bit tag (`0b11000000` = `0xC0`). */
		const val TAG_MASK: UInt = 0xC0u

		/** Bitmask for the 6-bit index (`0b00111111` = `0x3F`). */
		const val INDEX_MASK: UInt = 0x3Fu

		/** Maximum allowed index value (63). */
		val MAX_INDEX: UByte = 63u

		/**
		 * Deserializes a [QoiOpRun] from a single byte in [bytes] starting at [offset].
		 *
		 * @param bytes Byte array containing the serialized QOI chunk.
		 * @param offset Starting offset in [bytes].
		 * @return The deserialized [QoiOpRun].
		 * @throws IllegalArgumentException if fewer than 1 byte is available or tag is invalid.
		 */
		fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiOpRun {
			require(bytes.size - offset >= 1) {
				"Buffer too short for QOI_OP_INDEX. Expected at least 1 byte, but only ${bytes.size - offset} bytes are available."
			}
			val byte = bytes[offset].toUByte()
			val tag = ((byte.toUInt() and TAG_MASK) shr 6).toUByte()
			val index = (byte.toUInt() and INDEX_MASK).toUByte()

			val op = QoiOpRun(tag, index)
			if (!op.isValid()) {
				logger.warning("Deserialized QOI_OP_INDEX contains invalid values: $op")
			}
			return op
		}
	}

	init {
		require(tag == TAG) {
			"Invalid tag for QOI_OP_INDEX. Expected 0x00, but got $tag."
		}
		require(index <= MAX_INDEX) {
			"Index value must be between 0 and 63, but got $index."
		}
	}

	constructor(index: UByte) : this(TAG, index)
	constructor(index: Int) : this(TAG, index.toUByte())

	/**
	 * Checks if this operation has a valid tag (0x00) and index (0..63).
	 */
	fun isValid(): Boolean {
		return tag == TAG && index <= MAX_INDEX
	}

	/**
	 * Serializes this operation into a 1-byte QOI chunk.
	 */
	fun toBytes(): ByteArray {
		val rawByte = (((tag.toUInt() and 0x03u) shl 6) or (index.toUInt() and INDEX_MASK)).toByte()
		return byteArrayOf(rawByte)
	}
}
