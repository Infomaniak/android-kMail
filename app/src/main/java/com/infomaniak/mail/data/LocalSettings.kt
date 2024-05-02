/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SharedValues
import com.infomaniak.lib.core.utils.sharedValue
import com.infomaniak.lib.core.utils.transaction
import com.infomaniak.mail.MatomoMail.ACTION_ARCHIVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_DELETE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_FAVORITE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MARK_AS_SEEN_NAME
import com.infomaniak.mail.MatomoMail.ACTION_MOVE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_POSTPONE_NAME
import com.infomaniak.mail.MatomoMail.ACTION_SPAM_NAME
import com.infomaniak.mail.R
import com.google.android.material.R as RMaterial

class LocalSettings private constructor(context: Context) : SharedValues {

    override val sharedPreferences = context.applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)!!

    var appLaunches by sharedValue("appLaunchesKey", 0)
    var cancelDelay by sharedValue("cancelDelayKey", 10)
    var emailForwarding by sharedValue("emailForwardingKey", EmailForwarding.IN_BODY)
    var includeMessageInReply by sharedValue("includeMessageInReplyKey", true)
    var askEmailAcknowledgement by sharedValue("askEmailAcknowledgmentKey", false)
    var hasAlreadyEnabledNotifications by sharedValue("hasAlreadyEnabledNotificationsKey", false)
    var isAppLocked by sharedValue("isAppLockedKey", false)
    var aiEngine by sharedValue("aiEngineKey", AiEngine.FALCON)
    var threadDensity by sharedValue("threadDensityKey", ThreadDensity.LARGE)
    var theme by sharedValue("themeKey", if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Theme.LIGHT else Theme.SYSTEM)
    var accentColor by sharedValue("accentColorKey", AccentColor.PINK)
    var swipeRight by sharedValue("swipeRightKey", SwipeAction.TUTORIAL)
    var swipeLeft by sharedValue("swipeLeftKey", SwipeAction.TUTORIAL)
    var threadMode by sharedValue("threadModeKey", ThreadMode.CONVERSATION)
    var externalContent by sharedValue("externalContentKey", ExternalContent.ALWAYS)
    var recentSearches: List<String> by sharedValue("recentSearchesKey", emptyList())
    var firebaseToken by sharedValueNullable("firebaseTokenKey", null)
    var firebaseRegisteredUsers by sharedValue("firebaseRegisteredUsersKey", emptySet())
    var showAiDiscoveryBottomSheet by sharedValue("showAiDiscoveryBottomSheetKey", true)
    var showSyncDiscoveryBottomSheet by sharedValue("showSyncDiscoveryBottomSheetKey", true)
    var showPermissionsOnboarding by sharedValue("showPermissionsOnboardingKey", true)
    var isSentryTrackingEnabled by sharedValue("isSentryTrackingEnabledKey", true)
    var isMatomoTrackingEnabled by sharedValue("isMatomoTrackingEnabledKey", true)
    var autoAdvanceMode by sharedValue("autoAdvanceModeKey", AutoAdvanceMode.LIST_THREAD)
    var autoAdvanceIntelligentMode by sharedValue("autoAdvanceIntelligentModeKey", AutoAdvanceMode.NEXT_THREAD)

    fun removeSettings() = sharedPreferences.transaction { clear() }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction = when (nameRes) {
        R.string.settingsSwipeRight -> swipeRight
        R.string.settingsSwipeLeft -> swipeLeft
        else -> throw IllegalArgumentException()
    }

    fun markUserAsRegisteredByFirebase(userId: Int) {
        SentryLog.i(TAG, "markUserAsRegisteredByFirebase: $userId has been registered")
        firebaseRegisteredUsers = firebaseRegisteredUsers.toMutableSet().apply { add(userId.toString()) }
    }

    fun removeRegisteredFirebaseUser(userId: Int) {
        firebaseRegisteredUsers = firebaseRegisteredUsers.filterNot { it == userId.toString() }.toSet()
    }

    fun clearRegisteredFirebaseUsers() {
        SentryLog.i(TAG, "clearRegisteredFirebaseUsers: called")
        firebaseRegisteredUsers = mutableSetOf()
    }

    enum class EmailForwarding(@StringRes val localisedNameRes: Int) {
        IN_BODY(R.string.settingsTransferInBody),
        AS_ATTACHMENT(R.string.settingsTransferAsAttachment),
    }

    enum class AiEngine(
        @StringRes val localisedNameRes: Int,
        @DrawableRes val iconRes: Int,
        val matomoValue: String,
        val apiValue: String,
    ) {
        FALCON(R.string.aiEngineFalcon, R.drawable.ic_ai_engine_falcon, "falcon", "falcon"),
        CHAT_GPT(R.string.aiEngineChatGpt, R.drawable.ic_ai_engine_chat_gpt, "chatGpt", "gpt"),
    }

    enum class ThreadDensity(@StringRes val localisedNameRes: Int) {
        COMPACT(R.string.settingsDensityOptionCompact),
        NORMAL(R.string.settingsDensityOptionNormal),
        LARGE(R.string.settingsDensityOptionLarge);

        override fun toString() = name.lowercase()
    }

    enum class Theme(@StringRes val localisedNameRes: Int, val mode: Int) {
        SYSTEM(R.string.settingsOptionSystemTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(R.string.settingsOptionLightTheme, AppCompatDelegate.MODE_NIGHT_NO),
        DARK(R.string.settingsOptionDarkTheme, AppCompatDelegate.MODE_NIGHT_YES);

        override fun toString() = name.lowercase()
    }

    enum class AccentColor(
        @StringRes val localisedNameRes: Int,
        @StyleRes val theme: Int,
        val introTabIndex: Int,
    ) {
        PINK(R.string.accentColorPinkTitle, R.style.AppTheme_Pink, 0),
        BLUE(R.string.accentColorBlueTitle, R.style.AppTheme_Blue, 1),
        SYSTEM(R.string.accentColorSystemTitle, R.style.AppTheme, 2);

        fun getPrimary(context: Context): Int {
            val baseThemeContext = ContextThemeWrapper(context, theme)
            return MaterialColors.getColor(baseThemeContext, RMaterial.attr.colorPrimary, 0)
        }

        fun getOnPrimary(context: Context): Int {
            val baseThemeContext = ContextThemeWrapper(context, theme)
            return MaterialColors.getColor(baseThemeContext, RMaterial.attr.colorOnPrimary, 0)
        }

        fun getOnboardingSecondaryBackground(context: Context): Int {
            val baseThemeContext = ContextThemeWrapper(context, theme)
            return baseThemeContext.getColor(R.color.onboarding_secondary_background)
        }

        fun getRipple(context: Context): Int {
            val baseThemeContext = ContextThemeWrapper(context, theme)
            return MaterialColors.getColor(baseThemeContext, RMaterial.attr.colorControlHighlight, 0)
        }

        override fun toString() = name.lowercase()
    }

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

    enum class ThreadMode(@StringRes val localisedNameRes: Int, val matomoValue: String) {
        CONVERSATION(R.string.settingsOptionThreadModeConversation, "conversation"),
        MESSAGE(R.string.settingsOptionThreadModeMessage, "message"),
    }

    enum class ExternalContent(val apiCallValue: String, @StringRes val localisedNameRes: Int, val matomoValue: String) {
        ALWAYS("true", R.string.settingsOptionAlways, "always"),
        ASK_ME("false", R.string.settingsOptionAskMe, "askMe"),
    }

    enum class AutoAdvanceMode(val id: String, @StringRes val localisedNameRes: Int) {
        PREVIOUS_THREAD("lastThread", R.string.settingsAutoAdvancePreviousThreadDescription),
        NEXT_THREAD("nextThread", R.string.settingsAutoAdvanceFollowingThreadDescription),
        LIST_THREAD("listThread", R.string.settingsAutoAdvanceListOfThreadsDescription),
        LAST_ACTION("lastAction", R.string.settingsAutoAdvanceNaturalThreadDescription),
    }

    companion object {

        private val TAG = LocalSettings::class.java.simpleName
        private const val SHARED_PREFS_NAME = "LocalSettingsSharedPref"

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
