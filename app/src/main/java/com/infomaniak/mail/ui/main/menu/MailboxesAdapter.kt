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
package com.infomaniak.mail.ui.main.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.infomaniak.mail.MatomoMail.SWITCH_MAILBOX_NAME
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.databinding.ItemInvalidMailboxBinding
import com.infomaniak.mail.databinding.ItemSwitchMailboxBinding
import com.infomaniak.mail.ui.main.menu.MailboxesAdapter.MailboxesViewHolder
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.views.MenuDrawerItemView.SelectionStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MailboxesAdapter(
    private val isInMenuDrawer: Boolean,
    private val hasValidMailboxes: Boolean,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onInvalidPasswordMailboxClicked: ((Mailbox) -> Unit)? = null,
    private val onLockedMailboxClicked: ((String) -> Unit)? = null,
    private var mailboxes: List<Mailbox> = emptyList(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO, // TODO: Inject with hilt
) : RecyclerView.Adapter<MailboxesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MailboxesViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == DisplayType.INVALID_MAILBOX.layout) {
            ItemInvalidMailboxBinding.inflate(layoutInflater, parent, false)
        } else {
            ItemSwitchMailboxBinding.inflate(layoutInflater, parent, false)
        }

        return MailboxesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MailboxesViewHolder, position: Int) = with(holder.binding) {
        val mailbox = mailboxes[position]
        val isCurrentMailbox = mailbox.mailboxId == AccountUtils.currentMailboxId

        when (getItemViewType(position)) {
            DisplayType.VALID_MAILBOX.layout -> (this as ItemSwitchMailboxBinding).displayValidMailbox(mailbox, isCurrentMailbox)
            DisplayType.INVALID_MAILBOX.layout -> (this as ItemInvalidMailboxBinding).displayInvalidMailbox(mailbox)
            else -> Unit
        }
    }

    private fun ItemSwitchMailboxBinding.displayValidMailbox(mailbox: Mailbox, isCurrentMailbox: Boolean) = with(root) {
        text = mailbox.email

        if (isInMenuDrawer) badge = mailbox.unreadCountDisplay.count else itemStyle = SelectionStyle.ACCOUNT
        isPastilleDisplayed = mailbox.unreadCountDisplay.shouldDisplayPastille
        isPasswordOutdated = !mailbox.isPasswordValid
        isMailboxLocked = mailbox.isLocked
        hasValidMailbox = hasValidMailboxes

        setSelectedState(isCurrentMailbox)

        if (!isCurrentMailbox || !hasValidMailboxes) {
            setOnClickListener {
                if (isInMenuDrawer) {
                    context.trackMenuDrawerEvent(SWITCH_MAILBOX_NAME)
                } else {
                    context.trackAccountEvent(SWITCH_MAILBOX_NAME)
                }

                lifecycleScope.launch(ioDispatcher) {
                    AccountUtils.switchToMailbox(mailbox.mailboxId)
                }
            }

            setOnOutdatedPasswordClickListener { onInvalidPasswordMailboxClicked?.invoke(mailbox) }
            setOnLockedMailboxClickListener { onLockedMailboxClicked?.invoke(mailbox.email) }
        }
    }

    private fun ItemInvalidMailboxBinding.displayInvalidMailbox(mailbox: Mailbox) = with(root) {
        text = mailbox.email

        if (!mailbox.isLocked) {
            shouldDisplayChevron = true
            setOnClickListener { onInvalidPasswordMailboxClicked?.invoke(mailbox) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (mailboxes[position].isValid) DisplayType.VALID_MAILBOX.layout else DisplayType.INVALID_MAILBOX.layout
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    private enum class DisplayType(val layout: Int) {
        INVALID_MAILBOX(R.layout.item_invalid_mailbox),
        VALID_MAILBOX(R.layout.item_switch_mailbox),
    }

    class MailboxesViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
}
