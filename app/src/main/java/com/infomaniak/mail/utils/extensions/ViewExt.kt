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
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.infomaniak.mail.R
import androidx.appcompat.R as RAndroid

fun View.bindSendingClickListener(
    lifecycleOwner: LifecycleOwner,
    canSendEmailsLive: LiveData<Boolean>,
    onActionBlocked: () -> Unit,
    onActionExecute: () -> Unit
) {
    canSendEmailsLive.observe(lifecycleOwner) { canSendEmails ->
        Log.i("elouan", "ThreadListFragment : ${canSendEmails}")
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

    this.applyColor(buttonState)

    this.setOnClickListener {
        when (buttonState) {
            SendingButtonState.Send -> onActionExecute()
            SendingButtonState.SendingBlocked -> onActionBlocked()
        }
    }
}


fun View.applyColor(buttonState: SendingButtonState) {
    val color = if (buttonState == SendingButtonState.SendingBlocked) {
        ContextCompat.getColor(context, R.color.disabledIconColor)
    } else {
        context.getAttributeColor(RAndroid.attr.colorPrimary)
    }

    if (this is ExtendedFloatingActionButton) this.backgroundTintList = ColorStateList.valueOf(color)
}


enum class SendingButtonState {
    Send,
    SendingBlocked
}
