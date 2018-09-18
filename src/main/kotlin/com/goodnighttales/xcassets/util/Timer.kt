package com.goodnighttales.xcassets.util

import java.time.Duration

class Timer {
    private val startNanos = System.nanoTime()

    fun stop(): String {
        val stopNanos = System.nanoTime()
        val time = stopNanos-startNanos
        var duration = Duration.ofNanos(time)
        duration = Duration.ofMillis((duration.toMillis()/10)*10)
        return duration.humanReadable()
    }
}

fun Duration.humanReadable(): String {
    return this.toString()
            .substring(2)
            .replace("(\\d[HMS])(?!$)", "$1 ")
            .replace("(\\w)", "$1 ")
            .toLowerCase()
}
