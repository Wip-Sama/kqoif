package org.wip.kqoif.cli

import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.wip.kqoif.Color
import org.wip.kqoif.QoiHeader
import org.wip.kqoif.QoiImage
import org.wip.kqoif.imageio.QoiImageIO
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTest {

    @Test
    fun testConvertAndDumpCommands() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. Create test PNG file
            val pngFile = File(tempDir, "test.png")
            val qoiFile = File(tempDir, "test.qoi")
            val roundtripPng = File(tempDir, "roundtrip.png")

            val header = QoiHeader(width = 3u, height = 3u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(9) { Color(it * 20, it * 20, it * 20, 255) }
            val original = QoiImage(header, pixels)
            QoiImageIO.write(original, pngFile)
            assertTrue(pngFile.exists())

            // 2. Test Convert Command (PNG -> QOI)
            val convertToQoi = KqoifCommand().subcommands(ConvertCommand(), DumpCommand())
            convertToQoi.parse(listOf("convert", pngFile.absolutePath, qoiFile.absolutePath, "--stats"))
            assertTrue(qoiFile.exists())

            // 3. Test Convert Command (QOI -> PNG)
            val convertToPng = KqoifCommand().subcommands(ConvertCommand(), DumpCommand())
            convertToPng.parse(listOf("convert", qoiFile.absolutePath, roundtripPng.absolutePath))
            assertTrue(roundtripPng.exists())

            val reloaded = QoiImageIO.read(roundtripPng)
            assertEquals(original.pixels, reloaded.pixels)

            // 4. Test Dump Command (Text & JSON)
            val dumpText = KqoifCommand().subcommands(ConvertCommand(), DumpCommand())
            dumpText.parse(listOf("dump", qoiFile.absolutePath, "--pixels", "--limit", "5"))

            val dumpJson = KqoifCommand().subcommands(ConvertCommand(), DumpCommand())
            dumpJson.parse(listOf("dump", qoiFile.absolutePath, "--pixels", "--format", "json", "--limit", "5"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
