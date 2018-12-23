package com.chbachman.toron.util

import com.beust.klaxon.FieldRenamer
import com.beust.klaxon.Klaxon

val klaxon = Klaxon().fieldRenamer(object: FieldRenamer {
    override fun toJson(fieldName: String) = FieldRenamer.camelToUnderscores(fieldName)
    override fun fromJson(fieldName: String) = FieldRenamer.underscoreToCamel(fieldName)
})

inline fun <reified T> String.parseJSON(): T? =
    klaxon.parse<T>(this)