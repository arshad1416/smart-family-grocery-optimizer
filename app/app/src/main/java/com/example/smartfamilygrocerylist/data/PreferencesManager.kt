package com.example.smartfamilygrocerylist.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_grocery_prefs", Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun saveDouble(key: String, value: Double) {
        prefs.edit().putFloat(key, value.toFloat()).apply()
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return prefs.getFloat(key, defaultValue.toFloat()).toDouble()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
