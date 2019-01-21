package com.chbachman.toron.util

import org.dizitart.kno2.filters.and
import org.dizitart.kno2.filters.eq
import org.dizitart.kno2.filters.or
import org.dizitart.no2.Document
import org.dizitart.no2.NitriteId
import org.dizitart.no2.objects.ObjectFilter
import org.dizitart.no2.objects.ObjectRepository
import org.dizitart.no2.objects.filters.BaseObjectFilter
import org.dizitart.no2.objects.filters.ObjectFilters
import org.dizitart.no2.store.NitriteMap
import org.dizitart.no2.util.DocumentUtils.getFieldValue
import org.dizitart.no2.util.ValidationUtils.validateSearchTerm
import kotlin.reflect.KProperty

class CustomObjectFilter<T>(
    val field: String,
    val function: (T) -> Boolean
): BaseObjectFilter() {
    @Suppress("UNCHECKED_CAST")
    override fun apply(documentMap: NitriteMap<NitriteId, Document>?): MutableSet<NitriteId> {
        documentMap ?: return mutableSetOf()

        return documentMap.entrySet().filter { (_, document) ->
            val obj = getFieldValue(document, field) as T

            function(obj)
        }.map { it.key }.toMutableSet()
    }
}

inline infix fun <reified T> KProperty<T>.custom(noinline function: (T) -> Boolean): ObjectFilter {
    return CustomObjectFilter(this.name, function)
}

inline infix fun <reified T> KProperty<T?>.neq(value: T?): ObjectFilter =
    ObjectFilters.not(ObjectFilters.eq(this.name, value))

inline fun <reified T> ObjectRepository<T>.any(vararg filters: ObjectFilter) =
    this.find(ObjectFilters.or(*filters))!!

inline fun <reified T> ObjectRepository<T>.all(vararg filters: ObjectFilter) =
    this.find(ObjectFilters.and(*filters))!!

fun <T> ObjectRepository<T>.upsert(collection: Collection<T>) =
    collection.forEach { update(it, true) }

fun <T> ObjectRepository<T>.insert(collection: Collection<T>) =
    collection.forEach { insert(it) }

fun <T> ObjectRepository<T>.update(collection: Collection<T>) =
    collection.forEach { update(it) }

