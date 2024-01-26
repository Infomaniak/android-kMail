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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.databinding.ViewCalendarEventBannerBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.AttachmentIntentUtils.openAttachment
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.findUser
import com.infomaniak.mail.utils.toDate
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.ext.copyFromRealm
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
    private lateinit var savedAttendees: List<Attendee>
    private var attachmentResource: String = ""

    private var navigateToAttendeesBottomSheet: ((List<Attendee>) -> Unit)? = null
    private var navigateToDownloadProgressDialog: (() -> Unit)? = null
    private var replyToCalendarEvent: ((Attendee.AttendanceState) -> Unit)? = null

    @Inject
    lateinit var snackbarManager: SnackbarManager

    init {
        with(binding) {
            yesButton.handleChoiceButtonBehavior(Attendee.AttendanceState.ACCEPTED)
            maybeButton.handleChoiceButtonBehavior(Attendee.AttendanceState.TENTATIVE)
            noButton.handleChoiceButtonBehavior(Attendee.AttendanceState.DECLINED)

            attendeesButton.addOnCheckedChangeListener { _, isChecked -> attendeesSubMenu.isVisible = isChecked }
        }
    }

    fun loadCalendarEvent(
        calendarEvent: CalendarEvent,
        isCanceled: Boolean,
        shouldDisplayReplyOptions: Boolean,
        attachment: Attachment,
        hasInfomaniakCalendarEventAssociated: Boolean,
    ) = with(binding) {
        useInfomaniakCalendarRoute = hasInfomaniakCalendarEventAssociated
        savedAttendees = calendarEvent.attendees.copyFromRealm()
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

        val userAsAttendee = savedAttendees.findUser()
        setAttendanceUi(userAsAttendee != null, shouldDisplayReplyOptions, userAsAttendee?.state)

        addToCalendarButton.setOnClickListener {
            attachment.openAttachment(context, navigateToDownloadProgressDialog ?: return@setOnClickListener, snackbarManager)
        }
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
                "${startDate.formatMediumDate()} - ${displayEndDate.formatMediumDate()}\n${context.getString(R.string.calendarAllDayLong)}"
            }
        }
    }

    private fun setAttendanceUi(
        iAmInvited: Boolean,
        shouldDisplayReplyOptions: Boolean,
        attendanceState: Attendee.AttendanceState?,
    ) = with(binding) {
        notPartOfAttendeesWarning.isGone = iAmInvited
        participationButtons.isVisible = shouldDisplayReplyOptions
        attendeesLayout.isGone = savedAttendees.isEmpty()

        yesButton.isChecked = attendanceState == Attendee.AttendanceState.ACCEPTED
        maybeButton.isChecked = attendanceState == Attendee.AttendanceState.TENTATIVE
        noButton.isChecked = attendanceState == Attendee.AttendanceState.DECLINED

        displayOrganizer(savedAttendees)
        allAttendeesButton.setOnClickListener { navigateToAttendeesBottomSheet?.invoke(savedAttendees) }
        manyAvatarsView.setAttendees(savedAttendees)
    }

    fun initCallback(
        navigateToAttendeesBottomSheet: (List<Attendee>) -> Unit,
        navigateToDownloadProgressDialog: () -> Unit,
        replyToCalendarEvent: (Attendee.AttendanceState) -> Unit,
    ) {
        this.navigateToAttendeesBottomSheet = navigateToAttendeesBottomSheet
        this.navigateToDownloadProgressDialog = navigateToDownloadProgressDialog
        this.replyToCalendarEvent = replyToCalendarEvent
    }

    private fun MaterialButton.handleChoiceButtonBehavior(attendanceState: Attendee.AttendanceState) {
        setOnClickListener {
            val previouslyUnchecked = isChecked

            if (previouslyUnchecked) {
                resetChoiceButtons()
                updateThisUsersAttendance(attendanceState)
                replyToCalendarEvent?.invoke(attendanceState)
            }

            isChecked = true
        }
    }

    private fun updateThisUsersAttendance(attendanceState: Attendee.AttendanceState) {
        savedAttendees.firstOrNull(Attendee::isMe)?.manuallyUpdateAttendeeAfterReplying(attendanceState)
        binding.manyAvatarsView.setAttendees(savedAttendees)
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
