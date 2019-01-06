package com.chbachman.toron.util

import com.beust.klaxon.*
import com.chbachman.toron.api.anilist.MediaTitle

val longConverter = object: Converter {
    override fun canConvert(cls: Class<*>)
        = cls == Long::class.java

    override fun fromJson(jv: JsonValue) =
        when {
            jv.longValue != null -> jv.longValue
            jv.double != null -> jv.double?.toLong()
            jv.int != null -> jv.int?.toLong()
            jv.float != null -> jv.float?.toLong()
            else -> throw KlaxonException("Couldn't parse long: $jv")
        }

    override fun toJson(value: Any)
        = "$value"
}

val intConverter = object: Converter {
    override fun canConvert(cls: Class<*>)
        = cls == Int::class.java

    override fun fromJson(jv: JsonValue) =
        when {
            jv.int != null -> jv.int
            jv.genericType == null -> 0
            else -> throw KlaxonException("Couldn't parse int: ${jv.string}")
        }

    override fun toJson(value: Any)
        = "$value"
}

val camelConverter = object: FieldRenamer {
    override fun toJson(fieldName: String) = FieldRenamer.camelToUnderscores(fieldName)
    override fun fromJson(fieldName: String) = FieldRenamer.underscoreToCamel(fieldName)
}

val capitalConverter = object: FieldRenamer {
    override fun toJson(fieldName: String) = fieldName.capitalize()
    override fun fromJson(fieldName: String) = fieldName.decapitalize()
}

val mediaTitleConverter = object: Converter {
    override fun canConvert(cls: Class<*>) =
        cls == MediaTitle::class.java

    override fun toJson(value: Any): String =
        """

        """.trimIndent()

    override fun fromJson(jv: JsonValue): MediaTitle {
        val obj = jv.obj ?: throw KlaxonException("Couldn't parse media title: ${jv.string}")

        val romaji = obj.string("romaji")
            ?: throw KlaxonException("Romaji must not be null: ${jv.string}")
        val native = obj.string("native") ?: romaji
        val english = obj.string("english") ?: romaji

        return MediaTitle(
            romaji, native, english
        )
    }

}

@Target(AnnotationTarget.FIELD)
annotation class FuzzyLong

val klaxon = Klaxon()
    .fieldConverter(FuzzyLong::class, longConverter)
    .converter(intConverter)
    .converter(mediaTitleConverter)

val klaxonCamel = Klaxon()
    .fieldConverter(FuzzyLong::class, longConverter)
    .converter(intConverter)
    .converter(mediaTitleConverter)
    .fieldRenamer(camelConverter)

inline fun <reified T> String.parseJSONCamel(): T? =
    klaxonCamel.parse<T>(this)

inline fun <reified T> String.parseJSON(): T? =
    klaxon.parse<T>(this)
