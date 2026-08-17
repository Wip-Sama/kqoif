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
 * Subcommand to benchmark and compare all QOI encoding strategies (In-Memory Direct, In-Memory Object,
 * Rolling Direct, Rolling Object) and verify 100% byte-for-byte fidelity and memory footprint.
 */
class BenchmarkCommand : CliktCommand(name = "benchmark") {
    override fun help(context: Context) = "Benchmark and compare In-Memory vs Rolling Direct and Object encoder strategies."

    private val input: File by argument(
        name = "INPUT",
        help = "Path to the image file (.qoi, .png, .jpg, .bmp, .webp, .tiff) to benchmark"
    ).file(mustExist = true, canBeDir = false)

    private val iterations: Int by option(
        "-i", "--iterations",
        help = "Number of measurement iterations (default: 10)"
    ).int().default(10)

    private val warmup: Int by option(
        "-w", "--warmup",
        help = "Number of warmup iterations (default: 3)"
    ).int().default(3)

    private val format: DumpFormat by option(
        "-f", "--format",
        help = "Output format (TEXT or JSON, default: TEXT)"
    ).enum<DumpFormat>().default(DumpFormat.TEXT)

    override fun run() {
        val inExt = input.extension.lowercase()
        val qoiImage = if (inExt == "qoi") {
            QoiImageIO.read(input)
        } else {
            val buffered = ImageIO.read(input) ?: throw IllegalArgumentException("Failed to read image: ${input.name}")
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

        require(bytesMemDirect.contentEquals(bytesMemObject)) {
            "Validation Failure: In-Memory DIRECT and In-Memory OBJECT produced different bytes!"
        }
        require(bytesMemDirect.contentEquals(bytesRollDirect)) {
            "Validation Failure: In-Memory DIRECT and Rolling DIRECT produced different bytes!"
        }
        require(bytesMemDirect.contentEquals(bytesRollObject)) {
            "Validation Failure: In-Memory DIRECT and Rolling OBJECT produced different bytes!"
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
        val results = mutableListOf<BenchmarkModeResult>()
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

            results.add(
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
        val finalResults = results.map { r ->
            val speedup = if (r.avgMs > 0.0) baselineAvgMs / r.avgMs else 1.0
            val savedPct = if (baselineTotalAllocBytes > 0L) {
                ((baselineTotalAllocBytes - r.totalAllocatedBytes).toDouble() / baselineTotalAllocBytes * 100.0)
            } else 0.0
            r.copy(speedup = speedup, memorySavedPct = savedPct)
        }

        if (format == DumpFormat.JSON) {
            echo(formatJson(qoiImage, ingestionAllocatedBytes, finalResults))
        } else {
            echo(formatText(qoiImage, ingestionAllocatedBytes, finalResults))
        }
    }

    private fun getThreadAllocatedBytes(): Long {
        val threadMXBean = ManagementFactory.getThreadMXBean()
        if (threadMXBean is com.sun.management.ThreadMXBean && threadMXBean.isThreadAllocatedMemorySupported) {
            if (!threadMXBean.isThreadAllocatedMemoryEnabled) {
                try {
                    threadMXBean.isThreadAllocatedMemoryEnabled = true
                } catch (_: Throwable) {}
            }
            return threadMXBean.getThreadAllocatedBytes(Thread.currentThread().id)
        }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }

    private fun formatText(image: QoiImage, ingestionBytes: Long, results: List<BenchmarkModeResult>): String {
        val imgMemMB = (ingestionBytes / (1024.0 * 1024.0)).format2Decimals()
        val scanlineKB = ((image.width.toLong() * 4L) / 1024.0).format2Decimals()
        return buildString {
            appendLine("=== QOI Encoding & Memory Benchmark ===")
            appendLine("Image: ${input.name} (${image.width}x${image.height}, ${image.channels} channels, ${image.pixels.size} pixels)")
            appendLine("Image In-Memory Footprint (List<Color>): ${imgMemMB} MB | Scanline Stream Buffer: ${scanlineKB} KB")
            appendLine("Iterations: $iterations measured, $warmup warmup")
            appendLine("Output Size: ${results[0].outputBytes} bytes (Byte-for-byte exact match verified across all 4 modes)")
            appendLine("")
            appendLine("Encoder Mode                       | Avg Time (ms) | Speedup | Encode Alloc (KB) | Total Pipeline (KB) | Peak RAM (KB) | Total Mem Saved")
            appendLine("-----------------------------------+---------------+---------+-------------------+---------------------+---------------+----------------")
            for (r in results) {
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
    }

    private fun formatJson(image: QoiImage, ingestionBytes: Long, results: List<BenchmarkModeResult>): String {
        return buildString {
            appendLine("{")
            appendLine("  \"image\": \"${input.name}\",")
            appendLine("  \"width\": ${image.width},")
            appendLine("  \"height\": ${image.height},")
            appendLine("  \"pixels\": ${image.pixels.size},")
            appendLine("  \"inMemoryImageFootprintKB\": ${(ingestionBytes / 1024.0).format2Decimals()},")
            appendLine("  \"scanlineStreamBufferKB\": ${((image.width.toLong() * 4L) / 1024.0).format2Decimals()},")
            appendLine("  \"outputBytes\": ${results[0].outputBytes},")
            appendLine("  \"verifiedIdentical\": true,")
            appendLine("  \"results\": [")
            results.forEachIndexed { i, r ->
                val comma = if (i < results.size - 1) "," else ""
                appendLine("    {")
                appendLine("      \"mode\": \"${r.name}\",")
                appendLine("      \"avgMs\": ${r.avgMs.format2Decimals()},")
                appendLine("      \"minMs\": ${r.minMs.format2Decimals()},")
                appendLine("      \"maxMs\": ${r.maxMs.format2Decimals()},")
                appendLine("      \"megapixelsPerSec\": ${r.megapixelsPerSec.format2Decimals()},")
                appendLine("      \"throughputMBps\": ${r.throughputMBps.format2Decimals()},")
                appendLine("      \"speedup\": ${r.speedup.format2Decimals()},")
                appendLine("      \"encodeAllocatedKB\": ${(r.encodeAllocatedBytes / 1024.0).format2Decimals()},")
                appendLine("      \"totalPipelineAllocatedKB\": ${(r.totalAllocatedBytes / 1024.0).format2Decimals()},")
                appendLine("      \"peakResidentKB\": ${(r.peakResidentBytes / 1024.0).format2Decimals()},")
                appendLine("      \"bytesPerPixel\": ${r.bytesPerPixel.format2Decimals()},")
                appendLine("      \"memorySavedPct\": ${r.memorySavedPct.format2Decimals()}")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun Double.format2Decimals(): String = formatDecimals(2)
}

fun main(args: Array<String>) = KqoifCommand()
    .subcommands(ConvertCommand(), DumpCommand(), BenchmarkCommand())
    .main(args)
