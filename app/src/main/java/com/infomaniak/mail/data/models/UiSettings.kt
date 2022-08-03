package com.infomaniak.mail.data.models

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class UiSettings(private val context: Context) {

    private fun getUiSettings(): SharedPreferences = context.getSharedPreferences("UISettings", Context.MODE_PRIVATE)

    fun removeUiSettings() = with(getUiSettings().edit()) {
        clear()
        apply()
    }

    var nightMode: Int
        get() = getUiSettings().getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = with(getUiSettings().edit()) {
            putInt("nightMode", value)
            apply()
        }
}
