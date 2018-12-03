package com.chbachman.toron.api.reddit

import com.chbachman.toron.api.pushshift.PushShift
import okio.buffer
import okio.source
import java.io.File

class RedditCache {

    companion object {
        val folder = File("/Users/Chandler/Desktop/Toron/")
        val data = File(folder, "result")

        val cache = File(folder, "result")

        @JvmStatic
        fun main(args: Array<String>) {
            val original = loadData()
        }

        fun loadData(): List<PushShift> {
            val buffer = data.source().buffer()

            val list = mutableListOf<PushShift>()

            while (!buffer.exhausted()) {
                list.add(PushShift.read(buffer))
            }

            return list
        }
    }
}