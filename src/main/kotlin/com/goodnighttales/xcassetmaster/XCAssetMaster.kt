package com.goodnighttales.xcassetmaster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.goodnighttales.xcassetmaster.util.Timer
import com.goodnighttales.xcassetmaster.util.not
import com.goodnighttales.xcassetmaster.util.toReadableFileSize
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.*
import java.nio.file.Files
import java.util.stream.Collectors
import javax.imageio.ImageIO


fun main(args: Array<String>) = MainCommand.main(args)

object MainCommand : CliktCommand(
        name = "xcassetmaster",
        help = """
            Easily manage Xcode Asset Catalogs and update them based on a set of master images.
        """.trimIndent()
) {
    init {
        subcommands(XCAssetMaster, ScaleGenerator)
    }
    override fun run() {
        // nop
    }

}

object XCAssetMaster : CliktCommand(
        name = "update",
        help = """
            Automatically updates images in an Xcode Asset Catalog based on a directory of masters.
            In essence this script simply substitutes rescaled master pixel data into existing PNG files.

            This script goes through every asset in the passed Asset Catalog that has one of the whitelisted extensions
            (.imageset and .appiconset by default) and searches the passed input directories/files for an identically
            named .png file to use as a master. Once it finds an asset's master file it rescales the master to match
            each png file in the asset and replaces them in-place. This process is optimized by copying the modification
            date of the master onto the rescaled images and only updating images whose modification dates don't match
            their master's.
        """.trimIndent()
) {

    val xcAssetExtensions = mutableSetOf(
            "imageset",
            "appiconset"
    )

    val output: File by argument("output", "The output .xcasset directory")
            .file(exists = true, fileOkay = false, folderOkay = true, writable = true, readable = true)
            .validate { require(it.extension == "xcassets") { "invalid extension ${it.extension}, expecting xcassets"} }
    val inputs: List<File> by argument("inputs", "The input images and directories")
            .file(exists = true, fileOkay = true, folderOkay = true, readable = true)
            .multiple()
    val dry: Boolean by option("--dry", help = "Do a dry run, only printing warnings").flag("--no-dry", default = false)
    val crush: Boolean by option("-c", "--crush", help = "Enable PNGCrush").flag("--no-crush", default = true)
    val force: Boolean by option("-f", "--force", help = "Don't check file modification dates").flag("--no-force", default = false)
    val crop: Boolean by option("--crop", help = "Crop output images if the aspect ratio is mismatched. " +
            "(A square master and short and wide output will crop the top and bottom evenly)").flag("--no-crop", default = false)
    val exclude: List<String> by option("-x", "--exclude", help = "Exclude masters with this in their path").multiple()
    val additionalAssetExtensions: List<String> by option("--asset-extension", help = "Whitelist more asset extensions (default whitelist is 'imageset' and 'appiconset')").multiple()

    override fun run() {
        xcAssetExtensions.addAll(additionalAssetExtensions)
        val masters = getMasters().associateBy { it.name }
        val assets = getAssets()

        assets.forEach { asset ->
            val master = masters[asset.name]
            if(master != null) {
                asset.master = master
                master.assets.add(asset)
            }
        }

        if(!dry) {
            printWarnings(masters.values.toList(), assets)
        }

        if(force) {
            assets.forEach { it.forceNeedsUpdate() }
        } else {
            assets.forEach { it.checkNeedsUpdate() }
        }

        val updateCount = assets.sumBy { it.images.count { it.needsUpdate } }
        println("Compared ${assets.sumBy { it.images.size }} assets against ${masters.size} master images")
        println("Found $updateCount out-of-date images")

        if(!dry) {
            if (updateCount != 0) {
                val timer = Timer()

                masters.values.forEach {
                    it.updateAssets(crush, crop)
                    it.updateModificationDates()
                }

                println("${!"33m"}Time: ${!"33;1m"}${timer.stop()}")

                if (crush) {
                    val preCrush = assets.fold(0L) { t, it -> t + it.images.fold(0L) { t, it -> t + it.preCrush } } + 1L
                    val postCrush = assets.fold(0L) { t, it -> t + it.images.fold(0L) { t, it -> t + it.postCrush } } + 1L
                    val crushSavingFraction = (preCrush - postCrush) / preCrush.toDouble()
                    val crushSavings = String.format("%.1f", crushSavingFraction * 100)
                    println("${!"32m"}Crushed by $crushSavings% (saving ${(preCrush - postCrush).toReadableFileSize()})")
                }
            }
        }

        printWarnings(masters.values.toList(), assets)

        System.exit(0) // program hangs at the end without this. All the threads are just some form of "finalizer"
    }

    private fun getMasters(): List<MasterAsset> {
        val files = mutableListOf<Pair<File, File>>()

        inputs.forEach { input ->
            if (input.isFile)
                files.add(input to input)
            else if (input.isDirectory)
                files.addAll(filesInDirectory(input) { it.isFile && it.extension == "png" }.map { it to input })
        }

        val exclude = this.exclude.toSet()
        files.filter { it.first.toPath().asSequence().none { it.toString() in exclude } }.let {
            files.clear()
            files.addAll(it)
        }

        return files.map { MasterAsset(it.first, it.second) }
    }

    private fun getAssets(): List<XCAsset> {
        return filesInDirectory(output) {
            it.isDirectory && it.extension in xcAssetExtensions
        }.map { XCAsset(it, output) }

    }

    private fun filesInDirectory(dir: File, test: (file: File) -> Boolean = { true }): List<File> {
        return Files.walk(dir.toPath()).collect(Collectors.toList()).map { it.toFile() }.filter(test)
    }

    class Warnings(
            val master: MasterAsset,
            val duplicates: Map<Dimension, List<XCAsset.Image>>,
            val upscaled: List<XCAsset.Image>,
            val aspect: List<XCAsset.Image>,
            val unscaled: List<XCAsset.Image>
    ) {
        fun printWarnings() {
                printMasterHeader()
            if(duplicates.isNotEmpty()) printDuplicateWarnings()
            if(aspect.isNotEmpty()) printAspectWarnings()
            if(upscaled.isNotEmpty()) printUpscaledWarnings()
            if(unscaled.isNotEmpty()) printUnscaledWarnings()
        }

        private fun printMasterHeader() {
            println("\r${!"2K"}${!"31;1m"}${master.relativeFile}${!"m"}")
        }

        private fun printDuplicateWarnings() {
            println("\r${!"2K"}${!"33;1m"} - Duplicate resolutions:${!"m"}")

            duplicates.forEach { (res, images) ->
                println("\r${!"2K"}${!"33;1m"}   - ${!"34;1m"}${res.width}${!"m"}⨉${!"34;1m"}${res.height}${!"m"}")

                images.forEach { image ->
                    println("\r${!"2K"}${!"33;1m"}     - ${!"m"}${image.relativeFile}${!"m"}")
                }
            }
        }

        private fun printAspectWarnings() {
            println("\r${!"2K"}${!"33;1m"} - Mismatched aspect ratio:${!"m"}")

            upscaled.forEach { image ->
                val aspect = image.aspectFillDimensions
                if(image.dimensions.width != image.aspectFillDimensions.width) {
                    println("\r${!"2K"}${!"33;1m"}   - ${!"m"}${image.relativeFile}: width: ${!"34;1m"}${image.dimensions.width} " +
                            "${!"m"}vs. ${!"34;1m"}${aspect.width}${!"m"}(master)")
                } else if(image.dimensions.height != image.aspectFillDimensions.height) {
                    println("\r${!"2K"}${!"33;1m"}   - ${!"m"}${image.relativeFile}: height: ${!"34;1m"}${image.dimensions.height} " +
                            "${!"m"}vs. ${!"34;1m"}${aspect.height}${!"m"}(master)")
                }
            }
        }

        private fun printUpscaledWarnings() {
            println("\r${!"2K"}${!"33;1m"} - Upscaled images:${!"m"}")
            println("\r${!"2K"}${!"33;1m"}   - ${!"m"}Master: ${!"34;1m"}${master.dimensions.width}${!"m"}⨉${!"34;1m"}${master.dimensions.height}${!"m"}")

            upscaled.forEach { image ->
                println("\r${!"2K"}${!"33;1m"}   - ${!"m"}${image.relativeFile}: ${!"34;1m"}${image.dimensions.width}${!"m"}⨉${!"34;1m"}${image.dimensions.height}${!"m"}")
            }
        }

        private fun printUnscaledWarnings() {
            if(unscaled.size == 1) {
                println("\r${!"2K"}${!"33;1m"} - Same resolution as master:${!"m"} ${unscaled[0].relativeFile}")
            } else {
                println("\r${!"2K"}${!"33;1m"} - Same resolution as master:${!"m"}")
                unscaled.forEach { file ->
                    println("\r${!"2K"}${!"33;1m"}   - ${!"m"}${file.relativeFile}${!"m"}")
                }
            }
        }
    }

    private fun printWarnings(masters: List<MasterAsset>, assets: List<XCAsset>) {

        val warnings = mutableListOf<Warnings>()
        masters.forEach { master ->
            val resolutions = mutableMapOf<Dimension, MutableList<XCAsset.Image>>()

            master.assets.forEach { asset ->
                asset.images.forEach { image ->
                    resolutions.getOrPut(image.dimensions) { mutableListOf() }.add(image)
                }
            }
            val duplicates = resolutions.filterValues { it.size > 1 }
            val upscaled = resolutions.filterKeys { it.width > master.dimensions.width || it.height > master.dimensions.height }.values.flatten()
            val aspect = resolutions.values.flatten().filter { it.mismatchedAspectRatio }
            val unscaled = resolutions[master.dimensions] ?: mutableListOf()
            if(unscaled.isNotEmpty() || upscaled.isNotEmpty() || aspect.isNotEmpty() || duplicates.isNotEmpty())
                warnings.add(Warnings(master, duplicates, upscaled, aspect, unscaled))
        }

        if(warnings.isNotEmpty()) {
            warnings.forEach { it.printWarnings() }
        }

        masters.forEach { master ->
            if(master.assets.isEmpty()) {
                println("\r${!"2K"}${!"33;1m"}Unused master: ${!"m"}${master.relativeFile}${!"m"}")
            }
        }
        assets.forEach { asset ->
            if(asset.master == null) {
                println("\r${!"2K"}${!"33;1m"}Asset missing master: ${!"m"}${asset.relativeFile}${!"m"}")
            }
        }
    }
}

