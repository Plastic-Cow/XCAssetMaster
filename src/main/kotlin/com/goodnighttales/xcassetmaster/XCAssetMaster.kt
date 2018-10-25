package com.goodnighttales.xcassetmaster

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
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

fun main(args: Array<String>) = XCAssetMaster.main(args)

object XCAssetMaster : CliktCommand(
        name = "xcassetmaster",
        help = """
            Automatically updates images in an Xcode Asset Catalog based on a directory of masters.
            In essence this script simply substitutes rescaled master pixel data into existing PNG files.

            This script goes through every asset in the passed Asset Catalog that has one of the whitelisted extensions
            (.imageset and .appiconset by default) and searches the passed input directories/files for an identically
            named .png file to use as a master. Once it finds an asset's master file it rescales the master to match
            each png file in the asset and replaces them in-place. This process is optimized by copying the modification
            date of the master onto the rescaled images and only updating images whose modification dates don't match
            their masters'.

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
    val crush: Boolean by option("-c", "--crush", help = "Enable PNGCrush").flag("--no-crush", default = true)
    val force: Boolean by option("-f", "--force", help = "Don't check file modification dates").flag("--no-force", default = false)
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

        if(force) {
            assets.forEach { it.forceNeedsUpdate() }
        } else {
            assets.forEach { it.checkNeedsUpdate() }
        }

        val updateCount = assets.sumBy { it.images.count { it.needsUpdate } }
        println("Compared ${assets.sumBy { it.images.size }} assets against ${masters.size} master images")
        println("Found $updateCount out-of-date images")

        if(updateCount != 0) {
            val timer = Timer()

            masters.values.forEach {
                it.updateAssets(crush)
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
            val unscaled: List<XCAsset.Image>
    ) {
        fun printWarnings() {
                printMasterHeader()
            if(duplicates.isNotEmpty()) printDuplicateWarnings()
            if(unscaled.isNotEmpty()) printUnscaledWarnings()
        }

        private fun printMasterHeader() {
            println("\r${!"2K"}${!"33;1m"}! ${master.relativeFile}:${!"m"}")
        }

        private fun printDuplicateWarnings() {
            println("\r${!"2K"}${!"33;1m"}! - Duplicate resolutions:${!"m"}")

            duplicates.forEach { (res, images) ->
                println("\r${!"2K"}${!"33;1m"}!   - ${!"m"}${res.width}⨉${res.height}${!"m"}")

                images.forEach { image ->
                    println("\r${!"2K"}${!"33;1m"}!     - ${!"m"}${image.relativeFile}${!"m"}")
                }
            }
        }

        private fun printUnscaledWarnings() {
            if(unscaled.size == 1) {
                println("\r${!"2K"}${!"33;1m"}! - Same resolution as master:${!"m"} ${unscaled[0].relativeFile}")
            } else {
                println("\r${!"2K"}${!"33;1m"}! - Same resolution as master:${!"m"}")
                unscaled.forEach { file ->
                    println("\r${!"2K"}${!"33;1m"}!   - ${!"m"}${file.relativeFile}${!"m"}")
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
            val unscaled = resolutions[master.dimensions] ?: mutableListOf()
            if(unscaled.isNotEmpty() || duplicates.isNotEmpty())
                warnings.add(Warnings(master, duplicates, unscaled))
        }

        if(warnings.isNotEmpty()) {
            warnings.forEach { it.printWarnings() }
        }

        masters.forEach { master ->
            if(master.assets.isEmpty()) {
                println("\r${!"2K"}${!"31;1m"}! Unused master: ${!"m"}${master.relativeFile}${!"m"}")
            }
        }
        assets.forEach { asset ->
            if(asset.master == null) {
                println("\r${!"2K"}${!"31;1m"}! Asset missing master: ${!"m"}${asset.relativeFile}${!"m"}")
            }
        }
    }
}

