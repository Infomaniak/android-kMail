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
package com.infomaniak.mail

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import org.matomo.sdk.Tracker

object MatomoMail : MatomoCore {

    override val Context.tracker: Tracker get() = (this as ApplicationMain).matomoTracker
    override val siteId = 9

    //region Tracker Name
    const val OPEN_FROM_DRAFT_NAME = "openFromDraft"
    const val ACTION_REPLY_NAME = "reply"
    const val ACTION_REPLY_ALL_NAME = "replyAll"
    const val ACTION_FORWARD_NAME = "forward"
    const val ACTION_TRASH_NAME = "trash"
    const val ACTION_ARCHIVE_NAME = "archive"
    const val ACTION_MARK_AS_SEEN_NAME = "markAsSeen"
    const val ACTION_MOVE_NAME = "move"
    const val ACTION_FAVORITE_NAME = "favorite"
    const val ACTION_SPAM_NAME = "spam"
    const val ACTION_PRINT_NAME = "print"
    const val ACTION_POSTPONE_NAME = "postpone"
    //endregion

    fun Context.trackDestination(navDestination: NavDestination) = with(navDestination) {
        trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
    }

    fun Context.trackSendingDraftEvent(action: DraftAction, draft: Draft) = with(draft) {
        trackNewMessageEvent(action.matomoValue)
        if (action == DraftAction.SEND) {
            val trackerData = listOf("numberOfTo" to to, "numberOfCc" to cc, "numberOfBcc" to bcc)
            trackerData.forEach { (eventName, recipients) ->
                trackNewMessageEvent(eventName, TrackerAction.DATA, recipients.size.toFloat())
            }
        }
    }

    fun Fragment.trackContactActionsEvent(name: String) {
        trackEvent("contactActions", name)
    }

    fun Fragment.trackAttachmentActionsEvent(name: String) {
        trackEvent("attachmentActions", name)
    }

    fun Fragment.trackBottomSheetMessageActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "bottomSheetMessageActions", name = name, value = value?.toMailActionValue())
    }

    fun Fragment.trackBottomSheetThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "bottomSheetThreadActions", name = name, value = value?.toMailActionValue())
    }

    fun Fragment.trackThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent("threadActions", name, value = value?.toMailActionValue())
    }

    fun Fragment.trackMessageActionsEvent(name: String) {
        trackEvent("messageActions", name)
    }

    fun Fragment.trackSearchEvent(name: String, value: Boolean? = null) {
        requireContext().trackSearchEvent(name, value)
    }

    fun Context.trackMessageEvent(name: String, value: Boolean? = null) {
        trackEvent("message", name, value = value?.toFloat())
    }

    fun Context.trackSearchEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "search", name = name, value = value?.toFloat())
    }

    fun Fragment.trackNewMessageEvent(name: String) {
        context?.trackNewMessageEvent(name)
    }

    fun Context.trackNewMessageEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("newMessage", name, action, value)
    }

    fun Fragment.trackMenuDrawerEvent(name: String, value: Boolean? = null) {
        context?.trackMenuDrawerEvent(name, value = value?.toFloat())
    }

    fun Context.trackMenuDrawerEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("menuDrawer", name, action, value)
    }

    fun Fragment.trackCreateFolderEvent(name: String) {
        trackEvent("createFolder", name)
    }

    // We need to invert this logical value to keep a coherent value for analytics
    private fun Boolean.toMailActionValue() = (!this).toFloat()
}
