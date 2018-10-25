package com.goodnighttales.xcassetmaster

import com.goodnighttales.xcassetmaster.util.Timer
import com.goodnighttales.xcassetmaster.util.aspectRatio
import com.goodnighttales.xcassetmaster.util.getImageSize
import com.goodnighttales.xcassetmaster.util.not
import com.googlecode.pngtastic.core.PngImage
import com.googlecode.pngtastic.core.PngOptimizer
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

class XCAsset(val file: File, catalogFile: File) {
    val relativeFile = file.relativeTo(catalogFile)
    val name = file.nameWithoutExtension
    var master: MasterAsset? = null

    val images = file.listFiles { it -> it.extension == "png" }.map { Image(it, it.relativeTo(catalogFile)) }

    fun forceNeedsUpdate() {
        images.forEach { it.needsUpdate = true }
    }

    fun checkNeedsUpdate() {
        images.forEach { it.checkNeedsUpdate() }
    }

    fun updateModificationDates(lastModified: Long) {
        images.forEach { it.updateModificationDates(lastModified) }
    }

    fun updateAsset(source: BufferedImage, crush: Boolean, crop: Boolean) {
        images.forEach { it.update(source, crush, crop) }
    }

    inner class Image(val file: File, val relativeFile: File) {
        val name = file.nameWithoutExtension
        val dimensions = file.getImageSize()
        var needsUpdate = false
        var preCrush: Long = 0L
        var postCrush: Long = 0L
        val aspectFillDimensions: Dimension
            get() {
                val master = master ?: return dimensions
                val widthScale = dimensions.width.toDouble()/master.dimensions.width
                val heightScale = dimensions.height.toDouble()/master.dimensions.height
                val scaled = Dimension(dimensions)
                when {
                    widthScale > heightScale -> scaled.height = (master.dimensions.height * widthScale).toInt()
                    heightScale > widthScale -> scaled.width = (master.dimensions.width * heightScale).toInt()
                }

                if(abs(scaled.width - dimensions.width) <= 1) scaled.width = dimensions.width
                if(abs(scaled.height - dimensions.height) <= 1) scaled.height = dimensions.height
                return scaled
            }
        val mismatchedAspectRatio: Boolean
            get() {
                return aspectFillDimensions != dimensions
            }

        fun checkNeedsUpdate() {
            val master = master
            if(master != null) {
                val dateDifference = Math.abs(file.lastModified() - master.file.lastModified())
                needsUpdate = dateDifference > MOD_DATE_TOLERANCE
            }
        }

        fun update(source: BufferedImage, crush: Boolean, crop: Boolean) {
            val master = master!!
            val time = Timer()

            val prefix = "\r${!"2K"}  - ${!"37;1m"}${dimensions.width}${!"m"}⨉${!"37;1m"}${dimensions.height}${!"m"} " +
                    this.name.replace(this@XCAsset.name, "") // turns `blah@2x` into `@2x`

            print("$prefix - Scaling ...")

            val drawnDimensions =
                    if(mismatchedAspectRatio && crop) {
                        this.aspectFillDimensions
                    } else {
                        this.dimensions
                    }
            val scaledInstance = source.getScaledInstance(drawnDimensions.width, drawnDimensions.height, java.awt.Image.SCALE_SMOOTH)
            val scaled = BufferedImage(dimensions.width, dimensions.height, BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.drawImage(scaledInstance,
                    -(drawnDimensions.width-dimensions.width)/2, -(drawnDimensions.height-dimensions.height)/2,
                    drawnDimensions.width, drawnDimensions.height,
                    null)
            g.dispose()

            print("$prefix - Writing ...")
            val outputFile = if (crush) tempFile else file
            val out = outputFile.outputStream()
            ImageIO.write(scaled, "png", out)

            preCrush = outputFile.length()
            if (crush) {
                print("$prefix - Optimizing ...")
                optimize(FileInputStream(tempFile), file)
            }
            postCrush = file.length()

            val crushString = if(crush) {
                " (${!"36m"}${String.format("%.1f", ((preCrush-postCrush)/preCrush.toDouble())*100)}%${!"m"} crushed)"
            } else {
                ""
            }

            println("$prefix - Completed in ${!"33;1m"}${time.stop()}${!"m"}$crushString")
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

        fun updateModificationDates(lastModified: Long) {
            file.setLastModified(lastModified)
        }
    }
}

val tempFile: File = File.createTempFile("XCAssetMaster-", ".png").also { it.deleteOnExit() }

const val MOD_DATE_TOLERANCE = 2000
