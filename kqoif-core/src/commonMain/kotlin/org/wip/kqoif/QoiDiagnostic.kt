package org.wip.kqoif

import kotlin.math.abs
import kotlin.math.round

/**
 * Formats a [Double] number with a fixed number of [decimals] digits, preserving trailing zeros.
 *
 * @param decimals The number of fractional decimal places to format (default: 2).
 * @return Formatted string representation.
 */
fun Double.formatDecimals(decimals: Int = 2): String {
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"
    val isNegative = this < 0.0
    val absValue = abs(this)
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = round(absValue * factor).toLong()
    val integerPart = rounded / factor
    val fractionPart = (rounded % factor).toString().padStart(decimals, '0')
    val prefix = if (isNegative && (integerPart > 0 || rounded > 0)) "-" else ""
    return if (decimals > 0) "$prefix$integerPart.$fractionPart" else "$prefix$integerPart"
}

private fun Double.format2Decimals(): String = formatDecimals(2)

private fun Int.toHex2(): String = (this and 0xFF).toString(16).padStart(2, '0').uppercase()
private fun Byte.toHex2(): String = (this.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()

/**
 * Diagnostic analysis report containing metadata, compression statistics, and chunk breakdown for a QOI image.
 */
data class QoiDiagnosticReport(
    val header: QoiHeader,
    val totalPixels: Int,
    val encodedBytes: Int,
    val uncompressedBytes: Int,
    val compressionRatio: Double,
    val spaceSavingsPercentage: Double,
    val chunkCounts: Map<String, Int>,
    val pixels: List<Color>
) {
    /**
     * Formats a diagnostic summary table.
     */
    fun formatSummary(): String {
        return buildString {
            appendLine("=== QOI Image Diagnostics ===")
            appendLine("Dimensions         : ${header.width} x ${header.height} pixels ($totalPixels total)")
            appendLine("Channels           : ${header.channels} (${if (header.channels == 3u.toUByte()) "RGB" else "RGBA"})")
            appendLine("Colorspace         : ${header.colorspace} (${if (header.colorspace == 0u.toUByte()) "sRGB (linear alpha)" else "all channels linear"})")
            appendLine("Encoded File Size  : $encodedBytes bytes")
            appendLine("Uncompressed Size  : $uncompressedBytes bytes")
            appendLine("Compression Ratio  : ${compressionRatio.format2Decimals()}:1")
            appendLine("Space Savings      : ${spaceSavingsPercentage.format2Decimals()}%")
            appendLine("--- Chunk Breakdown ---")
            val totalChunks = chunkCounts.values.sum()
            for ((chunkType, count) in chunkCounts) {
                val percentage = if (totalChunks > 0) (count.toDouble() / totalChunks) * 100.0 else 0.0
                appendLine("${chunkType.padEnd(18)} : ${count.toString().padStart(6)} chunks (${percentage.format2Decimals().padStart(5)}%)")
            }
        }
    }

    /**
     * Dumps the pixel grid up to [limit] pixels in the requested [format] ("text" or "json").
     */
    fun dumpPixels(limit: Int = 100, format: String = "text"): String {
        val displayedPixels = pixels.take(limit)
        return when (format.lowercase()) {
            "json" -> buildString {
                appendLine("[")
                displayedPixels.forEachIndexed { index, pixel ->
                    val x = index % header.width.toInt()
                    val y = index / header.width.toInt()
                    val comma = if (index < displayedPixels.size - 1) "," else ""
                    val hex = "#${pixel.r.toHex2()}${pixel.g.toHex2()}${pixel.b.toHex2()}${pixel.a.toHex2()}"
                    appendLine("  {\"index\": $index, \"x\": $x, \"y\": $y, \"r\": ${pixel.r}, \"g\": ${pixel.g}, \"b\": ${pixel.b}, \"a\": ${pixel.a}, \"hex\": \"$hex\"}$comma")
                }
                appendLine("]")
            }
            else -> buildString {
                appendLine("Index |   X  |   Y  | Hex Color |   R  |   G  |   B  |   A  ")
                appendLine("------+------+------+-----------+------+------+------+------")
                displayedPixels.forEachIndexed { index, pixel ->
                    val x = index % header.width.toInt()
                    val y = index / header.width.toInt()
                    val hex = "#${pixel.r.toHex2()}${pixel.g.toHex2()}${pixel.b.toHex2()}${pixel.a.toHex2()}"
                    val idxStr = index.toString().padStart(5)
                    val xStr = x.toString().padStart(4)
                    val yStr = y.toString().padStart(4)
                    val rStr = pixel.r.toString().padStart(4)
                    val gStr = pixel.g.toString().padStart(4)
                    val bStr = pixel.b.toString().padStart(4)
                    val aStr = pixel.a.toString().padStart(4)
                    appendLine("$idxStr | $xStr | $yStr | $hex | $rStr | $gStr | $bStr | $aStr")
                }
                if (pixels.size > limit) {
                    appendLine("... and ${pixels.size - limit} more pixels.")
                }
            }
        }
    }
}

object QoiDiagnostic {
    /**
     * Performs a comprehensive diagnostic scan on raw QOI binary [bytes].
     *
     * @param bytes Raw QOI byte stream.
     * @return [QoiDiagnosticReport] containing metadata, chunk counts, and decoded pixels.
     */
    fun analyze(bytes: ByteArray): QoiDiagnosticReport {
        QoiImage.byteArrayIsPlausible(bytes)

        var offset = 0
        val header = QoiHeader.fromBytes(bytes, offset)
        offset += QoiHeader.CHUNK_SIZE

        val totalPixels = header.width.toInt() * header.height.toInt()
        val pixels = ArrayList<Color>(totalPixels)
        val seenPixels: Array<Color> = Array(64) { Color(0, 0, 0, 0) }
        var prevPixel = Color(0, 0, 0, 255)

        val counts = mutableMapOf(
            "QOI_OP_INDEX" to 0,
            "QOI_OP_DIFF" to 0,
            "QOI_OP_LUMA" to 0,
            "QOI_OP_RUN" to 0,
            "QOI_OP_RGB" to 0,
            "QOI_OP_RGBA" to 0
        )

        while (pixels.size < totalPixels && offset <= bytes.size - QoiImage.TERMINATOR.size) {
            if (QoiOpRgb.matchTag(bytes, offset)) {
                val op = QoiOpRgb.fromBytes(bytes, offset)
                offset += QoiOpRgb.CHUNK_SIZE
                val pixel = op.toColor(prevPixel)
                pixels.add(pixel)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                counts["QOI_OP_RGB"] = (counts["QOI_OP_RGB"] ?: 0) + 1
            } else if (QoiOpRgba.matchTag(bytes, offset)) {
                val op = QoiOpRgba.fromBytes(bytes, offset)
                offset += QoiOpRgba.CHUNK_SIZE
                val pixel = op.toColor()
                pixels.add(pixel)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                counts["QOI_OP_RGBA"] = (counts["QOI_OP_RGBA"] ?: 0) + 1
            } else if (QoiOpIndex.matchTag(bytes, offset)) {
                val op = QoiOpIndex.fromBytes(bytes, offset)
                offset += QoiOpIndex.CHUNK_SIZE
                val pixel = seenPixels[op.index.toInt()]
                pixels.add(pixel)
                prevPixel = pixel
                counts["QOI_OP_INDEX"] = (counts["QOI_OP_INDEX"] ?: 0) + 1
            } else if (QoiOpDiff.matchTag(bytes, offset)) {
                val op = QoiOpDiff.fromBytes(bytes, offset)
                offset += QoiOpDiff.CHUNK_SIZE
                val pixel = op.toColor(prevPixel)
                pixels.add(pixel)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                counts["QOI_OP_DIFF"] = (counts["QOI_OP_DIFF"] ?: 0) + 1
            } else if (QoiOpLuma.matchTag(bytes, offset)) {
                val op = QoiOpLuma.fromBytes(bytes, offset)
                offset += QoiOpLuma.CHUNK_SIZE
                val pixel = op.toColor(prevPixel)
                pixels.add(pixel)
                seenPixels[pixel.toHash()] = pixel
                prevPixel = pixel
                counts["QOI_OP_LUMA"] = (counts["QOI_OP_LUMA"] ?: 0) + 1
            } else if (QoiOpRun.matchTag(bytes, offset)) {
                val op = QoiOpRun.fromBytes(bytes, offset)
                offset += QoiOpRun.CHUNK_SIZE
                repeat(op.run.toInt()) {
                    pixels.add(prevPixel)
                }
                counts["QOI_OP_RUN"] = (counts["QOI_OP_RUN"] ?: 0) + 1
            } else {
                throw IllegalArgumentException("Unknown QOI chunk tag at offset $offset (byte: 0x${bytes[offset].toHex2()}).")
            }
        }

        val uncompressed = totalPixels * header.channels.toInt()
        val ratio = if (bytes.size > 0) uncompressed.toDouble() / bytes.size.toDouble() else 1.0
        val savings = if (uncompressed > 0) ((uncompressed - bytes.size).toDouble() / uncompressed.toDouble()) * 100.0 else 0.0

        return QoiDiagnosticReport(
            header = header,
            totalPixels = totalPixels,
            encodedBytes = bytes.size,
            uncompressedBytes = uncompressed,
            compressionRatio = ratio,
            spaceSavingsPercentage = savings,
            chunkCounts = counts,
            pixels = pixels
        )
    }
}
