/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail

import android.annotation.SuppressLint
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import org.matomo.sdk.Tracker

object MatomoMail : MatomoCore {

    override val Context.tracker: Tracker get() = (this as MainApplication).matomoTracker
    override val siteId = 9

    // region Tracker category
    private const val THREAD_ACTION_CATEGORY = "threadActions"
    private const val THREAD_BOTTOM_SHEET_ACTION_CATEGORY = "bottomSheetThreadActions"
    //endregion

    //region Tracker name
    const val OPEN_ACTION_BOTTOM_SHEET = "openBottomSheet"
    const val OPEN_FROM_DRAFT_NAME = "openFromDraft"
    const val ACTION_REPLY_NAME = "reply"
    const val ACTION_REPLY_ALL_NAME = "replyAll"
    const val ACTION_FORWARD_NAME = "forward"
    const val ACTION_DELETE_NAME = "delete"
    const val ACTION_ARCHIVE_NAME = "archive"
    const val ACTION_MARK_AS_SEEN_NAME = "markAsSeen"
    const val ACTION_MOVE_NAME = "move"
    const val ACTION_FAVORITE_NAME = "favorite"
    const val ACTION_SPAM_NAME = "spam"
    const val ACTION_PRINT_NAME = "print"
    const val ACTION_POSTPONE_NAME = "postpone"
    const val ADD_MAILBOX_NAME = "addMailbox"
    const val SWITCH_MAILBOX_NAME = "switchMailbox"
    //endregion

    @SuppressLint("RestrictedApi") // This `SuppressLint` is there so the CI can build
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
        trackEvent(category = THREAD_BOTTOM_SHEET_ACTION_CATEGORY, name = name, value = value?.toMailActionValue())
    }

    private fun Fragment.trackBottomSheetMultiSelectThreadActionsEvent(name: String, value: Int) {
        trackEvent(category = THREAD_BOTTOM_SHEET_ACTION_CATEGORY, name = name, value = value.toFloat())
    }

    fun Fragment.trackThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = THREAD_ACTION_CATEGORY, name = name, value = value?.toMailActionValue())
    }

    private fun Fragment.trackMultiSelectThreadActionsEvent(name: String, value: Int) {
        trackEvent(category = THREAD_ACTION_CATEGORY, name = name, value = value.toFloat())
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

    fun Context.trackMultiSelectionEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("multiSelection", name, action)
    }

    fun Fragment.trackMultiSelectActionEvent(name: String, value: Int, isFromBottomSheet: Boolean = false) {
        val trackerName = "${if (value == 1) "bulkSingle" else "bulk"}${name.capitalizeFirstChar()}"

        if (isFromBottomSheet) {
            trackBottomSheetMultiSelectThreadActionsEvent(trackerName, value)
        } else {
            trackMultiSelectThreadActionsEvent(trackerName, value)
        }
    }

    fun Context.trackUserInfo(name: String, value: Int) {
        trackEvent("userInfo", name, TrackerAction.DATA, value.toFloat())
    }

    fun Fragment.trackOnBoardingEvent(name: String) {
        trackEvent("onBoarding", name)
    }

    fun Fragment.trackThreadListEvent(name: String) {
        trackEvent("threadList", name)
    }

    fun Fragment.trackRestoreMailsEvent(name: String, action: TrackerAction) {
        trackEvent("restoreEmailsBottomSheet", name, action)
    }

    fun Fragment.trackNoValidMailboxesEvent(name: String) {
        trackEvent("noValidMailboxes", name)
    }

    fun Fragment.trackInvalidPasswordMailboxEvent(name: String) {
        trackEvent("invalidPasswordMailbox", name)
    }

    // We need to invert this logical value to keep a coherent value for analytics because actions
    // conditions are inverted (ex: if the condition is `message.isSpam`, then we want to unspam)
    private fun Boolean.toMailActionValue() = (!this).toFloat()
}
