/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main

import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.ui.bottomSheetDialogs.LockedMailboxBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.menu.MailboxesAdapter

interface MailboxListFragment {

    val mailboxesAdapter: MailboxesAdapter
    val currentClassName: String
    val hasValidMailboxes: Boolean

    fun Fragment.onLockedMailboxClicked(mailboxEmail: String) {
        safeNavigate(
            resId = R.id.lockedMailboxBottomSheetDialog,
            args = LockedMailboxBottomSheetDialogArgs(mailboxEmail).toBundle(),
            currentClassName = currentClassName,
        )
    }

    fun Fragment.onInvalidPasswordMailboxClicked(mailbox: Mailbox) {
        safeNavigate(
            resId = R.id.invalidPasswordFragment,
            args = InvalidPasswordFragmentArgs(mailbox.mailboxId, mailbox.objectId, mailbox.email).toBundle(),
            currentClassName = currentClassName,
        )
    }
}
