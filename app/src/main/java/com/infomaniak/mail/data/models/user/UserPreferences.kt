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
package com.infomaniak.mail.data.models.user

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class UserPreferences : RealmObject {

    //region API data
    var theme: String = ThemeMode.DEFAULT.apiName
    @SerialName("density")
    var threadListDensity: String = ListDensityMode.DEFAULT.apiName
    @SerialName("thread_mode")
    var threadMode: Int = ThreadMode.THREADS.apiValue
    @SerialName("auto_trust_emails")
    var autoTrustEmails: Int = ExternalContentMode.ASK_ME.apiValue
    // @SerialName("reabirthday_calendar_colord_pos")
    // var birthdayCalendarColor: String = ""
    // @SerialName("calendar_default_event_duration")
    // var calendarDefaultEventDuration: Int = 0
    // @SerialName("calendar_start_page")
    // var calendarStartPage: String = ""
    // @SerialName("calendar_time_format")
    // var calendarTimeFormat: String? = null
    // @SerialName("cancel_send_delay")
    // var cancelSendDelay: Int = 0
    // @SerialName("composer_open_fullsize")
    // var composerOpenFullsize: Int = 0
    // @SerialName("contact_display")
    // var contactDisplay: String = ""
    // @SerialName("default_charset")
    // var defaultCharset: String = ""
    // @SerialName("delivery_acknowledgement")
    // var deliveryAcknowledgement: Int = 0
    // @SerialName("display_birthday_calendar")
    // var displayBirthdayCalendar: Int = 0
    // @SerialName("display_only_working_hours")
    // var displayOnlyWorkingHours: Int = 0
    // @SerialName("display_weekend")
    // var displayWeekend: Int = 0
    // @SerialName("display_working_hours_start")
    // var displayWorkingHoursStart: Int = 0
    // @SerialName("display_working_hours_end")
    // var displayWorkingHoursEnd: Int = 0
    // @SerialName("disposition_notification")
    // var dispositionNotification: String = ""
    // @SerialName("favorites_first")
    // var favoritesFirst: Int = 0
    // @SerialName("first_day")
    // var firstDay: Int = 0
    // @SerialName("format_message")
    // var formatMessage: String = ""
    // @SerialName("forward_mode")
    // var forwardMode: String = ""
    // @SerialName("include_mess_in_reply")
    // var includeMessageInReply: Int = 0
    // @SerialName("phone_code")
    // var phoneCode: String = ""
    // @SerialName("read_pos")
    // var readPosition: String = ""
    // var shortcuts: Int = 0
    //endregion

    //region Local data (Transient)
    // private var intelligentMode: String = IntelligentMode.DISABLED.name
    //endregion

    private var _shortRightSwipe: String = SwipeAction.NONE.name
    var shortRightSwipe: SwipeAction
        get() = enumValueOfOrNull<SwipeAction>(_shortRightSwipe) ?: SwipeAction.NONE
        set(value) {
            _shortRightSwipe = value.name
        }

    private var _longRightSwipe: String = SwipeAction.NONE.name
    var longRightSwipe: SwipeAction
        get() = enumValueOfOrNull<SwipeAction>(_longRightSwipe) ?: SwipeAction.NONE
        set(value) {
            _longRightSwipe = value.name
        }

    private var _shortLeftSwipe: String = SwipeAction.NONE.name
    var shortLeftSwipe: SwipeAction
        get() = enumValueOfOrNull<SwipeAction>(_shortLeftSwipe) ?: SwipeAction.NONE
        set(value) {
            _shortLeftSwipe = value.name
        }

    private var _longLeftSwipe: String = SwipeAction.NONE.name
    var longLeftSwipe: SwipeAction
        get() = enumValueOfOrNull<SwipeAction>(_longLeftSwipe) ?: SwipeAction.NONE
        set(value) {
            _longLeftSwipe = value.name
        }

    fun getThemeMode(): ThemeMode = when (theme) {
        ThemeMode.LIGHT.apiName -> ThemeMode.LIGHT
        ThemeMode.DARK.apiName -> ThemeMode.DARK
        else -> ThemeMode.DEFAULT
    }

    fun getListDensityMode(): ListDensityMode = when (threadListDensity) {
        ListDensityMode.COMPACT.apiName -> ListDensityMode.COMPACT
        ListDensityMode.LARGE.apiName -> ListDensityMode.LARGE
        else -> ListDensityMode.DEFAULT
    }

    fun getThreadMode(): ThreadMode = when (threadMode) {
        ThreadMode.MESSAGES.apiValue -> ThreadMode.MESSAGES
        else -> ThreadMode.THREADS
    }

    fun getExternalContentMode(): ExternalContentMode = when (autoTrustEmails) {
        ExternalContentMode.ALWAYS.apiValue -> ExternalContentMode.ALWAYS
        else -> ExternalContentMode.ASK_ME
    }

    fun getSwipeAction(@StringRes nameRes: Int): SwipeAction? = when (nameRes) {
        R.string.settingsSwipeShortRight -> shortRightSwipe
        R.string.settingsSwipeLongRight -> longRightSwipe
        R.string.settingsSwipeShortLeft -> shortLeftSwipe
        R.string.settingsSwipeLongLeft -> longLeftSwipe
        else -> null
    }

    // fun getIntelligentMode(): IntelligentMode? = enumValueOfOrNull<IntelligentMode>(intelligentMode)

    enum class ThemeMode(val apiName: String, val mode: Int, @StringRes val localisedNameRes: Int) {
        DEFAULT("medium", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.settingsOptionSystemTheme),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO, R.string.settingsOptionLightTheme),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES, R.string.settingsOptionDarkTheme);
    }

    enum class ListDensityMode(val apiName: String, @StringRes val localisedNameRes: Int) {
        COMPACT("high", R.string.settingsDensityOptionCompact),
        DEFAULT("normal", R.string.settingsDensityOptionNormal),
        LARGE("low", R.string.settingsDensityOptionLarge),
    }

    enum class ThreadMode(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        THREADS(1, "on", R.string.settingsOptionDiscussions),
        MESSAGES(0, "off", R.string.settingsOptionMessages),
    }

    enum class ExternalContentMode(val apiValue: Int, val apiCallValue: String, @StringRes val localisedNameRes: Int) {
        ALWAYS(1, "true", R.string.settingsOptionAlways),
        ASK_ME(0, "false", R.string.settingsOptionAskMe),
    }

    // enum class IntelligentMode {
    //     ENABLED,
    //     DISABLED,
    // }

    data class SwipeActions(
        var shortRightSwipe: SwipeAction = SwipeAction.NONE,
        var longRightSwipe: SwipeAction = SwipeAction.NONE,
        var shortLeftSwipe: SwipeAction = SwipeAction.NONE,
        var longLeftSwipe: SwipeAction = SwipeAction.NONE,
    )

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
}
