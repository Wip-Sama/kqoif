package org.wip.kqoif

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
