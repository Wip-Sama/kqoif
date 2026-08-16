package org.wip.kqoif

import java.util.Locale
import java.util.logging.Logger

private val logger = Logger.getLogger("Main")

/**
 * QOI_OP_RGB   bytes[4], b11111110 reg green blue
 * QOI_OP_RGBA  bytes[5], b11111111 reg green blue alpha
 * QOI_OP_INDEX bytes[1], b00 6-bit_index !shall not provide more than 1 consecutive index, use QOI_OP_RUN
 * - index_position = (r * 3 + g * 5 + b * 7 + a * 11) % 64
 * QOI_OP_DIFF  bytes[1], b01 dr dg db
 * - dr = red channel difference from previous pixel -2..1
 * - dg = green channel difference from previous pixel -2..1
 * - db = blue channel difference from previous pixel -2..1
 * - wraparound operation for 1-2 will result in 255, while 255+1 will result in 0
 * - values are stored with a bias of 2 so -2 (b10) will result in -2+2 = 0 (b00); 1 (b01) will result in 1+2=3 (b11), alpha unchanced
 * QOI_OP_LUMA  bytes[2], b10 dg6 dr_dg db_dg
 * - dg6 = green channel difference from previous pixel -32..31
 * - dr_dg = (cur_px.r - prev_px.r) - (cur_px.g - prev_px.g) red channel difference minus green channel difference -8..7
 * - db_dg = (cur_px.b - prev_px.b) - (cur_px.g - prev_px.g) blue channel difference minus green channel difference -8..7
 * QOI_OP_RUN   bytes[1] b11 index
 * - the run-length is stored with a bias of -1
 * - 63 (b11111110) and 64 (b11111111) are illegal
 * */

fun main() {
    val qoiHeader = QoiHeader(
        width = 800u,
        height = 600u,
        channels = QoiHeader.CHANNELS_RGBA,
        colorspace = QoiHeader.COLORSPACE_SRGB
    )

    val headerBytes = qoiHeader.toBytes()
    val hexBytes = headerBytes.joinToString(", ") { byte -> String.format(Locale.ROOT, "0x%02X", byte) }
    logger.info("Generated QOI header bytes: size=${headerBytes.size}, content=[$hexBytes]")

    val parsedHeader = QoiHeader.fromBytes(headerBytes)
    logger.info("Parsed back QOI header: $parsedHeader, isValid=${parsedHeader.isValid()}")

    // Demo QOI_OP_INDEX
    val opIndex = QoiOpIndex(index = 42u)
    val opIndexBytes = opIndex.toBytes()
    logger.info("Created QOI_OP_INDEX: $opIndex (bytes=${opIndexBytes.joinToString { "0x%02X".format(it) }})")
}
