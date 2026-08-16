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
import org.wip.kqoif.imageio.QoiImageIO
import java.io.File

/**
 * Root CLI command for kqoif.
 */
class KqoifCommand : CliktCommand(name = "kqoif") {
    override fun help(context: Context) = "Fast, lightweight QOI (Quite OK Image) format encoder, decoder, and inspection tool."
    override fun run() = Unit
}

/**
 * Subcommand to convert between QOI and other standard image formats (PNG, JPG, BMP).
 */
class ConvertCommand : CliktCommand(name = "convert") {
    override fun help(context: Context) = "Convert an image between QOI and standard formats (PNG, JPG, BMP)."

    private val input: File by argument(
        name = "INPUT",
        help = "Path to the input image file (.qoi, .png, .jpg, .bmp)"
    ).file(mustExist = true, canBeDir = false)

    private val output: File by argument(
        name = "OUTPUT",
        help = "Path to the output image file (.qoi, .png, .jpg, .bmp)"
    ).file(canBeDir = false)

    private val showStats: Boolean by option(
        "-s", "--stats",
        help = "Display diagnostic and compression statistics after conversion"
    ).flag(default = false)

    override fun run() {
        echo("Converting '${input.name}' -> '${output.name}'...")
        val startTime = System.currentTimeMillis()

        val image = QoiImageIO.read(input)
        QoiImageIO.write(image, output)

        val duration = System.currentTimeMillis() - startTime
        echo("Conversion successful in ${duration}ms (${output.length()} bytes written).")

        if (showStats) {
            val qoiBytes = if (output.extension.lowercase() == "qoi") output.readBytes() else image.encode()
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

fun main(args: Array<String>) = KqoifCommand()
    .subcommands(ConvertCommand(), DumpCommand())
    .main(args)
