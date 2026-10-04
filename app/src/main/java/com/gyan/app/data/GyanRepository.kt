package com.gyan.app.data

import android.content.Context

class GyanRepository private constructor(val dao: GyanDao) {

    companion object {
        @Volatile private var instance: GyanRepository? = null

        fun get(context: Context): GyanRepository =
            instance ?: synchronized(this) {
                instance ?: GyanRepository(GyanDatabase.get(context).dao()).also { instance = it }
            }
    }
}
