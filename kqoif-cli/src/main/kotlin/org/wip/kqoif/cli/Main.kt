package org.wip.kqoif.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import org.wip.kqoif.QoiDiagnostic
import org.wip.kqoif.QoiEncoderStrategy
import org.wip.kqoif.QoiImage
import org.wip.kqoif.formatDecimals
import org.wip.kqoif.imageio.QoiImageIO
import org.wip.kqoif.imageio.toBufferedImage
import org.wip.kqoif.imageio.toQoiImage
import org.wip.kqoif.imageio.toQoiRollingBytes
import java.io.File
import java.lang.management.ManagementFactory
import javax.imageio.ImageIO

/**
 * Root CLI command for kqoif.
 */
class KqoifCommand : CliktCommand(name = "kqoif") {
    override fun help(context: Context) = "Fast, lightweight QOI (Quite OK Image) format encoder, decoder, and inspection tool."
    override fun run() = Unit
}

/**
 * Subcommand to convert between QOI and other standard image formats (PNG, JPG, BMP, WEBP, TIFF).
 */
class ConvertCommand : CliktCommand(name = "convert") {
    override fun help(context: Context) = "Convert an image between QOI and standard formats (PNG, JPG, BMP, WEBP, TIFF)."

    private val input: File by argument(
        name = "INPUT",
        help = "Path to the input image file (.qoi, .png, .jpg, .bmp, .webp, .tiff)"
    ).file(mustExist = true, canBeDir = false)

    private val output: File by argument(
        name = "OUTPUT",
        help = "Path to the output image file (.qoi, .png, .jpg, .bmp, .webp, .tiff)"
    ).file(canBeDir = false)

    private val encoder: QoiEncoderStrategy by option(
        "-e", "--encoder",
        help = "Encoding strategy: DIRECT (transitive zero-allocation) or OBJECT (AST objects)"
    ).enum<QoiEncoderStrategy>().default(QoiEncoderStrategy.DIRECT)

    private val rolling: Boolean by option(
        "-r", "--rolling",
        help = "Enable rolling scanline streaming to minimize memory consumption"
    ).flag(default = false)

    private val showStats: Boolean by option(
        "-s", "--stats",
        help = "Display diagnostic and compression statistics after conversion"
    ).flag(default = false)

    override fun run() {
        val modeDesc = if (rolling) "rolling ($encoder)" else "in-memory ($encoder)"
        echo("Converting '${input.name}' -> '${output.name}' using $modeDesc...")
        val startTime = System.currentTimeMillis()

        if (rolling) {
            QoiImageIO.convertRolling(input, output, encoder)
        } else {
            val image = QoiImageIO.read(input)
            QoiImageIO.write(image, output, encoder)
        }

        val duration = System.currentTimeMillis() - startTime
        echo("Conversion successful in ${duration}ms (${output.length()} bytes written).")

        if (showStats) {
            val qoiBytes = if (output.extension.lowercase() == "qoi") {
                output.readBytes()
            } else {
                val image = QoiImageIO.read(input)
                image.encode(encoder)
            }
            val report = QoiDiagnostic.analyze(qoiBytes)
            echo("")
            echo(report.formatSummary())
        }
    }
}

enum class DumpFormat { TEXT, JSON }
enum class BenchmarkFormat { TEXT, JSON, CSV }

private val SUPPORTED_IMAGE_EXTENSIONS = setOf("qoi", "png", "jpg", "jpeg", "bmp", "webp", "tiff", "tif")

/**
 * Subcommand to inspect and dump QOI file metadata, compression statistics, and pixel grid.
 */
class DumpCommand : CliktCommand(name = "dump") {
    override fun help(context: Context) = "Inspect QOI image metadata, chunk breakdown, and dump pixel colors."

    private val input: File by argument(
        name = "INPUT",
        help = "Path to the .qoi file to analyze"
    ).file(mustExist = true, canBeDir = false)

    private val showPixels: Boolean by option(
        "-p", "--pixels",
        help = "Output detailed pixel color table"
    ).flag(default = false)

    private val limit: Int by option(
        "-l", "--limit",
        help = "Maximum number of pixels to display (default: 100)"
    ).int().default(100)

