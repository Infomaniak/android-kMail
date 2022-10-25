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
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.transaction
import com.infomaniak.mail.R
import kotlin.reflect.KMutableProperty0

class LocalSettings private constructor(context: Context) {

    private val sharedPreferences = context.applicationContext.getSharedPreferences("UISettings", Context.MODE_PRIVATE)

    fun removeUiSettings() = sharedPreferences.transaction { clear() }

    //region Cancel delay
    var cancelDelay: Int
        get() = sharedPreferences.getInt(CANCEL_DELAY_KEY, DEFAULT_CANCEL_DELAY)
        set(value) = sharedPreferences.transaction { putInt(CANCEL_DELAY_KEY, value) }
    //endregion

    //region Email forwarding
    private var _emailForwarding: String?
        get() = getPrivateSetting(::emailForwarding, DEFAULT_EMAIL_FORWARDING)
        set(value) = setPrivateSetting(::emailForwarding, value)

    var emailForwarding: EmailForwarding
        get() = getSetting(_emailForwarding, DEFAULT_EMAIL_FORWARDING)
        set(value) {
            _emailForwarding = value.name
        }

    enum class EmailForwarding(@StringRes val localisedNameRes: Int) {
        IN_BODY(R.string.settingsTransferInBody),
        AS_ATTACHMENT(R.string.settingsTransferAsAttachment),
    }
    //endregion

    //region Include original message in reply
    var includeMessageInReply: Boolean
        get() = sharedPreferences.getBoolean(INCLUDE_MESSAGE_IN_REPLY_KEY, DEFAULT_INCLUDE_MESSAGE_IN_REPLY)
        set(value) = sharedPreferences.transaction { putBoolean(INCLUDE_MESSAGE_IN_REPLY_KEY, value) }
    //endregion

    //region Ask email acknowledgment
    var askEmailAcknowledgement: Boolean
        get() = sharedPreferences.getBoolean(ASK_EMAIL_ACKNOWLEDGMENT_KEY, DEFAULT_ASK_EMAIL_ACKNOWLEDGMENT)
        set(value) = sharedPreferences.transaction { putBoolean(ASK_EMAIL_ACKNOWLEDGMENT_KEY, value) }
    //endregion

    //region App lock
    var isAppLocked: Boolean
        get() = sharedPreferences.getBoolean(IS_APP_LOCKED_KEY, DEFAULT_IS_APP_LOCKED)
        set(value) = sharedPreferences.transaction { putBoolean(IS_APP_LOCKED_KEY, value) }
    //endregion

    //region Thread density
    private var _threadDensity: String?
        get() = getPrivateSetting(::threadDensity, DEFAULT_THREAD_DENSITY)
        set(value) = setPrivateSetting(::threadDensity, value)

    var threadDensity: ThreadDensity
        get() = getSetting(_threadDensity, DEFAULT_THREAD_DENSITY)
        set(value) {
            _threadDensity = value.name
        }

    enum class ThreadDensity(@StringRes val localisedNameRes: Int) {
        COMPACT(R.string.settingsDensityOptionCompact),
        NORMAL(R.string.settingsDensityOptionNormal),
        LARGE(R.string.settingsDensityOptionLarge),
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

    enum class Theme(@StringRes val localisedNameRes: Int, val mode: Int) {
        SYSTEM(R.string.settingsOptionSystemTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(R.string.settingsOptionLightTheme, AppCompatDelegate.MODE_NIGHT_NO),
        DARK(R.string.settingsOptionDarkTheme, AppCompatDelegate.MODE_NIGHT_YES);
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

    enum class SwipeAction(@StringRes val nameRes: Int, @ColorRes private val colorRes: Int, @DrawableRes val iconRes: Int?) {
        DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin),
        ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder),
        READ_UNREAD(R.string.settingsSwipeActionReadUnread, R.color.swipeReadUnread, R.drawable.ic_envelope),
        MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move),
        FAVORITE(R.string.favoritesFolder, R.color.swipeFavorite, R.drawable.ic_star),
        POSTPONE(R.string.actionPostpone, R.color.swipePostpone, R.drawable.ic_alarm_clock),
        SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam),
        READ_AND_ARCHIVE(R.string.settingsSwipeActionReadAndArchive, R.color.swipeNone, null), // TODO
        QUICKACTIONS_MENU(R.string.settingsSwipeActionQuickActionsMenu, R.color.swipeQuickActionMenu, R.drawable.ic_param_dots),
        NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null);

        @ColorInt
        fun getBackgroundColor(context: Context): Int {
            return context.getColor(colorRes)
        }
    }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction = when (nameRes) {
        R.string.settingsSwipeShortRight -> swipeShortRight
        R.string.settingsSwipeLongRight -> swipeLongRight
        R.string.settingsSwipeShortLeft -> swipeShortLeft
        R.string.settingsSwipeLongLeft -> swipeLongLeft
        else -> throw IllegalArgumentException()
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

    enum class ThreadMode(val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        THREADS("on", R.string.settingsOptionDiscussions),
        MESSAGES("off", R.string.settingsOptionMessages),
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

    enum class ExternalContent(val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        ALWAYS("true", R.string.settingsOptionAlways),
        ASK_ME("false", R.string.settingsOptionAskMe),
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

        //region Default values
        private const val DEFAULT_CANCEL_DELAY = 10
        private val DEFAULT_EMAIL_FORWARDING = EmailForwarding.IN_BODY
        private const val DEFAULT_INCLUDE_MESSAGE_IN_REPLY = true
        private const val DEFAULT_ASK_EMAIL_ACKNOWLEDGMENT = false

        private const val DEFAULT_IS_APP_LOCKED = false

        private val DEFAULT_THREAD_DENSITY = ThreadDensity.NORMAL
        private val DEFAULT_THEME = Theme.SYSTEM
        val DEFAULT_ACCENT_COLOR = AccentColor.PINK
        private val DEFAULT_SWIPE_ACTION = SwipeAction.NONE
        private val DEFAULT_THREAD_MODE = ThreadMode.THREADS
        private val DEFAULT_EXTERNAL_CONTENT = ExternalContent.ASK_ME
        //endregion

        //region Keys
        private const val CANCEL_DELAY_KEY = "cancelDelay"
        private const val INCLUDE_MESSAGE_IN_REPLY_KEY = "includeMessageInReply"
        private const val ASK_EMAIL_ACKNOWLEDGMENT_KEY = "askEmailAcknowledgment"
        private const val IS_APP_LOCKED_KEY = "isAppLocked"
        //endregion

        @Volatile
        private var INSTANCE: LocalSettings? = null

        fun getInstance(context: Context): LocalSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }
                LocalSettings(context).also { INSTANCE = it }
            }
        }
    }
}
