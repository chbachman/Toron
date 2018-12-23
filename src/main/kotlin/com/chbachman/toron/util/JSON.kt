package com.chbachman.toron.util

import com.beust.klaxon.*

val longConverter = object: Converter {
    override fun canConvert(cls: Class<*>)
        = cls == Long::class.java

    override fun fromJson(jv: JsonValue) =
        when {
            jv.longValue != null -> jv.longValue
            jv.double != null -> jv.double?.toLong()
            else -> throw KlaxonException("Couldn't parse long: ${jv.string}")
        }

    override fun toJson(value: Any)
        = "$value"
}

val camelConverter = object: FieldRenamer {
    override fun toJson(fieldName: String) = FieldRenamer.camelToUnderscores(fieldName)
    override fun fromJson(fieldName: String) = FieldRenamer.underscoreToCamel(fieldName)
}

@Target(AnnotationTarget.FIELD)
annotation class FuzzyLong

val klaxon = Klaxon()
    .fieldConverter(FuzzyLong::class, longConverter)
    .fieldRenamer(camelConverter)

inline fun <reified T> String.parseJSON(): T? =
    klaxon.parse<T>(this)
