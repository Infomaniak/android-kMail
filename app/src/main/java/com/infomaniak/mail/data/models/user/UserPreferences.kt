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

import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.Serializable

@Serializable
class UserPreferences : RealmObject {

    //region API data
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

    // fun getIntelligentMode(): IntelligentMode? = enumValueOfOrNull<IntelligentMode>(intelligentMode)

    // enum class IntelligentMode {
    //     ENABLED,
    //     DISABLED,
    // }
}
