/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.calendar

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackCalendarEventEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.databinding.ViewCalendarEventBannerBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.openAttachment
import com.infomaniak.mail.utils.extensions.findUser
import com.infomaniak.mail.utils.extensions.toDate
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import java.time.format.FormatStyle
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class CalendarEventBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewCalendarEventBannerBinding.inflate(LayoutInflater.from(context), this, true) }

    private var useInfomaniakCalendarRoute: Boolean = false
    // This value can be saved locally because it's only used in one situation : when calling setAttendanceUi() from
    // onlyUpdateAttendance(). The method onlyUpdateAttendance() can only be called if shouldDisplayReplyOptions hasn't changed
    // since the last loadCalendarEvent() call so it's safe to use it here.
    private var shouldDisplayReplyOptions: Boolean = false
    private var attachmentResource: String = ""

    private var navigateToAttendeesBottomSheet: ((List<Attendee>) -> Unit)? = null
    private var navigateToDownloadProgressDialog: ((Attachment, AttachmentIntentType) -> Unit)? = null
    private var replyToCalendarEvent: ((AttendanceState) -> Unit)? = null
    private var onAttendeesButtonClicked: ((Boolean) -> Unit)? = null

    @Inject
    lateinit var snackbarManager: SnackbarManager

    init {
        with(binding) {
            yesButton.handleChoiceButtonBehavior(AttendanceState.ACCEPTED)
            maybeButton.handleChoiceButtonBehavior(AttendanceState.TENTATIVE)
            noButton.handleChoiceButtonBehavior(AttendanceState.DECLINED)

            attendeesButton.apply {
                setOnClickListener { trackCalendarEventEvent("attendees", attendeesButton.isChecked) }
                addOnCheckedChangeListener { _, isChecked ->
                    attendeesSubMenu.isVisible = isChecked
                    onAttendeesButtonClicked?.invoke(isChecked)
                }
            }
        }
    }

    fun loadCalendarEvent(
        calendarEvent: CalendarEvent,
        isCanceled: Boolean,
        shouldDisplayReplyOptions: Boolean,
        attachment: Attachment,
        hasAssociatedInfomaniakCalendarEvent: Boolean,
        shouldStartExpanded: Boolean,
    ) = with(binding) {
        this@CalendarEventBannerView.shouldDisplayReplyOptions = shouldDisplayReplyOptions

        useInfomaniakCalendarRoute = hasAssociatedInfomaniakCalendarEvent
        attachmentResource = attachment.resource ?: run {
            // TODO: Check this sentry
            Sentry.captureMessage("No attachment resource when trying to load calendar event")
            return@with
        }

        val startDate = calendarEvent.start.toDate()
        val endDate = calendarEvent.end.toDate()

        setWarnings(endDate, isCanceled)
        eventName.text = calendarEvent.title
        setEventHour(startDate, endDate, calendarEvent.isFullDay)
        eventLocation.apply {
            isVisible = calendarEvent.location?.isNotBlank() == true
            text = calendarEvent.location
        }

        attendeesButton.isChecked = shouldStartExpanded
        attendeesSubMenu.isVisible = shouldStartExpanded

        setAttendanceUi(calendarEvent.attendees, shouldDisplayReplyOptions)

        addToCalendarButton.setOnClickListener {
            trackCalendarEventEvent("openInMyCalendar")
            attachment.openAttachment(context, navigateToDownloadProgressDialog ?: return@setOnClickListener, snackbarManager)
        }
    }

    fun onlyUpdateAttendance(attendees: List<Attendee>) {
        setAttendanceUi(attendees, shouldDisplayReplyOptions)
    }

    private fun setWarnings(endDate: Date, isCanceled: Boolean) = with(binding) {
        canceledEventWarning.isVisible = isCanceled
        pastEventWarning.isVisible = !isCanceled && Date() > endDate
    }

    private fun setEventHour(startDate: Date, endDate: Date, isFullDay: Boolean) = with(binding) {
        var displayEndDate = endDate

        // When receiving a full day event spanning two days, the start date will indicate let's say the 10th and then end day
        // will indicate the 12th. Here we don't want to show the event starting the 10th and finishing the 12th but rather
        // finishing the 11th. This also makes it so that single days events don't go from the 10th to the 11th but are only
        // displayed as happening on the 10th.
        if (isFullDay) {
            val oneLessDay = endDate.addDays(-1)
            if (oneLessDay >= startDate) displayEndDate = oneLessDay
        }

        val isSameDay = startDate.isSameDayAs(displayEndDate)

        eventDate.text = when {
            isSameDay && !isFullDay -> {
                "${startDate.formatFullDate()}\n${startDate.formatShortHour()} - ${displayEndDate.formatShortHour()}"
            }
            isSameDay && isFullDay -> {
                "${startDate.formatFullDate()}\n${context.getString(R.string.calendarAllDayLong)}"
            }
            !isSameDay && !isFullDay -> {
                "${startDate.formatDateAndHour()} -\n${displayEndDate.formatDateAndHour()}"
            }
            else -> {
                "${startDate.formatMediumDate()} - ${displayEndDate.formatMediumDate()}\n" +
                        context.getString(R.string.calendarAllDayLong)
            }
        }
    }

    private fun setAttendanceUi(attendees: List<Attendee>, shouldDisplayReplyOptions: Boolean) = with(binding) {
        val userAsAttendee = attendees.findUser()

        notPartOfAttendeesWarning.isVisible = userAsAttendee == null
        participationButtons.isVisible = shouldDisplayReplyOptions
        attendeesLayout.isGone = attendees.isEmpty()

        val attendanceState = userAsAttendee?.state
        yesButton.isChecked = attendanceState == AttendanceState.ACCEPTED
        maybeButton.isChecked = attendanceState == AttendanceState.TENTATIVE
        noButton.isChecked = attendanceState == AttendanceState.DECLINED

        displayOrganizer(attendees)
        allAttendeesButton.setOnClickListener {
            trackCalendarEventEvent("seeAllAttendees")
            navigateToAttendeesBottomSheet?.invoke(attendees)
        }
        manyAvatarsView.setAttendees(attendees)
    }

    fun initCallback(
        navigateToAttendeesBottomSheet: (List<Attendee>) -> Unit,
        navigateToDownloadProgressDialog: (Attachment, AttachmentIntentType) -> Unit,
        replyToCalendarEvent: (AttendanceState) -> Unit,
        onAttendeesButtonClicked: (Boolean) -> Unit,
    ) {
        this.navigateToAttendeesBottomSheet = navigateToAttendeesBottomSheet
        this.navigateToDownloadProgressDialog = navigateToDownloadProgressDialog
        this.replyToCalendarEvent = replyToCalendarEvent
        this.onAttendeesButtonClicked = onAttendeesButtonClicked
    }

    private fun MaterialButton.handleChoiceButtonBehavior(attendanceState: AttendanceState) {
        setOnClickListener {
            trackCalendarEventEvent(attendanceState.matomoValue!!)

            // Do nothing if it was already selected
            if (isChecked) {
                resetChoiceButtons()
                replyToCalendarEvent?.invoke(attendanceState)
            }

            isChecked = true
        }
    }

    private fun resetChoiceButtons() = with(binding) {
        yesButton.isChecked = false
        maybeButton.isChecked = false
        noButton.isChecked = false
    }

    private fun displayOrganizer(attendees: List<Attendee>) = with(binding) {
        val organizers = attendees.filter(Attendee::isOrganizer)
        if (organizers.count() > 1) {
            Sentry.withScope { scope ->
                scope.setExtra("amount of organizer", organizers.count().toString())
                scope.setExtra("have same email", organizers.all { it.email == organizers[0].email }.toString())
                scope.setExtra("have same name", organizers.all { it.name == organizers[0].name }.toString())
                Sentry.captureMessage("Found more than one organizer for an event")
            }
        }

        val organizer = organizers.firstOrNull()

        organizerLayout.isGone = organizer == null

        organizer?.let { attendee ->
            organizerAvatar.loadAvatar(attendee)

            val (name, _) = context.getPrettyNameAndEmail(attendee)
            organizerName.text = context.getString(R.string.calendarOrganizerName, name)
        }
    }

    private fun Date.formatDateAndHour(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.BOTH, FormatStyle.MEDIUM, FormatStyle.SHORT)
        } else {
            format("$FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR $FORMAT_DATE_HOUR_MINUTE") // Fallback on unambiguous format
        }
    }

    private fun Date.formatMediumDate(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.DATE, FormatStyle.MEDIUM)
        } else {
            format(FORMAT_DATE_CLEAR_MONTH_DAY_ONE_CHAR) // Fallback on textual day, day, month, year ordering for everyone
        }
    }

    private fun Date.formatFullDate(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.DATE, FormatStyle.FULL)
        } else {
            format(FORMAT_FULL_DATE) // Fallback on day, month, year ordering for everyone
        }
    }

    private fun Date.formatShortHour(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            formatWithLocal(FormatData.HOUR, FormatStyle.SHORT)
        } else {
            format(FORMAT_DATE_HOUR_MINUTE) // Fallback on 24 hours separated by colon format for everyone
        }
    }
}
