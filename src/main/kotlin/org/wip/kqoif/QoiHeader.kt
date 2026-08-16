package org.wip.kqoif

import java.util.logging.Logger

/**
 * Represents the 14-byte QOI (Quite OK Image) file header.
 *
 * C struct specification:
 * ```c
 * struct qoi_header {
 *     char     magic[4];   // magic bytes "qoif"
 *     uint32_t width;      // image width in pixels (BE)
 *     uint32_t height;     // image height in pixels (BE)
 *     uint8_t  channels;   // 3 = RGB, 4 = RGBA
 *     uint8_t  colorspace; // 0 = sRGB with linear alpha, 1 = all channels linear
 * };
 * ```
 */
data class QoiHeader(
    val magic: ByteArray,
    val width: UInt,
    val height: UInt,
    val channels: UByte,
    val colorspace: UByte
) {
    companion object {
        private val logger: Logger = Logger.getLogger(QoiHeader::class.java.name)

        /** The standard 4-byte magic signature for QOI files ("qoif" in ASCII: 0x71, 0x6F, 0x69, 0x66). */
        val MAGIC: ByteArray = byteArrayOf(0x71, 0x6F, 0x69, 0x66)

        /** Total size of the QOI header in bytes as defined by the specification. */
        const val HEADER_SIZE: Int = 14

        /** 3-channel RGB image format. */
        val CHANNELS_RGB: UByte = 3u

        /** 4-channel RGBA image format. */
        val CHANNELS_RGBA: UByte = 4u

        /** sRGB colorspace with linear alpha channel. */
        val COLORSPACE_SRGB: UByte = 0u

        /** All channels linear colorspace. */
        val COLORSPACE_LINEAR: UByte = 1u

        /**
         * Deserializes a [QoiHeader] from a byte array starting at the specified [offset].
         *
         * @param bytes The raw byte array containing header bytes.
         * @param offset Starting position in [bytes] (defaults to 0).
         * @return The parsed [QoiHeader].
         * @throws IllegalArgumentException if the buffer has fewer than 14 bytes remaining.
         */
        fun fromBytes(bytes: ByteArray, offset: Int = 0): QoiHeader {
            require(bytes.size - offset >= HEADER_SIZE) {
                "Buffer too short for QOI header. Expected at least $HEADER_SIZE bytes, but only ${bytes.size - offset} bytes are available."
            }

            val magic = bytes.copyOfRange(offset, offset + 4)
            val width = ((bytes[offset + 4].toUInt() and 0xFFu) shl 24) or
                    ((bytes[offset + 5].toUInt() and 0xFFu) shl 16) or
                    ((bytes[offset + 6].toUInt() and 0xFFu) shl 8) or
                    (bytes[offset + 7].toUInt() and 0xFFu)
            val height = ((bytes[offset + 8].toUInt() and 0xFFu) shl 24) or
                    ((bytes[offset + 9].toUInt() and 0xFFu) shl 16) or
                    ((bytes[offset + 10].toUInt() and 0xFFu) shl 8) or
                    (bytes[offset + 11].toUInt() and 0xFFu)
            val channels = (bytes[offset + 12].toInt() and 0xFF).toUByte()
            val colorspace = (bytes[offset + 13].toInt() and 0xFF).toUByte()

            val header = QoiHeader(
                magic = magic,
                width = width,
                height = height,
                channels = channels,
                colorspace = colorspace
            )

            if (!header.isValid()) {
                logger.warning("Deserialized QOI header does not conform to standard specification: $header")
            }

            return header
        }
    }

    init {
        require(magic.size == 4) { "QOI magic must be exactly 4 bytes, got ${magic.size} bytes." }
//        require(width > 0u) { "Image width must be greater than 0, got $width." }
//        require(height > 0u) { "Image height must be greater than 0, got $height." }
//        require(channels == CHANNELS_RGB || channels == CHANNELS_RGBA) { "Channels must be either 3 (RGB) or 4 (RGBA), got $channels." }
//        require(colorspace == COLORSPACE_SRGB || colorspace == COLORSPACE_LINEAR) { "Colorspace must be either 0 (sRGB) or 1 (Linear), got $colorspace." }
    }

    /**
     * Convenience constructor with default standard magic ("qoif") and [UByte] channel/colorspace parameters.
     */
    constructor(
        width: UInt,
        height: UInt,
        channels: UByte = CHANNELS_RGBA,
        colorspace: UByte = COLORSPACE_SRGB
    ) : this(MAGIC.copyOf(), width, height, channels, colorspace)

    /**
     * Convenience constructor supporting [UInt] for channels and colorspace with standard magic ("qoif").
     */
    constructor(
        width: UInt,
        height: UInt,
        channels: UInt,
        colorspace: UInt
    ) : this(MAGIC.copyOf(), width, height, channels.toUByte(), colorspace.toUByte())

    /**
     * Validates whether this header meets all QOI specification criteria:
     * - Magic matches "qoif" (0x71, 0x6F, 0x69, 0x66)
     * - Width > 0
     * - Height > 0
     * - Channels is 3 (RGB) or 4 (RGBA)
     * - Colorspace is 0 (sRGB) or 1 (Linear)
     */
    fun isValid(): Boolean {
        return magic.contentEquals(MAGIC) &&
                width > 0u &&
                height > 0u &&
                (channels == CHANNELS_RGB || channels == CHANNELS_RGBA) &&
                (colorspace == COLORSPACE_SRGB || colorspace == COLORSPACE_LINEAR)
    }

    /**
     * Serializes this header into a 14-byte QOI binary header format with big-endian integer fields.
     */
    fun toBytes(): ByteArray {
        val result = ByteArray(HEADER_SIZE)
        magic.copyInto(result, destinationOffset = 0, startIndex = 0, endIndex = 4)

        // Width (32-bit unsigned int, big-endian)
        result[4] = ((width shr 24) and 0xFFu).toByte()
        result[5] = ((width shr 16) and 0xFFu).toByte()
        result[6] = ((width shr 8) and 0xFFu).toByte()
        result[7] = (width and 0xFFu).toByte()

        // Height (32-bit unsigned int, big-endian)
        result[8] = ((height shr 24) and 0xFFu).toByte()
        result[9] = ((height shr 16) and 0xFFu).toByte()
        result[10] = ((height shr 8) and 0xFFu).toByte()
        result[11] = (height and 0xFFu).toByte()

        // Channels (8-bit unsigned int)
        result[12] = channels.toByte()

        // Colorspace (8-bit unsigned int)
        result[13] = colorspace.toByte()

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QoiHeader

        if (!magic.contentEquals(other.magic)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (channels != other.channels) return false
        if (colorspace != other.colorspace) return false

        return true
    }

    override fun hashCode(): Int {
        var result = magic.contentHashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + channels.hashCode()
        result = 31 * result + colorspace.hashCode()
        return result
    }

    override fun toString(): String {
        val magicStr = String(magic, Charsets.US_ASCII)
        return "QoiHeader(magic='$magicStr', width=$width, height=$height, channels=$channels, colorspace=$colorspace)"
    }
}

/**
 * Type alias for backward compatibility or matching C struct naming convention.
 */
typealias qoiHeader = QoiHeader
