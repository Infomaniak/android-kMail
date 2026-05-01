/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.utils.extensions

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions

fun View.bindSendingClickListener(
    lifecycleOwner: LifecycleOwner,
    permissionLive: LiveData<MailboxPermissions?>,
    onActionBlocked: () -> Unit,
    onActionExecute: () -> Unit
) {
    permissionLive.observe(lifecycleOwner) { permissions ->
        val canSendEmails = permissions?.canSendEmails ?: true
        val buttonState = if (canSendEmails) SendingButtonState.Send else SendingButtonState.SendingBlocked

        this.setSendingClickListener(
            buttonState = buttonState,
            onActionBlocked = onActionBlocked,
            onActionExecute = onActionExecute
        )
    }
}

fun View.setSendingClickListener(
    buttonState: SendingButtonState,
    onActionBlocked: () -> Unit,
    onActionExecute: () -> Unit
) {

    if (buttonState != SendingButtonState.Send) {
        this.applyDisabledColor(buttonState)
    }

    this.setOnClickListener {
        when (buttonState) {
            SendingButtonState.Send -> onActionExecute()
            SendingButtonState.SendingBlocked -> onActionBlocked()
            SendingButtonState.Disabled -> Unit
        }
    }
}


private fun View.applyDisabledColor(buttonState: SendingButtonState) {

    val color = ContextCompat.getColor(context, R.color.disabledIconColor)
    when (this) {
        is MaterialButton -> {
            // this.iconTint = ColorStateList.valueOf(color)
            // this.setTextColor(color)
            this.backgroundTintList = ColorStateList.valueOf(color)
        }

        is MaterialToolbar -> {
            this.navigationIcon?.setTint(color)
            // this.menu.forEach { item ->
            //     item.icon?.setTint(color)
            // }
            this.setTitleTextColor(color)
        }

        // is ActionItemView -> {
        //     this.setIconColor(color)
        // }

        is ImageView -> {
            this.setColorFilter(color)
        }

        is TextView -> {
            this.setTextColor(color)
        }
    }
}


enum class SendingButtonState {
    Send,
    SendingBlocked,
    Disabled
}
