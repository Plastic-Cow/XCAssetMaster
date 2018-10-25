package com.goodnighttales.xcassetmaster.util

import java.awt.Dimension
import java.io.File
import java.text.DecimalFormat
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
fun Long.toReadableFileSize(): String {
    val digitGroups = (Math.log10(this.toDouble())/3).roundToInt()
    return DecimalFormat("#,##0.#").format(this/Math.pow(1000.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun File.getImageSize(): Dimension {
    ImageIO.createImageInputStream(this).use { input ->
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
        throw IllegalArgumentException("Couldn't get dimensions of image $this")
    }
}

val Dimension.aspectRatio: Double
    get() = (width.toDouble()/height)
