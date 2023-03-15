/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import android.util.Log
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.api.ApiController.json
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.lib.core.utils.transaction
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_POSTPONE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.R
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

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
        LARGE(R.string.settingsDensityOptionLarge);

        override fun toString() = name.lowercase()
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

        override fun toString() = name.lowercase()
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
        ),
        SYSTEM(
            R.string.settingsOptionSystemTheme,
            -1,
            R.color.secondaryTextColor,
            R.color.backgroundWaveSystemColor,
            R.color.backgroundWaveSystemColor,
            2,
        );

        fun getPrimary(context: Context): Int = context.getColor(primary)

        fun getSecondaryBackground(context: Context): Int = context.getColor(secondaryBackground)

        fun getRipple(context: Context): Int = context.getColor(ripple)

        override fun toString() = name.lowercase()
    }
    //endregion

    //region Swipe actions
    var swipeRight: SwipeAction
        get() = getEnum(SWIPE_RIGHT_KEY, INITIAL_SWIPE_ACTION)
        set(value) = putEnum(SWIPE_RIGHT_KEY, value)

    var swipeLeft: SwipeAction
        get() = getEnum(SWIPE_LEFT_KEY, INITIAL_SWIPE_ACTION)
        set(value) = putEnum(SWIPE_LEFT_KEY, value)

    enum class SwipeAction(
        @StringRes val nameRes: Int,
        @ColorRes val colorRes: Int,
        @DrawableRes val iconRes: Int?,
        val matomoValue: String,
    ) {
        DELETE(R.string.actionDelete, R.color.swipeDelete, R.drawable.ic_bin, ACTION_DELETE_NAME),
        ARCHIVE(R.string.actionArchive, R.color.swipeArchive, R.drawable.ic_archive_folder, ACTION_ARCHIVE_NAME),
        READ_UNREAD(
            R.string.settingsSwipeActionReadUnread,
            R.color.swipeReadUnread,
            R.drawable.ic_envelope,
            ACTION_MARK_AS_SEEN_NAME,
        ),
        MOVE(R.string.actionMove, R.color.swipeMove, R.drawable.ic_email_action_move, ACTION_MOVE_NAME),
        FAVORITE(R.string.actionShortStar, R.color.swipeFavorite, R.drawable.ic_star, ACTION_FAVORITE_NAME),
        POSTPONE(R.string.actionPostpone, R.color.swipePostpone, R.drawable.ic_alarm_clock, ACTION_POSTPONE_NAME),
        SPAM(R.string.actionSpam, R.color.swipeSpam, R.drawable.ic_spam, ACTION_SPAM_NAME),
        QUICKACTIONS_MENU(
            R.string.settingsSwipeActionQuickActionsMenu,
            R.color.swipeQuickActionMenu,
            R.drawable.ic_param_dots,
            "quickActions",
        ),
        TUTORIAL(R.string.settingsSwipeActionNone, R.color.progressbarTrackColor, null, "tutorial"),
        NONE(R.string.settingsSwipeActionNone, R.color.swipeNone, null, "none");

        @ColorInt
        fun getBackgroundColor(context: Context): Int = context.getColor(colorRes)
    }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction = when (nameRes) {
        R.string.settingsSwipeRight -> swipeRight
        R.string.settingsSwipeLeft -> swipeLeft
        else -> throw IllegalArgumentException()
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

    //region Recent searches
    var recentSearches: List<String>
        get() = json.decodeFromString(sharedPreferences.getString(RECENT_SEARCHES_KEY, DEFAULT_RECENT_SEARCHES)!!)
        set(value) = sharedPreferences.transaction { putString(RECENT_SEARCHES_KEY, json.encodeToString(value)) }
    //endregion

    //region Firebase
    var firebaseToken: String?
        get() = sharedPreferences.getString(FIREBASE_TOKEN_KEY, null)
        set(value) = sharedPreferences.transaction { putString(FIREBASE_TOKEN_KEY, value) }

    var firebaseRegisteredUsers: MutableSet<String>
        get() = sharedPreferences.getStringSet(FIREBASE_REGISTERED_USERS_KEY, emptySet())!!.mapNotNull { it }.toMutableSet()
        private set(value) = sharedPreferences.transaction { putStringSet(FIREBASE_REGISTERED_USERS_KEY, value) }

    fun markUserAsRegisteredByFirebase(userId: Int) {
        Log.i(TAG, "markUserAsRegisteredByFirebase: $userId has been registered")
        firebaseRegisteredUsers = firebaseRegisteredUsers.apply { add(userId.toString()) }
    }

    fun removeRegisteredFirebaseUser(userId: Int) {
        firebaseRegisteredUsers = firebaseRegisteredUsers.filterNot { it == userId.toString() }.toMutableSet()
    }

    fun clearRegisteredFirebaseUsers() {
        Log.i(TAG, "clearRegisteredFirebaseUsers: called")
        firebaseRegisteredUsers = mutableSetOf()
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

        private val TAG = LocalSettings::class.simpleName

        //region Default values
        private const val DEFAULT_CANCEL_DELAY = 10
        private val DEFAULT_EMAIL_FORWARDING = EmailForwarding.IN_BODY
        private const val DEFAULT_INCLUDE_MESSAGE_IN_REPLY = true
        private const val DEFAULT_ASK_EMAIL_ACKNOWLEDGMENT = false

        private const val DEFAULT_IS_APP_LOCKED = false

        private val DEFAULT_THREAD_DENSITY = ThreadDensity.LARGE
        private val DEFAULT_THEME = Theme.SYSTEM
        private val DEFAULT_ACCENT_COLOR = AccentColor.PINK
        private val INITIAL_SWIPE_ACTION = SwipeAction.TUTORIAL
        val DEFAULT_SWIPE_ACTION_RIGHT = SwipeAction.READ_UNREAD
        val DEFAULT_SWIPE_ACTION_LEFT = SwipeAction.DELETE
        private val DEFAULT_EXTERNAL_CONTENT = ExternalContent.ASK_ME
        private const val DEFAULT_RECENT_SEARCHES = "[]"
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
        private const val EXTERNAL_CONTENT_KEY = "externalContentKey"
        private const val RECENT_SEARCHES_KEY = "recentSearchesKey"
        private const val FIREBASE_TOKEN_KEY = "firebaseTokenKey"
        private const val FIREBASE_REGISTERED_USERS_KEY = "firebaseRegisteredUsersKey"
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
