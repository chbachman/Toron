package com.chbachman.toron.util

import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

fun Long.toUTCDate(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

fun LocalDateTime.toUTC(): Long =
    this.toEpochSecond(ZoneOffset.UTC)

fun Char.isOpening(): Boolean =
    this == '(' || this == '[' || this == '{' || this == '<'

fun Char.isClosing(): Boolean =
    this == ')' || this == ']' || this == '}' || this == '>'

inline fun String.deleteInside(start: (Char) -> Boolean, end: (Char) -> Boolean): String {
    var inside = false
    return this.filter {
        when {
            start(it) -> inside = true
            end(it) -> {
                inside = false
                return@filter false
            }
        }

        !inside
    }
}

fun String.remove(value: String, ignoreCase: Boolean = false)
    = this.replace(value, "", ignoreCase)

val LocalDateTime.monthsAgo: Long
    get() = ChronoUnit.MONTHS.between(this, LocalDateTime.now())

val LocalDateTime.daysAgo: Long
    get() = ChronoUnit.DAYS.between(this, LocalDateTime.now())

val LocalDateTime.hoursAgo: Long
    get() = ChronoUnit.HOURS.between(this, LocalDateTime.now())

val LocalDateTime.minutesAgo: Long
    get() = ChronoUnit.MINUTES.between(this, LocalDateTime.now())

operator fun LocalDateTime.minus(other: LocalDateTime): Long {
    return minus(other, ChronoUnit.DAYS)
}

fun LocalDateTime.minus(other: LocalDateTime, unit: ChronoUnit = ChronoUnit.DAYS): Long {
    return unit.between(this, other)
}

inline fun <T> retry(times: Int, closure: () -> T?): T? {
    repeat(times) {
        val result = closure()

        if (result != null) {
            return result
        }
    }

    return null
}

fun loadFile(name: String): URL {
    return Thread.currentThread().contextClassLoader.getResource(name)
}