    private val format: DumpFormat by option(
        "-f", "--format",
        help = "Output format for pixel dump (TEXT or JSON, default: TEXT)"
    ).enum<DumpFormat>().default(DumpFormat.TEXT)

    override fun run() {
        val qoiBytes = input.readBytes()
        val report = QoiDiagnostic.analyze(qoiBytes)

        if (format == DumpFormat.TEXT) {
            echo(report.formatSummary())
        }

        if (showPixels) {
            echo("")
            echo(report.dumpPixels(limit = limit, format = format.name.lowercase()))
        }
    }
}

/**
 * Result data holder for benchmark mode runs including latency, throughput, and memory footprint.
 */
data class BenchmarkModeResult(
    val name: String,
    val iterations: Int,
    val avgMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val megapixelsPerSec: Double,
    val throughputMBps: Double,
    val speedup: Double,
    val outputBytes: Int,
    val encodeAllocatedBytes: Long,
    val totalAllocatedBytes: Long,
    val peakResidentBytes: Long,
    val bytesPerPixel: Double,
    val memorySavedPct: Double
)

/**
 * Result data holder for a single image benchmark run including raw & compressed sizes, ratio, and mode results.
 */
data class BenchmarkImageResult(
    val file: File,
    val width: Int,
    val height: Int,
    val channels: Int,
    val totalPixels: Long,
    val rawBytes: Long,
    val inputBytes: Long,
    val outputBytes: Int,
    val compressionRatio: Double,
    val spaceSavingsPct: Double,
    val ingestionAllocatedBytes: Long,
    val verifiedIdentical: Boolean,
    val modeResults: List<BenchmarkModeResult>
)

/**
 * Suite result aggregating multiple image benchmarks.
 */
data class BenchmarkSuiteResult(
    val totalImages: Int,
    val totalPixels: Long,
    val totalRawBytes: Long,
    val totalQoiBytes: Long,
    val overallCompressionRatio: Double,
    val overallSpaceSavingsPct: Double,
    val imageResults: List<BenchmarkImageResult>
)

/**
 * Subcommand to benchmark and compare all QOI encoding strategies (In-Memory Direct, In-Memory Object,
 * Rolling Direct, Rolling Object) and verify 100% byte-for-byte fidelity and memory footprint.
 */
class BenchmarkCommand : CliktCommand(name = "benchmark") {
    override fun help(context: Context) = "Benchmark and compare In-Memory vs Rolling Direct and Object encoder strategies on files or directories."

    private val input: File by argument(
        name = "INPUT",
        help = "Path to an image file or directory (.qoi, .png, .jpg, .bmp, .webp, .tiff) to benchmark"
    ).file(mustExist = true, canBeDir = true)

    private val iterations: Int by option(
        "-i", "--iterations",
        help = "Number of measurement iterations per strategy (default: 10)"
    ).int().default(10)

    private val warmup: Int by option(
        "-w", "--warmup",
        help = "Number of warmup iterations (default: 3)"
    ).int().default(3)

    private val format: BenchmarkFormat by option(
        "-f", "--format",
        help = "Output format (TEXT, JSON, or CSV, default: TEXT)"
    ).enum<BenchmarkFormat>().default(BenchmarkFormat.TEXT)

    private val report: File? by option(
        "-o", "--report", "--output-report",
        help = "Path to export the benchmark report file (.json, .csv, or .txt)"
    ).file(canBeDir = false)

    private val saveImages: Boolean by option(
        "--save-images",
        help = "Save encoded .qoi images in the same directory as input files"
    ).flag(default = false)

    private val saveDir: File? by option(
        "--save-dir",
        help = "Target directory to save encoded .qoi images"
    ).file(canBeFile = false)

    private val recursive: Boolean by option(
        "-r", "--recursive",
        help = "Recursively scan subdirectories when INPUT is a directory"
    ).flag(default = false)

