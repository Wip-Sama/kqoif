package org.wip.kqoif

/**
 * Strategy used when encoding QOI images.
 */
enum class QoiEncoderStrategy {
    /**
     * Transitive / direct zero-allocation stream encoding from Color values directly into byte arrays.
     */
    DIRECT,

    /**
     * Object-based AST encoding instantiating intermediate [QoiOp] instances and byte arrays.
     */
    OBJECT
}

/**
 * Interface representing common metadata and deserialization for QOI chunk constructs.
 */
interface QoiChunkCompanion<out T> {
    /** Total size of this chunk in bytes. */
    val CHUNK_SIZE: Int

    /**
     * Deserializes a construct [T] from [bytes] at [offset].
     *
     * @param bytes Raw byte array.
     * @param offset Starting offset in [bytes].
     * @return The deserialized construct of type [T].
     */
    fun fromBytes(bytes: ByteArray, offset: Int = 0): T
}

/**
 * Interface representing common metadata, tag matching, and deserialization for QOI operation companion objects.
 */
interface QoiOpCompanion<out T : QoiOp> : QoiChunkCompanion<T> {
    /** The operation tag identifier byte/value. */
    val TAG: UByte

    /**
     * Checks if the byte sequence at [offset] in [bytes] matches this operation's tag.
     *
     * @param bytes Raw byte array.
     * @param offset Starting offset in [bytes].
     * @return `true` if matching, `false` otherwise.
     */
    fun matchTag(bytes: ByteArray, offset: Int = 0): Boolean
}

/**
 * Common interface representing any QOI (Quite OK Image) chunk operation.
 *
 * Each QOI operation chunk specifies an identifier tag, is serializable to raw bytes,
 * and can be validated against the QOI specification.
 */
interface QoiOp {
    /**
     * The tag identifier identifying the operation type.
     */
    val tag: UByte

    /**
     * Serializes this QOI operation into its raw byte representation.
     *
     * @return Byte array containing the serialized chunk.
     */
    fun toBytes(): ByteArray

    /**
     * Checks if this operation contains valid values conforming to the QOI specification.
     *
     * @return `true` if valid, `false` otherwise.
     */
    fun isValid(): Boolean
}
