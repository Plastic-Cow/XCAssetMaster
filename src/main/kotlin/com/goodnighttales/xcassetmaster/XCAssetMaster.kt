package com.goodnighttales.xcassetmaster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.goodnighttales.xcassetmaster.util.Timer
import com.goodnighttales.xcassetmaster.util.not
import com.goodnighttales.xcassetmaster.util.toReadableFileSize
import com.googlecode.pngtastic.core.PngImage
import com.googlecode.pngtastic.core.PngOptimizer
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.*
import java.nio.file.Files
import java.util.stream.Collectors
import javax.imageio.ImageIO

fun main(args: Array<String>) = XCAssetMaster().main(args)

class XCAssetMaster : CliktCommand(name = "xcassetmaster") {
    val tempFile = File("xcassetsTmp.png")
    val updates = mutableMapOf<File, MutableList<File>>()
    var totalAssets = 0
    var inputSize = 0L
    var totalPreCrush = 0L
    var totalPostCrush = 0L

    val xcAssetExtensions = setOf(
            "imageset",
            "appiconset"
    )

    val output: File by argument("output", "The output .xcassetmaster directory")
            .file(exists = true, fileOkay = false, folderOkay = true, writable = true, readable = true)
            .validate { require(it.extension == "xcassetmaster") { "invalid extension ${it.extension}, expecting xcassets"} }
    val inputs: List<File> by argument("inputs", "The input images and directories")
            .file(exists = true, fileOkay = true, folderOkay = true, readable = true)
            .multiple()
    val crush: Boolean by option("-c", "--crush", help = "Enable PNGCrush").flag("--no-crush", default = true)
    val force: Boolean by option("-f", "--force", help = "Don't check file modification dates").flag("--no-force", default = false)

    override fun run() {
        tempFile.deleteOnExit()
        val sourceFiles = mutableMapOf<String, File>()
        inputs.forEach { input ->
            if (input.isFile)
                sourceFiles[input.nameWithoutExtension] = input
            else if (input.isDirectory)
                sourceFiles.putAll(filesInDirectoryByName(input) { it.isFile && it.extension == "png" })
        }

        val assets = filesInDirectoryByName(output) {
            it.isDirectory && it.extension in xcAssetExtensions
        }

        assets.forEach {
            val source = sourceFiles[it.key] ?: return@forEach
            queueImageSetUpdates(it.value, source)
        }

        println("Compared $totalAssets assets against ${sourceFiles.size} master images")
        println("Found ${updates.values.sumBy { it.size }} out-of-date images")

        val timer = Timer()
        updates.forEach {
            updateImages(it.key, it.value)
        }
        println("${!"33m"}Time: ${!"33;1m"}${timer.stop()}")

        if(crush) {
            val crushSavingFraction = (totalPreCrush - totalPostCrush) / totalPreCrush.toDouble()
            val crushSavings = String.format("%.1f", crushSavingFraction * 100)
            println("${!"32m"}Crushed by $crushSavings% (saving ${(totalPreCrush-totalPostCrush).toReadableFileSize()})")
        }
    }

    private fun queueImageSetUpdates(target: File, source: File) {
        val contents = filesInDirectory(target) { it.isFile && it.extension == "png" }
        totalAssets += contents.size

        contents.forEach {
            val dateDifference = it.lastModified() - source.lastModified()
            if(force || dateDifference < MOD_DATE_TOLERANCE) {
                updates.getOrPut(source) { mutableListOf() }.add(it)
            }
        }
    }

    private fun updateImages(source: File, outputs: List<File>) {
        val totalTime = Timer()
        println("- ${!"32m"}${source.nameWithoutExtension}${!"m"}")
        print("  - Reading ...")
        val master = ImageIO.read(source)
        val previousDimensions = mutableMapOf<Dimension, MutableList<File>>()
        inputSize += source.length()

        outputs.forEach { dest ->
            val individualTime = Timer()
            val resolution = getImageSize(dest)
            previousDimensions.getOrPut(resolution) { mutableListOf() }.add(dest)

            val newName = dest.nameWithoutExtension.replace(source.nameWithoutExtension, "")
            val prefix = "\r${!"2K"}  - ${!"37;1m"}${resolution.width}${!"m"}⨉${!"37;1m"}${resolution.height}${!"m"} $newName"

            print("$prefix - Scaling ...")

            val scaledInstance = master.getScaledInstance(resolution.width, resolution.height, Image.SCALE_SMOOTH)
            val scaled = BufferedImage(resolution.width, resolution.height, BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.drawImage(scaledInstance, 0, 0, null)
            g.dispose()

            print("$prefix - Writing ...")
            val out = if (crush) tempFile.outputStream() else dest.outputStream()
            ImageIO.write(scaled, "png", out)

            val preCrush = tempFile.length()
            totalPreCrush += preCrush
            if (crush) {
                print("$prefix - Optimizing ...")
                optimize(FileInputStream(tempFile), dest)
            }
            val postCrush = dest.length()
            totalPostCrush += postCrush

            val crushString = if(crush) {
                " (${!"36m"}${String.format("%.1f", ((preCrush-postCrush)/preCrush.toDouble())*100)}%${!"m"} crushed)"
            } else {
                ""
            }
            println("$prefix - Completed in ${!"33;1m"}${individualTime.stop()}${!"m"}$crushString")
        }

        println("\r${!"2K"}  - Time: ${!"33;1m"}${totalTime.stop()}${!"m"}")

        val duplicates =  previousDimensions.filter { it.value.size > 1 }
        if(duplicates.isNotEmpty()) {
            println("\r${!"2K"}  ${!"31;1m"}! Duplicate resolutions:${!"m"}")

            duplicates.forEach { (res, files) ->
                println("\r${!"2K"}  ${!"31;1m"}! - ${res.width}${!"m"}⨉${!"31;1m"}${res.height}${!"m"}")

                files.forEach { file ->
                    println("\r${!"2K"}  ${!"31;1m"}!   - Path: ${!"31m"}${file.relativeTo(this.output)}${!"m"}")
                }
            }
        }
    }

    private fun optimize(inputStream: InputStream, file: File) {
        // load png image from a file
        val image = PngImage(inputStream)

        // optimize
        val optimizer = PngOptimizer()
        val optimizedImage = optimizer.optimize(image)

        // export the optimized image to a new file
        val optimizedBytes = ByteArrayOutputStream()
        optimizedImage.writeDataOutputStream(optimizedBytes)
        optimizedImage.export(file.absolutePath, optimizedBytes.toByteArray())
    }

    companion object {
        const val MOD_DATE_TOLERANCE = 2000

        private fun filesInDirectoryByName(dir: File, test: (file: File) -> Boolean): Map<String, File> {
            return filesInDirectory(dir, test).associateBy { it.nameWithoutExtension }
        }

        private fun filesInDirectory(dir: File, test: (file: File) -> Boolean = { true }): List<File> {
            return Files.walk(dir.toPath()).collect(Collectors.toList()).map { it.toFile() }.filter(test)
        }

        private fun getImageSize(file: File): Dimension {
            ImageIO.createImageInputStream(file).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = input
                        return Dimension(reader.getWidth(0), reader.getHeight(0))
                    } finally {
                        reader.dispose()
                    }
                }
                throw IllegalArgumentException("Couldn't get dimensions of image $file")
            }
        }
    }
}