object ScaleGenerator : CliktCommand(
        name = "generate",
        help = """
            Generates empty white images at various scales and writes them to the working directory.
        """.trimIndent()
) {

    val name: String by argument("name", "The base name for the image. This name will have the scale and extension " +
            "added and will be put in the current working directory")
    val size: Pair<Int, Int> by argument("size", "The size of the 1x scale in the format ###x###").convert {
        val components = it.split("x")
        if(components.size != 2) {
            fail("Invalid number of size components ${components.size}. Ensure the argument is in the format ###x###")
        }
        val (width, height) = components.map { it.toInt() }
        return@convert width to height
    }
    val scales: List<Int> by option("-s", "--scale", help = "Generate an additional image at N x the base size. " +
            "The file will be given an @Nx at the end of its name. Default scales are @2x and @3x")
            .int().multiple(listOf(2, 3))
    val color: Color by option("-c", "--color", help = "Specify the background color of the image in AARRGGBB hex. " +
            "If no alpha is provided the color will be opaque. Default is FFFFFF").convert {
        Color(Integer.parseUnsignedInt(it, 16))
    }.default(Color.WHITE)

    override fun run() {
        for(scale in scales.toSet() + setOf(1)) {
            val filename =
                    if(scale == 1)
                        "$name.png"
                    else
                        "$name@${scale}x.png"
            print("$name - Generating")
            val image = BufferedImage(size.first * scale, size.second * scale, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()

            graphics.paint = color
            graphics.fillRect(0, 0, image.width, image.height)

            graphics.dispose()
            print("\r$name - Writing       ")
            ImageIO.write(image, "png", File(filename))
            println("\r$name - Done        ")
        }
    }

}
