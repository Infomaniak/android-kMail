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
import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import com.infomaniak.mail.R
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class UserPreferences : RealmObject {

    //region API data
    var theme: String = ThemeMode.DEFAULT.mode
    @SerialName("density")
    var threadListDensity: String = ListDensityMode.DEFAULT.mode
    @SerialName("thread_mode")
    var threadMode: Int = ThreadMode.THREADS.getValue
    // @SerialName("auto_trust_emails")
    // var autoTrustEmails: Int = 0
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

    var _shortRightSwipe: String = SwipeAction.NONE.name
    val shortRightSwipe: SwipeAction?
        get() = enumValueOfOrNull<SwipeAction>(_shortRightSwipe)

    var _longRightSwipe: String = SwipeAction.NONE.name
    val longRightSwipe: SwipeAction?
        get() = enumValueOfOrNull<SwipeAction>(_longRightSwipe)

    var _shortLeftSwipe: String = SwipeAction.NONE.name
    val shortLeftSwipe: SwipeAction?
        get() = enumValueOfOrNull<SwipeAction>(_shortLeftSwipe)

    var _longLeftSwipe: String = SwipeAction.NONE.name
    val longLeftSwipe: SwipeAction?
        get() = enumValueOfOrNull<SwipeAction>(_longLeftSwipe)

    fun getThemeMode(): ThemeMode = when (theme) {
        ThemeMode.LIGHT.mode -> ThemeMode.LIGHT
        ThemeMode.DARK.mode -> ThemeMode.DARK
        else -> ThemeMode.DEFAULT
    }

    fun getListDensityMode(): ListDensityMode = when (threadListDensity) {
        ListDensityMode.COMPACT.mode -> ListDensityMode.COMPACT
        ListDensityMode.LARGE.mode -> ListDensityMode.LARGE
        else -> ListDensityMode.DEFAULT
    }

    fun getThreadMode(): ThreadMode = when (threadMode) {
        ThreadMode.MESSAGES.getValue -> ThreadMode.MESSAGES
        else -> ThreadMode.THREADS
    }

    // fun getIntelligentMode(): IntelligentMode? = enumValueOfOrNull<IntelligentMode>(intelligentMode)

    enum class ThemeMode(val mode: String) {
        DEFAULT("medium"),
        LIGHT("light"),
        DARK("dark"),
    }

    enum class ListDensityMode(val mode: String) {
        COMPACT("high"),
        DEFAULT("normal"),
        LARGE("low"),
    }

    enum class ThreadMode(val getValue: Int, val setValue: String) {
        THREADS(1, "on"),
        MESSAGES(0, "off"),
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
