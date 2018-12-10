@file:Suppress("UNCHECKED_CAST")

package com.chbachman.toron.serial

import com.chbachman.toron.homeDir
import org.mapdb.*
import java.io.File
import kotlin.reflect.full.companionObjectInstance

val databaseDir = File(homeDir, "database")

val mainDB = createDB("toron.db")

inline fun <reified T> dbList(name: String, db: DB = mainDB): IndexTreeList<T> {
    val serial = T::class.companionObjectInstance as Serializer<T>
    return db.indexTreeList(name, serial).createOrOpen()
}

inline fun <reified T> dbSet(name: String, db: DB = mainDB): HTreeMap.KeySet<T> {
    val serial = T::class.companionObjectInstance as Serializer<T>
    return db.hashSet(name, serial).createOrOpen()
}

inline fun <reified T, reified K> dbMap(name: String, db: DB = mainDB): HTreeMap<T, K> {
    val serialT = T::class.companionObjectInstance as Serializer<T>
    val serialK = K::class.companionObjectInstance as Serializer<K>
    return db.hashMap(name, serialT, serialK).createOrOpen()
}

inline fun <T> transaction(db: DB = mainDB, closure: () -> T): T {
    val temp = closure()
    db.commit()
    return temp
}

fun createDB(name: String): DB =
    DBMaker
        .fileDB(File(databaseDir, name))
        .transactionEnable()
        .fileMmapEnableIfSupported()
        .fileMmapPreclearDisable()
        .closeOnJvmShutdown()
        .make()