    override fun run() {
        val targetFiles = resolveInputFiles(input, recursive)
        if (targetFiles.isEmpty()) {
            echo("No supported image files found in: ${input.absolutePath}")
            return
        }

        if (saveDir != null && !saveDir!!.exists()) {
            saveDir!!.mkdirs()
        }

        val imageResults = mutableListOf<BenchmarkImageResult>()

        for (file in targetFiles) {
            val result = benchmarkSingleImage(file, saveDir, saveImages)
            imageResults.add(result)
        }

        val totalPixels = imageResults.sumOf { it.totalPixels }
        val totalRawBytes = imageResults.sumOf { it.rawBytes }
        val totalQoiBytes = imageResults.sumOf { it.outputBytes.toLong() }
        val overallRatio = if (totalQoiBytes > 0L) totalRawBytes.toDouble() / totalQoiBytes.toDouble() else 1.0
        val overallSavings = if (totalRawBytes > 0L) ((totalRawBytes - totalQoiBytes).toDouble() / totalRawBytes.toDouble()) * 100.0 else 0.0

        val suite = BenchmarkSuiteResult(
            totalImages = imageResults.size,
            totalPixels = totalPixels,
            totalRawBytes = totalRawBytes,
            totalQoiBytes = totalQoiBytes,
            overallCompressionRatio = overallRatio,
            overallSpaceSavingsPct = overallSavings,
            imageResults = imageResults
        )

        // Output to console
        when (format) {
            BenchmarkFormat.JSON -> echo(formatJsonSuite(suite))
            BenchmarkFormat.CSV -> echo(formatCsvSuite(suite))
            BenchmarkFormat.TEXT -> echo(formatTextSuite(suite))
        }

        // Export report file if requested
        if (report != null) {
            val reportParent = report!!.parentFile
            if (reportParent != null && !reportParent.exists()) {
                reportParent.mkdirs()
            }
            val reportContent = when {
                report!!.extension.lowercase() == "json" -> formatJsonSuite(suite)
                report!!.extension.lowercase() == "csv" -> formatCsvSuite(suite)
                format == BenchmarkFormat.JSON -> formatJsonSuite(suite)
                format == BenchmarkFormat.CSV -> formatCsvSuite(suite)
                else -> formatTextSuite(suite)
            }
            report!!.writeText(reportContent)
            echo("Benchmark report saved to: ${report!!.absolutePath}")
        }
    }

