@file:Suppress("UNCHECKED_CAST")

package com.chbachman.toron.serial

import com.chbachman.toron.homeDir
import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.IndexTreeList
import org.mapdb.Serializer
import java.io.File
import kotlin.reflect.full.companionObjectInstance

val databaseDir = File(homeDir, "database")

val db = DBMaker
    .fileDB(File(databaseDir, "toron.db"))
    .transactionEnable()
    .fileMmapEnableIfSupported()
    .fileMmapPreclearDisable()
    .closeOnJvmShutdown()
    .make()

interface Serializable

inline fun <reified T> dbList(name: String): IndexTreeList<T> {
    val serial = T::class.companionObjectInstance as Serializer<T>
    return db.indexTreeList(name, serial).createOrOpen()
}

inline fun <reified T> dbSet(name: String): HTreeMap.KeySet<T> {
    val serial = T::class.companionObjectInstance as Serializer<T>
    return db.hashSet(name, serial).createOrOpen()
}

inline fun <reified T, reified K> dbMap(name: String): HTreeMap<T, K> {
    val serialT = T::class.companionObjectInstance as Serializer<T>
    val serialK = K::class.companionObjectInstance as Serializer<K>
    return db.hashMap(name, serialT, serialK).createOrOpen()
}

inline fun <T> transaction(closure: () -> T): T {
    val temp = closure()
    db.commit()
    return temp
}