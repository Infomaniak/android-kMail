/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
@file:Suppress("ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD")

package com.infomaniak.mail.data

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.use
import com.google.android.material.color.MaterialColors
import com.infomaniak.core.auth.AccessTokenUsageInterceptor.ApiCallRecord
import com.infomaniak.core.dotlottie.model.DotLottieTheme
import com.infomaniak.core.legacy.utils.SharedValues
import com.infomaniak.core.legacy.utils.sharedValue
import com.infomaniak.core.legacy.utils.transaction
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.SwipeAction
import androidx.appcompat.R as RAndroid
import com.google.android.material.R as RMaterial

class LocalSettings private constructor(context: Context) : SharedValues {

    override val sharedPreferences = context.applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)!!

    var cancelDelay by sharedValue("cancelDelayKey", 10)
    var emailForwarding by sharedValue("emailForwardingKey", EmailForwarding.IN_BODY)
    var includeMessageInReply by sharedValue("includeMessageInReplyKey", true)
    var askEmailAcknowledgement by sharedValue("askEmailAcknowledgmentKey", false)
    var hasAlreadyEnabledNotifications by sharedValue("hasAlreadyEnabledNotificationsKey", false)
    var isAppLocked by sharedValue("isAppLockedKey", false)
    var threadDensity by sharedValue("threadDensityKey", ThreadDensity.LARGE)
    var theme by sharedValue("themeKey", if (SDK_INT >= 29) Theme.SYSTEM else Theme.LIGHT)
    var accentColor by sharedValue("accentColorKey", AccentColor.PINK)
    var swipeRight by sharedValue("swipeRightKey", SwipeAction.TUTORIAL)
    var swipeLeft by sharedValue("swipeLeftKey", SwipeAction.TUTORIAL)
    var threadMode by sharedValue("threadModeKey", ThreadMode.CONVERSATION)
    var externalContent by sharedValue("externalContentKey", ExternalContent.ALWAYS)
    var recentSearches: List<String> by sharedValue("recentSearchesKey", emptyList())
    // firebaseToken: String? was removed.
    // firebaseRegisteredUsersKey: Set<String> was removed.
    var showAiDiscoveryBottomSheet by sharedValue("showEuriaDiscoveryBottomSheetKey", true)
    var showEncryptionDiscoveryBottomSheet by sharedValue("showEncryptionDiscoveryBottomSheetKey", true)
    var showPermissionsOnboarding by sharedValue("showPermissionsOnboardingKey", true)
    var isSentryTrackingEnabled by sharedValue("isSentryTrackingEnabledKey", true)
    var isMatomoTrackingEnabled by sharedValue("isMatomoTrackingEnabledKey", true)
    var autoAdvanceMode by sharedValue("autoAdvanceModeKey", AutoAdvanceMode.THREADS_LIST)
    var autoAdvanceNaturalThread by sharedValue("autoAdvanceNaturalThreadKey", AutoAdvanceMode.FOLLOWING_THREAD)
    var showWebViewOutdated by sharedValue("showWebViewOutdatedKey", true)
    var accessTokenApiCallRecord by sharedValue<ApiCallRecord>("accessTokenApiCallRecordKey", null)
    var lastSelectedScheduleEpochMillis by sharedValue<Long>("lastSelectedScheduleEpochKey", null)
    var lastSelectedSnoozeEpochMillis by sharedValue<Long>("lastSelectedSnoozeEpochMillisKey", null)
    var storageBannerDisplayAppLaunches by sharedValue("storageBannerDisplayAppLaunchesKey", 0)
    var hasClosedStorageBanner by sharedValue("hasClosedStorageBannerKey", false)

    fun removeSettings() = sharedPreferences.transaction { clear() }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction = when (nameRes) {
        R.string.settingsSwipeRight -> swipeRight
        R.string.settingsSwipeLeft -> swipeLeft
        else -> throw IllegalArgumentException()
    }

    fun resetStorageBannerAppLaunches() {
        hasClosedStorageBanner = true
        storageBannerDisplayAppLaunches = 0
    }

    enum class EmailForwarding(@StringRes val localisedNameRes: Int) {
        IN_BODY(R.string.settingsTransferInBody),
        AS_ATTACHMENT(R.string.settingsTransferAsAttachment),
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
        @StringRes val tabNameRes: Int = localisedNameRes,
    ) {
        PINK(R.string.accentColorPinkTitle, R.style.AppTheme_Pink, 0),
        BLUE(R.string.accentColorBlueTitle, R.style.AppTheme_Blue, 1),
        SYSTEM(R.string.accentColorSystemTitle, R.style.AppTheme, 2, R.string.settingsOptionSystemTheme);

        fun getPrimary(context: Context): Int {
            val baseThemeContext = ContextThemeWrapper(context, theme)
            return MaterialColors.getColor(baseThemeContext, RAndroid.attr.colorPrimary, 0)
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
            return MaterialColors.getColor(baseThemeContext, RAndroid.attr.colorControlHighlight, 0)
        }

        fun getDotLottieTheme(context: Context): DotLottieTheme = when (this) {
            PINK, BLUE -> DotLottieTheme.Embedded(context.getAttributeString(R.attr.dotLottieThemeId))
            SYSTEM -> DotLottieTheme.Custom(context.getThemeColorMap())
        }

        private fun Context.getAttributeString(@AttrRes attribute: Int): String? {
            return theme.obtainStyledAttributes(intArrayOf(attribute)).use {
                it.getString(0)
            }
        }

        private fun Context.getThemeColorMap(): Map<String, Color> = buildMap {
            set("AccentPrimary", colorOf(R.color.onboardingDotLottieAccent))
            set("Fond1", colorOf(R.color.onboardingDotLottieBackground))
            set("Shape1", colorOf(R.color.onboardingDotLottieShape))
            set("Stroke", colorOf(R.color.onboardingDotLottieStroke))
        }

        private fun Context.colorOf(@ColorRes res: Int): Color = Color(getColor(res))

        override fun toString() = name.lowercase()
    }

    enum class ThreadMode(@StringRes val localisedNameRes: Int, val matomoName: MatomoName) {
        CONVERSATION(R.string.settingsOptionThreadModeConversation, MatomoName.Conversation),
        MESSAGE(R.string.settingsOptionThreadModeMessage, MatomoName.Message),
    }

    enum class ExternalContent(val apiCallValue: String, @StringRes val localisedNameRes: Int, val matomoName: MatomoName) {
        ALWAYS("true", R.string.settingsOptionAlways, MatomoName.Always),
        ASK_ME("false", R.string.settingsOptionAskMe, MatomoName.AskMe),
    }

    enum class AutoAdvanceMode(val matomoName: MatomoName, @StringRes val localisedNameRes: Int) {
        PREVIOUS_THREAD(MatomoName.PreviousThread, R.string.settingsAutoAdvancePreviousThreadDescription),
        FOLLOWING_THREAD(MatomoName.FollowingThread, R.string.settingsAutoAdvanceFollowingThreadDescription),
        THREADS_LIST(MatomoName.ListOfThread, R.string.settingsAutoAdvanceListOfThreadsDescription),
        NATURAL_THREAD(MatomoName.NaturalThread, R.string.settingsAutoAdvanceNaturalThreadDescription),
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
