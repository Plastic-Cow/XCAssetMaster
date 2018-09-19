package com.goodnighttales.xcassets

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.goodnighttales.xcassets.optimizer.XCAssetsOptimizer
import java.io.File

class XCAssetsMain : CliktCommand(name = "xcassets") {
    val output: File by argument("output", "The output .xcassets directory")
            .file(exists = true, fileOkay = false, folderOkay = true, writable = true, readable = true)
            .validate { require(it.extension == "xcassets") { "invalid extension ${it.extension}, expecting xcassets"} }
    val input: List<File> by argument("input", "The input images and directories")
            .file(exists = true, fileOkay = true, folderOkay = true, readable = true)
            .multiple()
    val crush: Boolean by option("-c", "--crush", help = "Enable PNGCrush").flag("--no-crush", default = true)
    val force: Boolean by option("-f", "--force", help = "Don't check file modification dates").flag("--no-force", default = false)

    override fun run() {
        val optimizer = XCAssetsOptimizer(input, output, crush, force)
        optimizer()
    }
}

fun main(args: Array<String>) = XCAssetsMain().main(args)
