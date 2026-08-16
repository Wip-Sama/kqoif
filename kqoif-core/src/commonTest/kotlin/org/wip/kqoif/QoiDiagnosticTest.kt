package org.wip.kqoif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QoiDiagnosticTest {

    @Test
    fun testAnalyzeAndFormatSummary() {
        val header = QoiHeader(width = 4u, height = 1u, channels = 4u, colorspace = 0u)
        val pixels = listOf(
            Color(10, 20, 30, 255),
            Color(10, 20, 30, 255),
            Color(11, 20, 30, 255),
            Color(200, 100, 50, 128)
        )
        val image = QoiImage(header, pixels)
        val report = image.analyze()

        assertEquals(4, report.totalPixels)
        assertEquals(16, report.uncompressedBytes) // 4 pixels * 4 channels
        assertTrue(report.chunkCounts["QOI_OP_RUN"] ?: 0 >= 1 || report.chunkCounts["QOI_OP_DIFF"] ?: 0 >= 1)

        val summary = report.formatSummary()
        assertTrue(summary.contains("4 x 1 pixels"))
        assertTrue(summary.contains("Chunk Breakdown"))

        val textDump = report.dumpPixels(limit = 2, format = "text")
        assertTrue(textDump.contains("Index |"))

        val jsonDump = report.dumpPixels(limit = 2, format = "json")
        assertTrue(jsonDump.startsWith("["))
        assertTrue(jsonDump.contains("\"hex\":"))
    }
}
