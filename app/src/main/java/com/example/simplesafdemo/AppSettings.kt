package com.example.simplesafdemo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppSettings {

    private lateinit var sharedPreferences: SharedPreferences
    private val LOCK = Any()

    fun init(context: Context) {
        if (!AppSettings::sharedPreferences.isInitialized) {
            synchronized(LOCK) {
                sharedPreferences =
                    context.getSharedPreferences("simple_saf_demo", Context.MODE_PRIVATE)
            }
        }
    }

    var treeUri: String?
        set(value) {
            sharedPreferences.edit {
                putString(TREE_URI, value)
                commit()
            }
        }
        get() {
            return sharedPreferences.getString(TREE_URI, null)
        }


    private const val TREE_URI = "TREE_URI"
}