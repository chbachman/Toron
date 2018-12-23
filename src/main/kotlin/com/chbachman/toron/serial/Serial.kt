package com.chbachman.toron.serial

import okio.BufferedSink
import okio.BufferedSource
import okio.utf8Size
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure

data class TypeData(
    val name: String,
    val value: Any?,
    val type: KType
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Serial(
    val ignored: Boolean
) {
    companion object {
        private fun BufferedSink.write(s: String): BufferedSink {
            return writeLong(s.utf8Size()).writeUtf8(s)
        }

        private fun BufferedSink.write(b: Boolean): BufferedSink {
            return writeByte(if (b) 1 else 0)
        }

        private fun BufferedSource.read(): String {
            return readUtf8(readLong())
        }

        private const val zeroByte: Byte = 0
        private fun BufferedSource.readBoolean(): Boolean {
            return readByte() != zeroByte
        }

        private val KProperty1<*, *>.ignored
        get() = findAnnotation<Serial>()?.ignored ?: false

        @Suppress("UNCHECKED_CAST")
        fun <K: Any> write(sink: BufferedSink, value: K?, type: KType) {
            if (type.isMarkedNullable) {
                sink.write(value != null)
            }

            if (value == null) return

            when(type.jvmErasure) {
                Boolean::class -> sink.write(value as Boolean)
                Int::class -> sink.writeInt(value as Int)
                String::class -> sink.write(value as String)
                Long::class -> sink.writeLong(value as Long)
                Double::class -> sink.writeLong((value as Double).toBits())
                Float::class -> sink.writeInt((value as Float).toBits())
                List::class -> {
                    val listType = type.arguments.first().type!!
                    val list = value as List<*>
                    sink.writeInt(list.size)
                    list.forEach { writeGeneric(sink, it, listType, listType.jvmErasure) }
                }
                Pair::class -> {
                    val (firstType, secondType) = type.arguments.map { it.type!! }
                    val pair = value as Pair<*, *>

                    writeGeneric(sink, pair.first, firstType, firstType.jvmErasure)
                    writeGeneric(sink, pair.second, secondType, secondType.jvmErasure)
                }
                else -> {
                    writeObj(sink, value, type.jvmErasure as KClass<K>)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T: Any> writeGeneric(sink: BufferedSink, obj: Any?, type: KType, clazz: KClass<T>) {
            write(sink, obj as T?, type)
        }

        private fun <T: Any> writeObj(sink: BufferedSink, element: T, type: KClass<T>) {
            val fields = type.declaredMemberProperties.sortedBy { it.name }

            fields.filterNot {
                it.ignored
            }.forEach { field ->
                write(sink, field.get(element), field.returnType)
            }
        }

        fun read(source: BufferedSource, type: KType): Any? {
            val isNull = type.isMarkedNullable && !source.readBoolean()
            if (isNull) return null

            return when(type.jvmErasure) {
                Int::class -> source.readInt()
                String::class -> source.read()
                Boolean::class -> source.readBoolean()
                Long::class -> source.readLong()
                Double::class -> Double.fromBits(source.readLong())
                Float::class -> Float.fromBits(source.readInt())
                List::class -> {
                    val size = source.readInt()

                    List(size) {
                        read(source, type.arguments.first().type!!)
                    }
                }
                Pair::class -> {
                    val (firstType, secondType) = type.arguments.map { it.type!! }
                    read(source, firstType) to read(source, secondType)
                }
                else -> readObj(source, type.jvmErasure)
            }
        }

        private fun <T: Any> readObj(source: BufferedSource, type: KClass<T>): T {
            fun equals(value: TypeData, parameter: KParameter): Boolean {
                return value.name == parameter.name &&
                    value.type == parameter.type
            }

            val fields = type.declaredMemberProperties
                .sortedBy { it.name }
                .filterNot { it.ignored }

            val data = fields.map { field ->
                TypeData(field.name, read(source, field.returnType), field.returnType)
            }

            val constructor = type.constructors.firstOrNull { constructor ->
                constructor.parameters.all { parameter ->
                    data.any { equals(it, parameter) }
                }
            } ?: error("Could not find a suitable constructor.")

            val args = constructor.parameters.map { parameter ->
                parameter to data.find { equals(it, parameter) }!!.value
            }.toMap()

            return constructor.callBy(args)
        }

        inline fun <reified T: Any> writeList(sink: BufferedSink, element: List<T>) {
            element.forEach { write(sink, it, T::class.createType()) }
        }

        inline fun <reified T: Any> readList(source: BufferedSource): List<T> {
            val list = mutableListOf<T>()

            while (!source.exhausted()) {
                list += read(source, T::class.createType()) as T
            }

            return list
        }

        inline fun <reified T: Any> write(sink: BufferedSink, element: T) {
            write(sink, element, T::class.createType())
        }

        inline fun <reified T: Any> read(source: BufferedSource): T {
            return read(source, T::class.createType()) as T
        }
    }
}