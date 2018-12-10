package com.chbachman.toron.util

import java.time.LocalDateTime
import java.time.ZoneOffset

fun Long.toUTCDate(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

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