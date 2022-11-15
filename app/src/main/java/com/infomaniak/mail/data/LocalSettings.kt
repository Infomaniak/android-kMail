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

class LocalSettings private constructor(context: Context) {

    private val sharedPreferences = context.applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    fun removeSettings() = sharedPreferences.transaction { clear() }

    //region Cancel delay
    var cancelDelay: Int
        get() = sharedPreferences.getInt(CANCEL_DELAY_KEY, DEFAULT_CANCEL_DELAY)
        set(value) = sharedPreferences.transaction { putInt(CANCEL_DELAY_KEY, value) }
    //endregion

    //region Email forwarding
    var emailForwarding: EmailForwarding
        get() = getEnum(EMAIL_FORWARDING_KEY, DEFAULT_EMAIL_FORWARDING)
        set(value) = putEnum(EMAIL_FORWARDING_KEY, value)

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
    var threadDensity: ThreadDensity
        get() = getEnum(THREAD_DENSITY_KEY, DEFAULT_THREAD_DENSITY)
        set(value) = putEnum(THREAD_DENSITY_KEY, value)

    enum class ThreadDensity(@StringRes val localisedNameRes: Int) {
        COMPACT(R.string.settingsDensityOptionCompact),
        NORMAL(R.string.settingsDensityOptionNormal),
        LARGE(R.string.settingsDensityOptionLarge),
    }
    //endregion

    //region Theme
    var theme: Theme
        get() = getEnum(THEME_KEY, DEFAULT_THEME)
        set(value) = putEnum(THEME_KEY, value)

    enum class Theme(@StringRes val localisedNameRes: Int, val mode: Int) {
        SYSTEM(R.string.settingsOptionSystemTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(R.string.settingsOptionLightTheme, AppCompatDelegate.MODE_NIGHT_NO),
        DARK(R.string.settingsOptionDarkTheme, AppCompatDelegate.MODE_NIGHT_YES);
    }
    //endregion

    //region Accent color
    var accentColor: AccentColor
        get() = getEnum(ACCENT_COLOR_KEY, DEFAULT_ACCENT_COLOR)
        set(value) = putEnum(ACCENT_COLOR_KEY, value)

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
    var swipeRight: SwipeAction
        get() = getEnum(SWIPE_RIGHT_KEY, DEFAULT_SWIPE_ACTION)
        set(value) = putEnum(SWIPE_RIGHT_KEY, value)

    var swipeLeft: SwipeAction
        get() = getEnum(SWIPE_LEFT_KEY, DEFAULT_SWIPE_ACTION)
        set(value) = putEnum(SWIPE_LEFT_KEY, value)

    enum class SwipeAction(@StringRes val nameRes: Int, @ColorRes private val colorRes: Int, @DrawableRes val iconRes: Int?) {
        DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin),
        ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder),
        READ_UNREAD(R.string.settingsSwipeActionReadUnread, R.color.swipeReadUnread, R.drawable.ic_envelope),
        MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move),
        FAVORITE(R.string.favoritesFolder, R.color.swipeFavorite, R.drawable.ic_star),
        POSTPONE(R.string.actionPostpone, R.color.swipePostpone, R.drawable.ic_alarm_clock),
        SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam),
        READ_AND_ARCHIVE(R.string.settingsSwipeActionReadAndArchive, R.color.swipeReadAndArchive, R.drawable.ic_drawer_mailbox),
        QUICKACTIONS_MENU(R.string.settingsSwipeActionQuickActionsMenu, R.color.swipeQuickActionMenu, R.drawable.ic_param_dots),
        NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null);

        @ColorInt
        fun getBackgroundColor(context: Context): Int {
            return context.getColor(colorRes)
        }
    }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction = when (nameRes) {
        R.string.settingsSwipeRight -> swipeRight
        R.string.settingsSwipeLeft -> swipeLeft
        else -> throw IllegalArgumentException()
    }
    //endregion

    //region Thread mode
    var threadMode: ThreadMode
        get() = getEnum(THREAD_MODE_KEY, DEFAULT_THREAD_MODE)
        set(value) = putEnum(THREAD_MODE_KEY, value)

    enum class ThreadMode(val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        THREADS("on", R.string.settingsOptionDiscussions),
        MESSAGES("off", R.string.settingsOptionMessages),
    }
    //endregion

    //region External content
    var externalContent: ExternalContent
        get() = getEnum(EXTERNAL_CONTENT_KEY, DEFAULT_EXTERNAL_CONTENT)
        set(value) = putEnum(EXTERNAL_CONTENT_KEY, value)

    enum class ExternalContent(val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        ALWAYS("true", R.string.settingsOptionAlways),
        ASK_ME("false", R.string.settingsOptionAskMe),
    }
    //endregion

    //region Utils
    private inline fun <reified T : Enum<T>> getEnum(key: String, default: T): T {
        return enumValueOfOrNull<T>(sharedPreferences.getString(key, default.name)) ?: default
    }

    private fun putEnum(key: String, value: Enum<*>) {
        sharedPreferences.transaction { putString(key, value.name) }
    }
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
        private const val SHARED_PREFS_NAME = "LocalSettingsSharedPref"
        private const val CANCEL_DELAY_KEY = "cancelDelayKey"
        private const val EMAIL_FORWARDING_KEY = "emailForwardingKey"
        private const val INCLUDE_MESSAGE_IN_REPLY_KEY = "includeMessageInReplyKey"
        private const val ASK_EMAIL_ACKNOWLEDGMENT_KEY = "askEmailAcknowledgmentKey"
        private const val IS_APP_LOCKED_KEY = "isAppLockedKey"
        private const val THREAD_DENSITY_KEY = "threadDensityKey"
        private const val THEME_KEY = "themeKey"
        private const val ACCENT_COLOR_KEY = "accentColorKey"
        private const val SWIPE_RIGHT_KEY = "swipeRightKey"
        private const val SWIPE_LEFT_KEY = "swipeLeftKey"
        private const val THREAD_MODE_KEY = "threadModeKey"
        private const val EXTERNAL_CONTENT_KEY = "externalContentKey"
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
