/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.mail.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import kotlin.reflect.KMutableProperty0

class UiSettings(private val context: Context) {

    private companion object {
        val DEFAULT_THREADS_DENSITY = ThreadsDensity.NORMAL
        val DEFAULT_NIGHT_MODE = NightMode.SYSTEM
        val DEFAULT_COLOR_THEME = ColorTheme.PINK
        val DEFAULT_SWIPE_ACTION = SwipeAction.NONE
        val DEFAULT_EMAILS_DISPLAY_TYPE = EmailsDisplayType.THREADS
        val DEFAULT_EXTERNAL_CONTENT_MODE = ExternalContentMode.ASK_ME
    }

    private fun getUiSettings(): SharedPreferences = context.getSharedPreferences("UISettings", Context.MODE_PRIVATE)

    fun removeUiSettings() = with(getUiSettings().edit()) {
        clear()
        apply()
    }

    //regin Threads density
    private var _threadsDensity: String?
        get() = getPrivateSetting(::threadsDensity, DEFAULT_THREADS_DENSITY)
        set(value) = setPrivateSetting(::threadsDensity, value)

    var threadsDensity: ThreadsDensity
        get() = getSetting(_threadsDensity, DEFAULT_THREADS_DENSITY)
        set(value) {
            _threadsDensity = value.name
        }

    enum class ThreadsDensity(val apiName: String, @StringRes val localisedNameRes: Int) {
        COMPACT("high", R.string.settingsDensityOptionCompact),
        NORMAL("normal", R.string.settingsDensityOptionNormal),
        LARGE("low", R.string.settingsDensityOptionLarge),
    }
    //endregion

    //regin Night mode
    private var _nightMode: String?
        get() = getPrivateSetting(::nightMode, DEFAULT_NIGHT_MODE)
        set(value) = setPrivateSetting(::nightMode, value)

    var nightMode: NightMode
        get() = getSetting(_nightMode, DEFAULT_NIGHT_MODE)
        set(value) {
            _nightMode = value.name
        }

    enum class NightMode(val apiName: String, @StringRes val localisedNameRes: Int, val mode: Int) {
        SYSTEM("medium", R.string.settingsOptionSystemTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", R.string.settingsOptionLightTheme, AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", R.string.settingsOptionDarkTheme, AppCompatDelegate.MODE_NIGHT_YES);
    }
    //endregion

    //region Color theme
    private var _colorTheme: String?
        get() = getPrivateSetting(::colorTheme, DEFAULT_COLOR_THEME)
        set(value) = setPrivateSetting(::colorTheme, value)

    var colorTheme: ColorTheme
        get() = getSetting(_colorTheme, DEFAULT_COLOR_THEME)
        set(value) {
            _colorTheme = value.name
        }

    enum class ColorTheme(@StringRes val localisedNameRes: Int, @StyleRes val themeRes: Int) {
        PINK(R.string.accentColorPinkTitle, R.style.AppTheme_Pink),
        BLUE(R.string.accentColorBlueTitle, R.style.AppTheme_Blue),
    }
    //endregion

    //region Swipe actions
    private var _shortRightSwipe: String?
        get() = getPrivateSetting(::shortRightSwipe, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::shortRightSwipe, value)

    var shortRightSwipe: SwipeAction
        get() = getSetting(_shortRightSwipe, DEFAULT_SWIPE_ACTION)
        set(value) {
            _shortRightSwipe = value.name
        }

    private var _longRightSwipe: String?
        get() = getPrivateSetting(::longRightSwipe, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::longRightSwipe, value)

    var longRightSwipe: SwipeAction
        get() = getSetting(_longRightSwipe, DEFAULT_SWIPE_ACTION)
        set(value) {
            _longRightSwipe = value.name
        }

    private var _shortLeftSwipe: String?
        get() = getPrivateSetting(::shortLeftSwipe, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::shortLeftSwipe, value)

    var shortLeftSwipe: SwipeAction
        get() = getSetting(_shortLeftSwipe, DEFAULT_SWIPE_ACTION)
        set(value) {
            _shortLeftSwipe = value.name
        }

    private var _longLeftSwipe: String?
        get() = getPrivateSetting(::longLeftSwipe, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::longLeftSwipe, value)

    var longLeftSwipe: SwipeAction
        get() = getSetting(_longLeftSwipe, DEFAULT_SWIPE_ACTION)
        set(value) {
            _longLeftSwipe = value.name
        }

    enum class SwipeAction(@IdRes val nameRes: Int) {
        NONE(R.string.settingsSwipeActionNone),
        ARCHIVE(R.string.actionArchive),
        DELETE(R.string.actionDelete),
        FAVORITE(R.string.favoritesFolder),
        MOVE(R.string.actionMove),
        POSTPONE(R.string.actionPostpone),
        QUICKACTIONS_MENU(R.string.settingsSwipeActionQuickActionsMenu),
        READ_AND_ARCHIVE(R.string.settingsSwipeActionReadAndArchive),
        READ_UNREAD(R.string.settingsSwipeActionReadUnread),
        SPAM(R.string.actionSpam),
    }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction? = when (nameRes) {
        R.string.settingsSwipeShortRight -> shortRightSwipe
        R.string.settingsSwipeLongRight -> longRightSwipe
        R.string.settingsSwipeShortLeft -> shortLeftSwipe
        R.string.settingsSwipeLongLeft -> longLeftSwipe
        else -> null
    }

    data class SwipeActions(
        var shortRightSwipe: SwipeAction = SwipeAction.NONE,
        var longRightSwipe: SwipeAction = SwipeAction.NONE,
        var shortLeftSwipe: SwipeAction = SwipeAction.NONE,
        var longLeftSwipe: SwipeAction = SwipeAction.NONE,
    )
    //endregion

    // Emails display type
    private var _emailsDisplayType: String?
        get() = getPrivateSetting(::emailsDisplayType, DEFAULT_EMAILS_DISPLAY_TYPE)
        set(value) = setPrivateSetting(::emailsDisplayType, value)

    var emailsDisplayType: EmailsDisplayType
        get() = getSetting(_emailsDisplayType, DEFAULT_EMAILS_DISPLAY_TYPE)
        set(value) {
            _emailsDisplayType = value.name
        }

    enum class EmailsDisplayType(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        THREADS(1, "on", R.string.settingsOptionDiscussions),
        MESSAGES(0, "off", R.string.settingsOptionMessages),
    }
    //endregion

    // External content mode
    private var _externalContentMode: String?
        get() = getPrivateSetting(::externalContentMode, DEFAULT_EXTERNAL_CONTENT_MODE)
        set(value) = setPrivateSetting(::externalContentMode, value)

    var externalContentMode: ExternalContentMode
        get() = getSetting(_externalContentMode, DEFAULT_EXTERNAL_CONTENT_MODE)
        set(value) {
            _externalContentMode = value.name
        }

    enum class ExternalContentMode(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        ALWAYS(1, "true", R.string.settingsOptionAlways),
        ASK_ME(0, "false", R.string.settingsOptionAskMe),
    }
    //endregion

    //region Utils
    private fun getPrivateSetting(key: KMutableProperty0<*>, enum: Enum<*>): String? {
        return getUiSettings().getString(key.name, enum.name)
    }

    private fun setPrivateSetting(key: KMutableProperty0<*>, value: String?) = with(getUiSettings().edit()) {
        putString(key.name, value)
        apply()
    }

    private inline fun <reified T : Enum<T>> getSetting(enum: String?, default: T): T = enumValueOfOrNull<T>(enum) ?: default
    //endregion
}
