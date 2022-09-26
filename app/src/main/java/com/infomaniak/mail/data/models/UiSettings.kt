package com.infomaniak.mail.data.models

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R

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

    private var _colorTheme: String?
        get() = getUiSettings().getString("colorTheme", ColorTheme.PINK.name)
        set(value) = with(getUiSettings().edit()) {
            putString("colorTheme", value)
            apply()
        }

    var colorTheme: ColorTheme
        get() = enumValueOfOrNull<ColorTheme>(_colorTheme) ?: ColorTheme.PINK
        set(value) {
            _colorTheme = value.name
        }

    enum class ColorTheme(@StringRes val localisedNameRes: Int) {
        BLUE(R.string.accentColorBlueTitle),
        PINK(R.string.accentColorPinkTitle),
    }
}
