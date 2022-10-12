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
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.transaction
import com.infomaniak.mail.R
import kotlin.reflect.KMutableProperty0

class UiSettings private constructor(context: Context) {

    private val sharedPreferences = context.applicationContext.getSharedPreferences("UISettings", Context.MODE_PRIVATE)

    fun removeUiSettings() = sharedPreferences.transaction { clear() }

    //region Thread density
    private var _threadDensity: String?
        get() = getPrivateSetting(::threadDensity, DEFAULT_THREAD_DENSITY)
        set(value) = setPrivateSetting(::threadDensity, value)

    var threadDensity: ThreadDensity
        get() = getSetting(_threadDensity, DEFAULT_THREAD_DENSITY)
        set(value) {
            _threadDensity = value.name
        }

    enum class ThreadDensity(val apiName: String, @StringRes val localisedNameRes: Int) {
        COMPACT("high", R.string.settingsDensityOptionCompact),
        NORMAL("normal", R.string.settingsDensityOptionNormal),
        LARGE("low", R.string.settingsDensityOptionLarge),
    }
    //endregion

    //region Theme
    private var _theme: String?
        get() = getPrivateSetting(::theme, DEFAULT_THEME)
        set(value) = setPrivateSetting(::theme, value)

    var theme: Theme
        get() = getSetting(_theme, DEFAULT_THEME)
        set(value) {
            _theme = value.name
        }

    enum class Theme(val apiName: String, @StringRes val localisedNameRes: Int, val mode: Int) {
        SYSTEM("medium", R.string.settingsOptionSystemTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", R.string.settingsOptionLightTheme, AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", R.string.settingsOptionDarkTheme, AppCompatDelegate.MODE_NIGHT_YES);
    }
    //endregion

    //region Accent color
    private var _accentColor: String?
        get() = getPrivateSetting(::accentColor, DEFAULT_ACCENT_COLOR)
        set(value) = setPrivateSetting(::accentColor, value)

    var accentColor: AccentColor
        get() = getSetting(_accentColor, DEFAULT_ACCENT_COLOR)
        set(value) {
            _accentColor = value.name
        }

    enum class AccentColor(
        @StringRes val localisedNameRes: Int,
        @StyleRes val theme: Int,
        @ColorRes private val primary: Int,
        @ColorRes private val secondaryBackground: Int,
        @ColorRes private val ripple: Int,
        val introTabIndex: Int,
    ) {
        PINK(
            R.string.accentColorPinkTitle,
            R.style.AppTheme_Pink,
            R.color.pinkMail,
            R.color.pinkBoardingSecondaryBackground,
            R.color.pinkMailRipple,
            0,
        ),
        BLUE(
            R.string.accentColorBlueTitle,
            R.style.AppTheme_Blue,
            R.color.blueMail,
            R.color.blueBoardingSecondaryBackground,
            R.color.blueMailRipple,
            1,
        );

        fun getPrimary(context: Context): Int = context.getColor(primary)

        fun getSecondaryBackground(context: Context): Int = context.getColor(secondaryBackground)

        fun getRipple(context: Context): Int = context.getColor(ripple)
    }
    //endregion

    //region Swipe actions
    private var _swipeShortRight: String?
        get() = getPrivateSetting(::swipeShortRight, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::swipeShortRight, value)

    var swipeShortRight: SwipeAction
        get() = getSetting(_swipeShortRight, DEFAULT_SWIPE_ACTION)
        set(value) {
            _swipeShortRight = value.name
        }

    private var _swipeLongRight: String?
        get() = getPrivateSetting(::swipeLongRight, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::swipeLongRight, value)

    var swipeLongRight: SwipeAction
        get() = getSetting(_swipeLongRight, DEFAULT_SWIPE_ACTION)
        set(value) {
            _swipeLongRight = value.name
        }

    private var _swipeShortLeft: String?
        get() = getPrivateSetting(::swipeShortLeft, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::swipeShortLeft, value)

    var swipeShortLeft: SwipeAction
        get() = getSetting(_swipeShortLeft, DEFAULT_SWIPE_ACTION)
        set(value) {
            _swipeShortLeft = value.name
        }

    private var _swipeLongLeft: String?
        get() = getPrivateSetting(::swipeLongLeft, DEFAULT_SWIPE_ACTION)
        set(value) = setPrivateSetting(::swipeLongLeft, value)

    var swipeLongLeft: SwipeAction
        get() = getSetting(_swipeLongLeft, DEFAULT_SWIPE_ACTION)
        set(value) {
            _swipeLongLeft = value.name
        }

    enum class SwipeAction(@StringRes val nameRes: Int) {
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
        R.string.settingsSwipeShortRight -> swipeShortRight
        R.string.settingsSwipeLongRight -> swipeLongRight
        R.string.settingsSwipeShortLeft -> swipeShortLeft
        R.string.settingsSwipeLongLeft -> swipeLongLeft
        else -> null
    }
    //endregion

    //region Thread mode
    private var _threadMode: String?
        get() = getPrivateSetting(::threadMode, DEFAULT_THREAD_MODE)
        set(value) = setPrivateSetting(::threadMode, value)

    var threadMode: ThreadMode
        get() = getSetting(_threadMode, DEFAULT_THREAD_MODE)
        set(value) {
            _threadMode = value.name
        }

    enum class ThreadMode(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        THREADS(1, "on", R.string.settingsOptionDiscussions),
        MESSAGES(0, "off", R.string.settingsOptionMessages),
    }
    //endregion

    //region External content
    private var _externalContent: String?
        get() = getPrivateSetting(::externalContent, DEFAULT_EXTERNAL_CONTENT)
        set(value) = setPrivateSetting(::externalContent, value)

    var externalContent: ExternalContent
        get() = getSetting(_externalContent, DEFAULT_EXTERNAL_CONTENT)
        set(value) {
            _externalContent = value.name
        }

    enum class ExternalContent(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        ALWAYS(1, "true", R.string.settingsOptionAlways),
        ASK_ME(0, "false", R.string.settingsOptionAskMe),
    }
    //endregion

    //region Utils
    private fun getPrivateSetting(key: KMutableProperty0<*>, enum: Enum<*>): String? {
        return sharedPreferences.getString(key.name, enum.name)
    }

    private fun setPrivateSetting(keyClass: KMutableProperty0<*>, value: String?) {
        sharedPreferences.transaction { putString(keyClass.name, value) }
    }

    private inline fun <reified T : Enum<T>> getSetting(enum: String?, default: T): T = enumValueOfOrNull<T>(enum) ?: default
    //endregion

    companion object {
        val DEFAULT_ACCENT_COLOR = AccentColor.PINK
        private val DEFAULT_EXTERNAL_CONTENT = ExternalContent.ASK_ME
        private val DEFAULT_SWIPE_ACTION = SwipeAction.NONE
        private val DEFAULT_THEME = Theme.SYSTEM
        private val DEFAULT_THREAD_DENSITY = ThreadDensity.NORMAL
        private val DEFAULT_THREAD_MODE = ThreadMode.THREADS

        @Volatile
        private var INSTANCE: UiSettings? = null

        fun getInstance(context: Context): UiSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }
                UiSettings(context).also { INSTANCE = it }
            }
        }
    }
}
