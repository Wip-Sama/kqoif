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

class BenchmarkCommandTest {

    @Test
    fun testConvertWithDifferentStrategiesAndRolling() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val pngFile = File(tempDir, "test.png")
            val qoiDirect = File(tempDir, "test_direct.qoi")
            val qoiObject = File(tempDir, "test_object.qoi")
            val qoiRolling = File(tempDir, "test_rolling.qoi")

            val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(16) { Color(it * 15, it * 15, it * 15, 255) }
            val original = QoiImage(header, pixels)
            QoiImageIO.write(original, pngFile)

            // 1. Convert with DIRECT
            val cliDirect = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliDirect.parse(listOf("convert", pngFile.absolutePath, qoiDirect.absolutePath, "-e", "direct"))
            assertTrue(qoiDirect.exists())

            // 2. Convert with OBJECT
            val cliObject = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliObject.parse(listOf("convert", pngFile.absolutePath, qoiObject.absolutePath, "-e", "object"))
            assertTrue(qoiObject.exists())

            // 3. Convert with ROLLING
            val cliRolling = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliRolling.parse(listOf("convert", pngFile.absolutePath, qoiRolling.absolutePath, "-r", "-e", "direct"))
            assertTrue(qoiRolling.exists())

            val directBytes = qoiDirect.readBytes()
            val objectBytes = qoiObject.readBytes()
            val rollingBytes = qoiRolling.readBytes()

            assertTrue(directBytes.contentEquals(objectBytes))
            assertTrue(directBytes.contentEquals(rollingBytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBenchmarkCommandRunsSuccessfully() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench2_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val qoiFile = File(tempDir, "sample.qoi")
            val header = QoiHeader(width = 8u, height = 8u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(64) { Color(it * 3, it * 4, it * 5, 255) }
            val original = QoiImage(header, pixels)
            QoiImageIO.write(original, qoiFile)

            // Test Benchmark Text
            val benchText = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchText.parse(listOf("benchmark", qoiFile.absolutePath, "-i", "3", "-w", "1", "-f", "text"))

            // Test Benchmark JSON
            val benchJson = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchJson.parse(listOf("benchmark", qoiFile.absolutePath, "-i", "3", "-w", "1", "-f", "json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
