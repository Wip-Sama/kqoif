package org.wip.kqoif

import java.util.Locale
import java.util.logging.Logger

private val logger = Logger.getLogger("Main")


/**
 * QOI_OP_LUMA  bytes[2], b10 dg6 dr_dg db_dg
 * - dg6 = green channel difference from previous pixel -32..31
 * - dr_dg = (cur_px.r - prev_px.r) - (cur_px.g - prev_px.g) red channel difference minus green channel difference -8..7
 * - db_dg = (cur_px.b - prev_px.b) - (cur_px.g - prev_px.g) blue channel difference minus green channel difference -8..7
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

    // Demo QOI_OP_DIFF (bias of 2)
    val opDiff = QoiOpDiff(dr = -1, dg = 0, db = 1)
    val opDiffBytes = opDiff.toBytes()
    logger.info("Created QOI_OP_DIFF: $opDiff (bytes=${opDiffBytes.joinToString { "0x%02X".format(it) }})")

    // Demo QOI_OP_RUN (bias of -1)
    val opRun = QoiOpRun(run = 15u)
    val opRunBytes = opRun.toBytes()
    logger.info("Created QOI_OP_RUN: $opRun (bytes=${opRunBytes.joinToString { "0x%02X".format(it) }})")
}
