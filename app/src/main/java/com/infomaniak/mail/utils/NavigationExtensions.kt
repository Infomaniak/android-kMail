/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.canNavigate
import com.infomaniak.lib.core.utils.clearStack
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.ui.login.NoMailboxActivity
import com.infomaniak.mail.ui.main.thread.actions.AttachmentActionsBottomSheetDialog
import com.infomaniak.mail.ui.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.noValidMailboxes.NoValidMailboxesActivity
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.createDownloadDialogNavArgs

fun getAnimatedNavOptions() = NavOptions
    .Builder()
    .setEnterAnim(R.anim.fragment_swipe_enter)
    .setExitAnim(R.anim.fragment_swipe_exit)
    .setPopEnterAnim(R.anim.fragment_swipe_pop_enter)
    .setPopExitAnim(R.anim.fragment_swipe_pop_exit)
    .build()

fun Fragment.animatedNavigation(directions: NavDirections, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) findNavController().navigate(directions, getAnimatedNavOptions())
}

fun Fragment.animatedNavigation(@IdRes resId: Int, args: Bundle? = null, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) findNavController().navigate(resId, args, getAnimatedNavOptions())
}

fun Fragment.safeNavigateToNewMessageActivity(
    draftMode: Draft.DraftMode,
    previousMessageUid: String,
    currentClassName: String? = null,
    shouldLoadDistantResources: Boolean = false,
) {
    safeNavigateToNewMessageActivity(
        args = NewMessageActivityArgs(
            arrivedFromExistingDraft = false,
            draftMode = draftMode,
            previousMessageUid = previousMessageUid,
            shouldLoadDistantResources = shouldLoadDistantResources,
        ).toBundle(),
        currentClassName = currentClassName,
    )
}

fun Fragment.safeNavigateToNewMessageActivity(args: Bundle? = null, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) (requireActivity() as MainActivity).navigateToNewMessageActivity(args)
}

fun Fragment.navigateToDownloadProgressDialog(
    attachment: Attachment,
    attachmentIntentType: AttachmentExt.AttachmentIntentType,
    currentClassName: String = AttachmentActionsBottomSheetDialog::class.java.name,
) {
    safeNavigate(
        resId = R.id.downloadAttachmentProgressDialog,
        args = attachment.createDownloadDialogNavArgs(attachmentIntentType),
        currentClassName = currentClassName,
    )
}

//region Launch Activities
fun Context.getLoginActivityIntent(args: LoginActivityArgs? = null, shouldClearStack: Boolean = false): Intent {
    return Intent(this, LoginActivity::class.java).apply {
        if (shouldClearStack) clearStack()
        args?.toBundle()?.let(::putExtras)
    }
}

fun Context.launchLoginActivity(args: LoginActivityArgs? = null) {
    getLoginActivityIntent(args).also(::startActivity)
}

fun Context.launchNoValidMailboxesActivity() {
    Intent(this, NoValidMailboxesActivity::class.java).apply {
        clearStack()
    }.also(::startActivity)
}

fun Context.launchNoMailboxActivity(userId: Int? = null, shouldStartLoginActivity: Boolean = false) {
    val noMailboxActivityIntent =
        Intent(this, NoMailboxActivity::class.java).putExtra(AccountUtils.NO_MAILBOX_USER_ID_KEY, userId)
    val intentsArray = if (shouldStartLoginActivity) {
        arrayOf(getLoginActivityIntent(shouldClearStack = true), noMailboxActivityIntent)
    } else {
        arrayOf(noMailboxActivityIntent)
    }

    startActivities(intentsArray)
}

fun Fragment.launchSyncAutoConfigActivityForResult() {
    (requireActivity() as MainActivity).navigateToSyncAutoConfigActivity()
}
//endregion