    private fun resolveInputFiles(input: File, recursive: Boolean): List<File> {
        return if (input.isFile) {
            val ext = input.extension.lowercase()
            if (ext in SUPPORTED_IMAGE_EXTENSIONS) {
                listOf(input)
            } else {
                throw IllegalArgumentException("Unsupported image format '${input.extension}'. Supported formats: ${SUPPORTED_IMAGE_EXTENSIONS.joinToString()}")
            }
        } else if (input.isDirectory) {
            if (recursive) {
                input.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS }
                    .sortedBy { it.path }
                    .toList()
            } else {
                input.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS }
                    ?.sortedBy { it.path }
                    ?: emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun benchmarkSingleImage(file: File, explicitSaveDir: File?, saveInPlace: Boolean): BenchmarkImageResult {
        val inExt = file.extension.lowercase()
        val qoiImage = if (inExt == "qoi") {
            QoiImageIO.read(file)
        } else {
            val buffered = ImageIO.read(file) ?: throw IllegalArgumentException("Failed to read image: ${file.name}")
            buffered.toQoiImage()
        }

        val bufferedImage = qoiImage.toBufferedImage()
        val totalPixels = qoiImage.width.toLong() * qoiImage.height.toLong()

        // Measure in-memory List<Color> pixel ingestion memory
        val startIngest = getThreadAllocatedBytes()
        val _sampleQoi = bufferedImage.toQoiImage()
        val endIngest = getThreadAllocatedBytes()
        val ingestionAllocatedBytes = if (startIngest >= 0L && endIngest >= startIngest) {
            endIngest - startIngest
        } else {
            totalPixels * 28L // 24B per Color + 4B pointer in ArrayList
        }

        // 1. Verify byte-for-byte identical output across all modes
        val bytesMemDirect = qoiImage.encode(QoiEncoderStrategy.DIRECT)
        val bytesMemObject = qoiImage.encode(QoiEncoderStrategy.OBJECT)
        val bytesRollDirect = bufferedImage.toQoiRollingBytes(QoiEncoderStrategy.DIRECT)
        val bytesRollObject = bufferedImage.toQoiRollingBytes(QoiEncoderStrategy.OBJECT)

        val verified = bytesMemDirect.contentEquals(bytesMemObject) &&
            bytesMemDirect.contentEquals(bytesRollDirect) &&
            bytesMemDirect.contentEquals(bytesRollObject)

        require(verified) {
            "Validation Failure for '${file.name}': In-Memory and Rolling encoder strategies produced different bytes!"
        }

        // Save image if requested
        if (explicitSaveDir != null) {
            val outFile = File(explicitSaveDir, "${file.nameWithoutExtension}.qoi")
            outFile.writeBytes(bytesMemDirect)
        } else if (saveInPlace) {
            val targetDir = file.parentFile ?: File(".")
            val outFile = File(targetDir, "${file.nameWithoutExtension}.qoi")
            outFile.writeBytes(bytesMemDirect)
        }

        val modes = listOf(
            "In-Memory Direct (Transitive)" to { qoiImage.encode(QoiEncoderStrategy.DIRECT) },
            "In-Memory Object (AST Model)" to { qoiImage.encode(QoiEncoderStrategy.OBJECT) },
            "Rolling Direct (Transitive Stream)" to { bufferedImage.toQoiRollingBytes(QoiEncoderStrategy.DIRECT) },
            "Rolling Object (AST Stream)" to { bufferedImage.toQoiRollingBytes(QoiEncoderStrategy.OBJECT) }
        )

        // Warmup runs
        repeat(warmup) {
            for ((_, action) in modes) {
                action()
            }
        }

        // Measured runs
        val modeResults = mutableListOf<BenchmarkModeResult>()
        var baselineAvgMs = 1.0
        var baselineTotalAllocBytes = 1L

        for (modeIndex in modes.indices) {
            val (name, action) = modes[modeIndex]
            val times = ArrayList<Double>(iterations)
            val memoryAllocations = ArrayList<Long>(iterations)

            repeat(iterations) {
                val startAlloc = getThreadAllocatedBytes()
                val start = System.nanoTime()
                val bytes = action()
                val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
                val endAlloc = getThreadAllocatedBytes()

                times.add(elapsedMs)
                if (startAlloc >= 0L && endAlloc >= startAlloc) {
                    memoryAllocations.add(endAlloc - startAlloc)
                }
            }

            val avgMs = times.average()
            val minMs = times.minOrNull() ?: avgMs
            val maxMs = times.maxOrNull() ?: avgMs
            val mps = (totalPixels / 1_000_000.0) / (avgMs / 1000.0)
            val mbps = (bytesMemDirect.size / (1024.0 * 1024.0)) / (avgMs / 1000.0)
            val avgEncodeAllocated = if (memoryAllocations.isNotEmpty()) memoryAllocations.average().toLong() else 0L

            val isInMemory = modeIndex < 2
            val totalAllocated = if (isInMemory) avgEncodeAllocated + ingestionAllocatedBytes else avgEncodeAllocated
            val bpp = if (totalPixels > 0) totalAllocated.toDouble() / totalPixels else 0.0

            // Estimated peak resident RAM working set
            val peakResident = when (modeIndex) {
                0 -> ingestionAllocatedBytes + (14L + totalPixels * 5L + 8L) + bytesMemDirect.size // In-Memory Direct: Image + WorstCaseBuffer + Output
                1 -> ingestionAllocatedBytes + (totalPixels * 24L) + bytesMemDirect.size           // In-Memory Object: Image + AST Objects + Output
                2 -> (qoiImage.width.toLong() * 4L) + bytesMemDirect.size                           // Rolling Direct: 1 scanline + Output sink
                3 -> (qoiImage.width.toLong() * 4L) + bytesMemDirect.size                           // Rolling Object: 1 scanline + Output sink
                else -> totalAllocated
            }

            if (modeIndex == 1) { // Baseline: In-Memory Object
                baselineAvgMs = avgMs
                baselineTotalAllocBytes = totalAllocated.coerceAtLeast(1L)
            }

            modeResults.add(
                BenchmarkModeResult(
                    name = name,
                    iterations = iterations,
                    avgMs = avgMs,
                    minMs = minMs,
                    maxMs = maxMs,
                    megapixelsPerSec = mps,
                    throughputMBps = mbps,
                    speedup = 1.0,
                    outputBytes = bytesMemDirect.size,
                    encodeAllocatedBytes = avgEncodeAllocated,
                    totalAllocatedBytes = totalAllocated,
                    peakResidentBytes = peakResident,
                    bytesPerPixel = bpp,
                    memorySavedPct = 0.0
                )
            )
        }

        // Recalculate speedups and memory reduction against In-Memory Object baseline
        val finalModeResults = modeResults.map { r ->
            val speedup = if (r.avgMs > 0.0) baselineAvgMs / r.avgMs else 1.0
            val savedPct = if (baselineTotalAllocBytes > 0L) {
                ((baselineTotalAllocBytes - r.totalAllocatedBytes).toDouble() / baselineTotalAllocBytes * 100.0)
            } else 0.0
            r.copy(speedup = speedup, memorySavedPct = savedPct)
        }

        val rawBytes = totalPixels * qoiImage.channels.toLong()
        val outBytes = bytesMemDirect.size
        val ratio = if (outBytes > 0) rawBytes.toDouble() / outBytes.toDouble() else 1.0
        val savings = if (rawBytes > 0L) ((rawBytes - outBytes).toDouble() / rawBytes.toDouble()) * 100.0 else 0.0

        return BenchmarkImageResult(
            file = file,
            width = qoiImage.width.toInt(),
            height = qoiImage.height.toInt(),
            channels = qoiImage.channels.toInt(),
            totalPixels = totalPixels,
            rawBytes = rawBytes,
            inputBytes = file.length(),
            outputBytes = outBytes,
            compressionRatio = ratio,
            spaceSavingsPct = savings,
            ingestionAllocatedBytes = ingestionAllocatedBytes,
            verifiedIdentical = verified,
            modeResults = finalModeResults
        )
    }

    private fun getThreadAllocatedBytes(): Long {
        val threadMXBean = ManagementFactory.getThreadMXBean()
        if (threadMXBean is com.sun.management.ThreadMXBean && threadMXBean.isThreadAllocatedMemorySupported) {
            if (!threadMXBean.isThreadAllocatedMemoryEnabled) {
                try {
                    threadMXBean.isThreadAllocatedMemoryEnabled = true
                } catch (_: Throwable) {}
            }
            return threadMXBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
        }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }

    private fun formatTextSuite(suite: BenchmarkSuiteResult): String {
        return buildString {
            for ((index, img) in suite.imageResults.withIndex()) {
                if (index > 0) appendLine("")
                val imgMemMB = (img.ingestionAllocatedBytes / (1024.0 * 1024.0)).format2Decimals()
                val scanlineKB = ((img.width.toLong() * 4L) / 1024.0).format2Decimals()
                val rawMB = (img.rawBytes / (1024.0 * 1024.0)).format2Decimals()
                val qoiMB = (img.outputBytes / (1024.0 * 1024.0)).format2Decimals()

                appendLine("=== QOI Encoding & Memory Benchmark ===")
                appendLine("Image: ${img.file.name} (${img.width}x${img.height}, ${img.channels} channels, ${img.totalPixels} pixels)")
                appendLine("RAW Size: $rawMB MB | QOI Size: $qoiMB MB (${img.outputBytes} bytes) | Ratio: ${img.compressionRatio.format2Decimals()}:1 (${img.spaceSavingsPct.format2Decimals()}% savings)")
                appendLine("Image In-Memory Footprint (List<Color>): $imgMemMB MB | Scanline Stream Buffer: $scanlineKB KB")
                appendLine("Iterations: $iterations measured, $warmup warmup")
                appendLine("Output Verification: Byte-for-byte exact match verified across all 4 modes")
                appendLine("")
                appendLine("Encoder Mode                       | Avg Time (ms) | Speedup | Encode Alloc (KB) | Total Pipeline (KB) | Peak RAM (KB) | Total Mem Saved")
                appendLine("-----------------------------------+---------------+---------+-------------------+---------------------+---------------+----------------")
                for (r in img.modeResults) {
                    val name = r.name.padEnd(34)
                    val avg = r.avgMs.format2Decimals().padStart(13)
                    val speedup = "${r.speedup.format2Decimals()}x".padStart(7)
                    val encAllocKb = (r.encodeAllocatedBytes / 1024.0).format2Decimals().padStart(17)
                    val totalAllocKb = (r.totalAllocatedBytes / 1024.0).format2Decimals().padStart(19)
                    val peakRamKb = (r.peakResidentBytes / 1024.0).format2Decimals().padStart(13)
                    val memSaved = "${r.memorySavedPct.format2Decimals()}%".padStart(15)
                    appendLine("$name | $avg | $speedup | $encAllocKb | $totalAllocKb | $peakRamKb | $memSaved")
                }
            }

            if (suite.imageResults.size > 1) {
                appendLine("")
                appendLine("=== Directory Benchmark Summary (${suite.totalImages} images processed) ===")
                appendLine("Image Name                     | Dimensions  | RAW Size   | QOI Size   | Ratio    | Direct (ms) | Speedup | Direct Throughput")
                appendLine("-------------------------------+-------------+------------+------------+----------+-------------+---------+------------------")
                for (img in suite.imageResults) {
                    val name = (if (img.file.name.length > 30) img.file.name.take(27) + "..." else img.file.name).padEnd(30)
                    val dims = "${img.width}x${img.height}".padEnd(11)
                    val rawStr = "${(img.rawBytes / (1024.0 * 1024.0)).format2Decimals()} MB".padStart(10)
                    val qoiStr = "${(img.outputBytes / (1024.0 * 1024.0)).format2Decimals()} MB".padStart(10)
                    val ratioStr = "${img.compressionRatio.format2Decimals()}:1".padStart(8)
                    val directResult = img.modeResults.firstOrNull { it.name.startsWith("In-Memory Direct") } ?: img.modeResults.first()
                    val directMs = "${directResult.avgMs.format2Decimals()} ms".padStart(11)
                    val speedupStr = "${directResult.speedup.format2Decimals()}x".padStart(7)
                    val tpStr = "${directResult.throughputMBps.format2Decimals()} MB/s".padStart(17)
                    appendLine("$name | $dims | $rawStr | $qoiStr | $ratioStr | $directMs | $speedupStr | $tpStr")
                }
                appendLine("-------------------------------+-------------+------------+------------+----------+-------------+---------+------------------")
                val totalRawMB = (suite.totalRawBytes / (1024.0 * 1024.0)).format2Decimals()
                val totalQoiMB = (suite.totalQoiBytes / (1024.0 * 1024.0)).format2Decimals()
                appendLine("Total Images: ${suite.totalImages} | Total Pixels: ${suite.totalPixels} | RAW: $totalRawMB MB | QOI: $totalQoiMB MB | Overall Ratio: ${suite.overallCompressionRatio.format2Decimals()}:1 (${suite.overallSpaceSavingsPct.format2Decimals()}% space savings)")
            }
        }
    }

    private fun formatJsonSuite(suite: BenchmarkSuiteResult): String {
        return buildString {
            appendLine("{")
            appendLine("  \"summary\": {")
            appendLine("    \"totalImages\": ${suite.totalImages},")
            appendLine("    \"totalPixels\": ${suite.totalPixels},")
            appendLine("    \"totalRawBytes\": ${suite.totalRawBytes},")
            appendLine("    \"totalQoiBytes\": ${suite.totalQoiBytes},")
            appendLine("    \"overallCompressionRatio\": ${suite.overallCompressionRatio.format2Decimals()},")
            appendLine("    \"overallSpaceSavingsPct\": ${suite.overallSpaceSavingsPct.format2Decimals()}")
            appendLine("  },")
            appendLine("  \"images\": [")
            suite.imageResults.forEachIndexed { imgIdx, img ->
                val imgComma = if (imgIdx < suite.imageResults.size - 1) "," else ""
                appendLine("    {")
                appendLine("      \"image\": \"${escapeJson(img.file.name)}\",")
                appendLine("      \"path\": \"${escapeJson(img.file.absolutePath.replace('\\', '/'))}\",")
                appendLine("      \"width\": ${img.width},")
                appendLine("      \"height\": ${img.height},")
                appendLine("      \"channels\": ${img.channels},")
                appendLine("      \"pixels\": ${img.totalPixels},")
                appendLine("      \"rawBytes\": ${img.rawBytes},")
                appendLine("      \"inputBytes\": ${img.inputBytes},")
                appendLine("      \"outputBytes\": ${img.outputBytes},")
                appendLine("      \"compressionRatio\": ${img.compressionRatio.format2Decimals()},")
                appendLine("      \"spaceSavingsPct\": ${img.spaceSavingsPct.format2Decimals()},")
                appendLine("      \"inMemoryImageFootprintKB\": ${(img.ingestionAllocatedBytes / 1024.0).format2Decimals()},")
                appendLine("      \"scanlineStreamBufferKB\": ${((img.width.toLong() * 4L) / 1024.0).format2Decimals()},")
                appendLine("      \"verifiedIdentical\": ${img.verifiedIdentical},")
                appendLine("      \"results\": [")
                img.modeResults.forEachIndexed { i, r ->
                    val comma = if (i < img.modeResults.size - 1) "," else ""
                    appendLine("        {")
                    appendLine("          \"mode\": \"${escapeJson(r.name)}\",")
                    appendLine("          \"iterations\": ${r.iterations},")
                    appendLine("          \"avgMs\": ${r.avgMs.format2Decimals()},")
                    appendLine("          \"minMs\": ${r.minMs.format2Decimals()},")
                    appendLine("          \"maxMs\": ${r.maxMs.format2Decimals()},")
                    appendLine("          \"megapixelsPerSec\": ${r.megapixelsPerSec.format2Decimals()},")
                    appendLine("          \"throughputMBps\": ${r.throughputMBps.format2Decimals()},")
                    appendLine("          \"speedup\": ${r.speedup.format2Decimals()},")
                    appendLine("          \"encodeAllocatedKB\": ${(r.encodeAllocatedBytes / 1024.0).format2Decimals()},")
                    appendLine("          \"totalPipelineAllocatedKB\": ${(r.totalAllocatedBytes / 1024.0).format2Decimals()},")
                    appendLine("          \"peakResidentKB\": ${(r.peakResidentBytes / 1024.0).format2Decimals()},")
                    appendLine("          \"bytesPerPixel\": ${r.bytesPerPixel.format2Decimals()},")
                    appendLine("          \"memorySavedPct\": ${r.memorySavedPct.format2Decimals()}")
                    appendLine("        }$comma")
                }
                appendLine("      ]")
                appendLine("    }$imgComma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun formatCsvSuite(suite: BenchmarkSuiteResult): String {
        return buildString {
            appendLine("Image,Path,Width,Height,Channels,Pixels,RawBytes,InputBytes,OutputBytes,CompressionRatio,SpaceSavingsPct,Mode,AvgMs,MinMs,MaxMs,MegapixelsPerSec,ThroughputMBps,Speedup,EncodeAllocatedKB,TotalPipelineAllocatedKB,PeakResidentKB,BytesPerPixel,MemorySavedPct,Verified")
            for (img in suite.imageResults) {
                val escapedImgName = escapeCsv(img.file.name)
                val escapedPath = escapeCsv(img.file.absolutePath.replace('\\', '/'))
                for (r in img.modeResults) {
                    val escapedMode = escapeCsv(r.name)
                    val encAllocKb = (r.encodeAllocatedBytes / 1024.0).format2Decimals()
                    val totalAllocKb = (r.totalAllocatedBytes / 1024.0).format2Decimals()
                    val peakRamKb = (r.peakResidentBytes / 1024.0).format2Decimals()
                    appendLine("$escapedImgName,$escapedPath,${img.width},${img.height},${img.channels},${img.totalPixels},${img.rawBytes},${img.inputBytes},${img.outputBytes},${img.compressionRatio.format2Decimals()},${img.spaceSavingsPct.format2Decimals()},$escapedMode,${r.avgMs.format2Decimals()},${r.minMs.format2Decimals()},${r.maxMs.format2Decimals()},${r.megapixelsPerSec.format2Decimals()},${r.throughputMBps.format2Decimals()},${r.speedup.format2Decimals()},$encAllocKb,$totalAllocKb,$peakRamKb,${r.bytesPerPixel.format2Decimals()},${r.memorySavedPct.format2Decimals()},${img.verifiedIdentical}")
                }
            }
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun Double.format2Decimals(): String = formatDecimals(2)
}

fun main(args: Array<String>) = KqoifCommand()
    .subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
    .main(args)

