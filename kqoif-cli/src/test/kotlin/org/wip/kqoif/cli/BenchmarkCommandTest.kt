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
            benchText.parse(listOf("benchmark", qoiFile.absolutePath, "-i", "2", "-w", "1", "-f", "text"))

            // Test Benchmark JSON
            val benchJson = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchJson.parse(listOf("benchmark", qoiFile.absolutePath, "-i", "2", "-w", "1", "-f", "json"))

            // Test Benchmark CSV
            val benchCsv = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchCsv.parse(listOf("benchmark", qoiFile.absolutePath, "-i", "2", "-w", "1", "-f", "csv"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBenchmarkExportReports() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench_report_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val pngFile = File(tempDir, "sample.png")
            val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(16) { Color(it * 10, it * 10, it * 10, 255) }
            val original = QoiImage(header, pixels)
            QoiImageIO.write(original, pngFile)

            val jsonReport = File(tempDir, "report.json")
            val csvReport = File(tempDir, "report.csv")

            // 1. Export JSON report
            val benchJson = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchJson.parse(listOf("benchmark", pngFile.absolutePath, "-i", "2", "-w", "1", "-o", jsonReport.absolutePath))
            assertTrue(jsonReport.exists())
            val jsonContent = jsonReport.readText()
            assertTrue(jsonContent.contains("\"summary\""))
            assertTrue(jsonContent.contains("\"compressionRatio\""))
            assertTrue(jsonContent.contains("\"spaceSavingsPct\""))
            assertTrue(jsonContent.contains("\"verifiedIdentical\": true"))
            assertTrue(jsonContent.contains("In-Memory Direct"))

            // 2. Export CSV report
            val benchCsv = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            benchCsv.parse(listOf("benchmark", pngFile.absolutePath, "-i", "2", "-w", "1", "-o", csvReport.absolutePath))
            assertTrue(csvReport.exists())
            val csvContent = csvReport.readText()
            assertTrue(csvContent.startsWith("Image,Path,Width,Height,Channels,Pixels,RawBytes"))
            assertTrue(csvContent.contains("sample.png"))
            assertTrue(csvContent.contains("In-Memory Direct"))
            assertTrue(csvContent.contains("Rolling Direct"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBenchmarkDirectoryBatchAndRecursive() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench_dir_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val subDir = File(tempDir, "nested")
            subDir.mkdirs()

            val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(16) { Color(it * 5, it * 10, it * 15, 255) }
            val original = QoiImage(header, pixels)

            val img1 = File(tempDir, "img1.png")
            val img2 = File(tempDir, "img2.qoi")
            val imgNested = File(subDir, "img3.png")

            QoiImageIO.write(original, img1)
            QoiImageIO.write(original, img2)
            QoiImageIO.write(original, imgNested)

            val jsonReportNonRecursive = File(tempDir, "non_rec.json")
            val jsonReportRecursive = File(tempDir, "rec.json")

            // 1. Benchmark folder non-recursive (should find 2 images in root)
            val cliNonRec = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliNonRec.parse(listOf("benchmark", tempDir.absolutePath, "-i", "1", "-w", "0", "-o", jsonReportNonRecursive.absolutePath))
            assertTrue(jsonReportNonRecursive.exists())
            val nonRecContent = jsonReportNonRecursive.readText()
            assertTrue(nonRecContent.contains("\"totalImages\": 2"))

            // 2. Benchmark folder recursive (should find 3 images total)
            val cliRec = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliRec.parse(listOf("benchmark", tempDir.absolutePath, "-r", "-i", "1", "-w", "0", "-o", jsonReportRecursive.absolutePath))
            assertTrue(jsonReportRecursive.exists())
            val recContent = jsonReportRecursive.readText()
            assertTrue(recContent.contains("\"totalImages\": 3"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBenchmarkSaveImagesInPlaceAndCustomDir() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench_save_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val customOutDir = File(tempDir, "saved_qoi_output")
            val pngFile = File(tempDir, "source_photo.png")

            val header = QoiHeader(width = 4u, height = 4u, channels = QoiHeader.CHANNELS_RGBA, colorspace = 0u)
            val pixels = List(16) { Color(it * 12, it * 8, it * 4, 255) }
            val original = QoiImage(header, pixels)
            QoiImageIO.write(original, pngFile)

            // 1. Save images to custom dir (--save-dir)
            val cliCustom = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliCustom.parse(listOf("benchmark", pngFile.absolutePath, "--save-dir", customOutDir.absolutePath, "-i", "1", "-w", "0"))
            val savedCustomFile = File(customOutDir, "source_photo.qoi")
            assertTrue(savedCustomFile.exists())
            val decodedCustom = QoiImageIO.read(savedCustomFile)
            assertEquals(original.pixels, decodedCustom.pixels)

            // 2. Save images in place (--save-images)
            val cliInPlace = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            cliInPlace.parse(listOf("benchmark", pngFile.absolutePath, "--save-images", "-i", "1", "-w", "0"))
            val savedInPlaceFile = File(tempDir, "source_photo.qoi")
            assertTrue(savedInPlaceFile.exists())
            val decodedInPlace = QoiImageIO.read(savedInPlaceFile)
            assertEquals(original.pixels, decodedInPlace.pixels)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBenchmarkEmptyDirectoryHandling() {
        val emptyDir = File(System.getProperty("java.io.tmpdir"), "kqoif_cli_bench_empty_${System.currentTimeMillis()}")
        emptyDir.mkdirs()

        try {
            val cli = KqoifCommand().subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
            // Should complete gracefully without crashing
            cli.parse(listOf("benchmark", emptyDir.absolutePath))
        } finally {
            emptyDir.deleteRecursively()
        }
    }
}
