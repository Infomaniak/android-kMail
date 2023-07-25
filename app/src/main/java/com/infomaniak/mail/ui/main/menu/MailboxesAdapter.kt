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
import com.infomaniak.mail.databinding.ItemMailboxMenuDrawerBinding
import com.infomaniak.mail.databinding.ItemSimpleMailboxBinding
import com.infomaniak.mail.ui.main.menu.MailboxesAdapter.MailboxesViewHolder
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.views.decoratedTextItemView.DecoratedTextItemView.SelectionStyle
import com.infomaniak.mail.views.decoratedTextItemView.SelectableTextItemView
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
        val binding = when (viewType) {
            DisplayType.SIMPLE_MAILBOX.layout -> ItemSimpleMailboxBinding.inflate(layoutInflater, parent, false)
            DisplayType.MENU_DRAWER_MAILBOX.layout -> ItemMailboxMenuDrawerBinding.inflate(layoutInflater, parent, false)
            else -> ItemInvalidMailboxBinding.inflate(layoutInflater, parent, false)
        }

        return MailboxesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MailboxesViewHolder, position: Int) = with(holder.binding) {
        val mailbox = mailboxes[position]
        val isCurrentMailbox = mailbox.mailboxId == AccountUtils.currentMailboxId

        when (getItemViewType(position)) {
            DisplayType.SIMPLE_MAILBOX.layout -> {
                (this as ItemSimpleMailboxBinding).displaySimpleMailbox(mailbox, isCurrentMailbox)
            }
            DisplayType.MENU_DRAWER_MAILBOX.layout -> {
                (this as ItemMailboxMenuDrawerBinding).displayMenuDrawerMailbox(mailbox, isCurrentMailbox)
            }
            DisplayType.INVALID_MAILBOX.layout -> (this as ItemInvalidMailboxBinding).displayInvalidMailbox(mailbox)
        }
    }

    private fun ItemSimpleMailboxBinding.displaySimpleMailbox(mailbox: Mailbox, isCurrentMailbox: Boolean) = with(root) {
        displayValidMailbox(mailbox, isCurrentMailbox) { context.trackAccountEvent(SWITCH_MAILBOX_NAME) }
        setSelectedState(isCurrentMailbox)
    }


    private fun ItemMailboxMenuDrawerBinding.displayMenuDrawerMailbox(mailbox: Mailbox, isCurrentMailbox: Boolean) = with(root) {
        displayValidMailbox(mailbox, isCurrentMailbox) { context.trackMenuDrawerEvent(SWITCH_MAILBOX_NAME) }

        unreadCount = mailbox.unreadCountDisplay.count
        isPastilleDisplayed = mailbox.unreadCountDisplay.shouldDisplayPastille
    }

    private fun SelectableTextItemView.displayValidMailbox(
        mailbox: Mailbox,
        isCurrentMailbox: Boolean,
        trackerCallback: () -> Unit,
    ) {
        text = mailbox.email

        if (!isCurrentMailbox) {
            setOnClickListener {
                trackerCallback()

                lifecycleScope.launch(ioDispatcher) {
                    AccountUtils.switchToMailbox(mailbox.mailboxId)
                }
            }
        }
    }

    private fun ItemInvalidMailboxBinding.displayInvalidMailbox(mailbox: Mailbox) = with(root) {
        text = mailbox.email

        itemStyle = if (isInMenuDrawer) SelectionStyle.MENU_DRAWER else SelectionStyle.OTHER

        isPasswordOutdated = !mailbox.isPasswordValid
        isMailboxLocked = mailbox.isLocked
        hasNoValidMailboxes = !hasValidMailboxes

        computeEndIconVisibility()

        initSetOnClickListener(
            onLockedMailboxClicked = { onLockedMailboxClicked?.invoke(mailbox.email) },
            onInvalidPasswordMailboxClicked = { onInvalidPasswordMailboxClicked?.invoke(mailbox) },
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            !mailboxes[position].isValid -> DisplayType.INVALID_MAILBOX.layout
            isInMenuDrawer -> DisplayType.MENU_DRAWER_MAILBOX.layout
            else -> DisplayType.SIMPLE_MAILBOX.layout
        }
    }

    override fun getItemCount(): Int = mailboxes.count()

    fun setMailboxes(newMailboxes: List<Mailbox>) {
        mailboxes = newMailboxes
        notifyDataSetChanged()
    }

    private enum class DisplayType(val layout: Int) {
        INVALID_MAILBOX(R.layout.item_invalid_mailbox),
        MENU_DRAWER_MAILBOX(R.layout.item_mailbox_menu_drawer),
        SIMPLE_MAILBOX(R.layout.item_simple_mailbox),
    }

    class MailboxesViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
}
