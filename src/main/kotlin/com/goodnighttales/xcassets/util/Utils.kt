package com.goodnighttales.xcassets.util

import java.text.DecimalFormat
import kotlin.math.roundToInt

private val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
fun Long.toReadableFileSize(): String {
    val digitGroups = (Math.log10(this.toDouble())/3).roundToInt()
    return DecimalFormat("#,##0.#").format(this/Math.pow(1000.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
