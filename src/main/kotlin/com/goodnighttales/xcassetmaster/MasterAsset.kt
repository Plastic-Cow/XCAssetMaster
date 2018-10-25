package com.goodnighttales.xcassetmaster

import com.goodnighttales.xcassetmaster.util.Timer
import com.goodnighttales.xcassetmaster.util.getImageSize
import com.goodnighttales.xcassetmaster.util.not
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class MasterAsset(val file: File, val base: File) {
    val name = file.nameWithoutExtension
    val relativeFile = file.relativeTo(base.parentFile)
    val dimensions = file.getImageSize()
    val assets = mutableListOf<XCAsset>()

    fun updateAssets(crush: Boolean) {
        val totalTime = Timer()
        println("- ${!"32m"}$name${!"m"}")
        print("  - Reading ...")
        val image = ImageIO.read(file)

        assets.forEach { dest ->
            dest.updateAsset(image, crush)
        }

        println("\r${!"2K"}  - Time: ${!"33;1m"}${totalTime.stop()}${!"m"}")
    }

    fun updateModificationDates() {
        assets.forEach {
            it.updateModificationDates(file.lastModified())
        }
    }
}
