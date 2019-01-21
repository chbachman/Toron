@file:Suppress("UNCHECKED_CAST")

package com.chbachman.toron.serial

import com.chbachman.toron.homeDir
import org.dizitart.kno2.nitrite
import org.dizitart.no2.Nitrite
import org.dizitart.no2.objects.ObjectRepository
import org.mapdb.*
import java.io.File
import kotlin.reflect.full.companionObjectInstance

const val mainDBName = "toron-nitrite.db"

val databaseDir = File(homeDir, "database")
val mainDBFile = File(databaseDir, mainDBName)
val mainDB = createDB(mainDBName)

inline fun <reified T> repo(db: Nitrite = mainDB): ObjectRepository<T> = db.getRepository(T::class.java)

fun createDB(name: String): Nitrite =
    nitrite {
        file = File(databaseDir, name)
    }

fun createDB(name: File): Nitrite =
    nitrite {
        file = name
    }

