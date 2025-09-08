/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import onl.ycode.kpacker.DEBUG

object ImageConverter {
    private val fs = FileSystem.SYSTEM

    /**
     * Supported image formats for input
     */
    private val supportedFormats = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif",
        "svg", "pdf", "ico", "icns", "webp", "avif", "heic", "heif"
    )

    /**
     * Vector formats that need special handling
     */
    private val vectorFormats = setOf("svg", "pdf")

    /**
     * Converts any supported image format to PNG with 512x512 size while preserving aspect ratio and alpha channel.
     * If input is already PNG, it may still be processed to ensure consistent size.
     *
     * @param inputPath Path to the input image file
     * @param outputPath Path where the converted PNG should be saved
     * @return true if conversion was successful, false otherwise
     */
    suspend fun convertToPng(inputPath: Path, outputPath: Path): Boolean {
        if (!fs.exists(inputPath)) {
            if (DEBUG) println("Input image file does not exist: $inputPath")
            return false
        }

        val inputExtension = inputPath.name.substringAfterLast('.', "").lowercase()
        if (inputExtension !in supportedFormats) {
            if (DEBUG) println("Unsupported image format: $inputExtension")
            return false
        }

        // Ensure output directory exists
        fs.createDirectories(outputPath.parent!!)

        return if (inputExtension in vectorFormats)
            convertVectorToPng(inputPath, outputPath, inputExtension)
        else
            convertRasterToPng(inputPath, outputPath)
    }

    /**
     * Convert vector formats (SVG, PDF) to PNG using Docker container with proper ImageMagick
     */
    private fun convertVectorToPng(inputPath: Path, outputPath: Path, format: String): Boolean {
        val runner = ContainerFactory.createRunner()
        try {
            // Copy input file to working directory
            fs.copy(inputPath, runner.workingDir / inputPath.name)

            // For vector formats, we need special handling to ensure good quality
            val convertArgs = when (format) {
                "svg" -> {
                    // SVG: Use rsvg-convert directly instead of ImageMagick's SVG delegate for better color preservation
                    arrayOf(
                        "rsvg-convert",
                        "-w", "512",
                        "-h", "512",
                        "--format", "png",
                        "--output",
                        outputPath.name,
                        inputPath.name
                    )
                }

                "pdf" -> {
                    // PDF: Use higher density, then resize to fit within 512x512 preserving aspect ratio
                    arrayOf(
                        "convert",
                        "-density", "300",
                        "-background", "transparent",
                        "${inputPath.name}[0]", // Take first page of PDF
                        "-resize", "512x512\\>",  // Only shrink if larger, preserve aspect ratio
                        "-extent", "512x512",   // Center in 512x512 canvas with transparent background
                        "-gravity", "center",
                        outputPath.name
                    )
                }

                else -> return false
            }

            if (DEBUG) println("Converting $format to PNG: ${inputPath.name} -> ${outputPath.name}")

            val cmd = runner.run(convertArgs.joinToString(" "))

            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()

            cmd.addOutListener { output ->
                val text = output.decodeToString()
                outputBuffer.append(text)
                if (DEBUG) print(text)
            }
            cmd.addErrorListener { error ->
                val text = error.decodeToString()
                errorBuffer.append(text)
                if (DEBUG) print("ERROR: $text")
            }

            val exitCode = cmd.exec().waitFor()

            if (exitCode == 0) {
                // Copy result back from working directory
                val resultFile = runner.workingDir / outputPath.name
                if (fs.exists(resultFile)) {
                    fs.copy(resultFile, outputPath)
                    if (DEBUG) println("Successfully converted $format to PNG: ${outputPath.name}")
                    return true
                }
            }

            if (DEBUG) {
                println("Failed to convert $format to PNG (exit code: $exitCode)")
                if (errorBuffer.isNotEmpty()) {
                    println("Error: ${errorBuffer.toString().trim()}")
                }
            }
            return false
        } finally {
            // Clean up temporary directory
            try {
                fs.deleteRecursively(runner.workingDir)
            } catch (e: Exception) {
                if (DEBUG) println("Failed to clean up temporary directory: ${e.message}")
            }
        }
    }

    /**
     * Convert raster formats (PNG, JPG, etc.) to PNG using ImageMagick
     */
    private fun convertRasterToPng(inputPath: Path, outputPath: Path): Boolean {
        val runner = ContainerFactory.createRunner()
        try {
            // Copy input file to working directory
            fs.copy(inputPath, runner.workingDir / inputPath.name)

            if (DEBUG) println("Converting raster image to PNG: ${inputPath.name} -> ${outputPath.name}")

            // ImageMagick command to resize while preserving aspect ratio and alpha channel
            val cmd =
                runner.run("convert ${inputPath.name} -background transparent -resize '512x512>' -extent 512x512 -gravity center ${outputPath.name}")

            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()

            cmd.addOutListener { output ->
                val text = output.decodeToString()
                outputBuffer.append(text)
                if (DEBUG) print(text)
            }
            cmd.addErrorListener { error ->
                val text = error.decodeToString()
                errorBuffer.append(text)
                if (DEBUG) print("ERROR: $text")
            }

            val exitCode = cmd.exec().waitFor()

            if (exitCode == 0) {
                // Copy result back from working directory
                val resultFile = runner.workingDir / outputPath.name
                if (fs.exists(resultFile)) {
                    fs.copy(resultFile, outputPath)
                    if (DEBUG) println("Successfully converted raster image to PNG: ${outputPath.name}")
                    return true
                }
            }

            if (DEBUG) {
                println("Failed to convert raster image to PNG (exit code: $exitCode)")
                if (errorBuffer.isNotEmpty()) {
                    println("Error: ${errorBuffer.toString().trim()}")
                }
            }
            return false
        } finally {
            // Clean up temporary directory
            try {
                fs.deleteRecursively(runner.workingDir)
            } catch (e: Exception) {
                if (DEBUG) println("Failed to clean up temporary directory: ${e.message}")
            }
        }
    }

    /**
     * Check if a file is a supported image format
     */
    fun isSupportedImageFormat(path: Path): Boolean {
        val extension = path.name.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    /**
     * Get a standardized PNG filename for an input image
     */
    fun getStandardPngName(originalPath: Path, appName: String): String {
        return "${appName.lowercase().replace(Regex("[^a-z0-9]"), "_")}_icon.png"
    }
}