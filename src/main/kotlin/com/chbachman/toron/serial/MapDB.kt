@file:Suppress("UNCHECKED_CAST")

package com.chbachman.toron.serial

import com.chbachman.toron.homeDir
import org.mapdb.*
import java.io.File
import kotlin.reflect.full.companionObjectInstance

val databaseDir = File(homeDir, "database")

val mainDB = createDB("toron.db")

inline fun <reified T> dbList(name: String, db: DB = mainDB): IndexTreeList<T> =
    db.indexTreeList(name, serializer<T>()).createOrOpen()

inline fun <reified T> dbSet(name: String, db: DB = mainDB): HTreeMap.KeySet<T> =
    db.hashSet(name, serializer<T>()).createOrOpen()

inline fun <reified T, reified K> dbMap(name: String, db: DB = mainDB): HTreeMap<T, K> =
    db.hashMap(name, serializer<T>(), serializer<K>()).createOrOpen()

inline fun <T> transaction(db: DB = mainDB, closure: () -> T): T {
    val temp = closure()
    db.commit()
    return temp
}

inline fun <reified T> serializer(): Serializer<T> {
    return when (T::class) {
        String::class -> Serializer.STRING
        Long::class -> Serializer.LONG
        else -> T::class.companionObjectInstance
    } as Serializer<T>
}

fun createDB(name: String): DB =
    DBMaker
        .fileDB(File(databaseDir, name))
        .transactionEnable()
        .fileMmapEnableIfSupported()
        .fileMmapPreclearDisable()
        .closeOnJvmShutdown()
        .make()