package com.chbachman.toron

import com.chbachman.toron.jedis.closeDB
import com.chbachman.toron.jedis.transaction
import com.chbachman.toron.util.anilistShows

class Migration {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            transaction {
                val anilist = anilistShows()

                anilist.scanValuesGroup { posts ->
                    set(posts.map { it.id to it })
                }
            }

            closeDB()
        }
    }
}