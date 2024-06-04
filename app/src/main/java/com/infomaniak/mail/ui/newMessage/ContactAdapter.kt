/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.KNOWN_CONTACT
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.UNKNOWN_CONTACT
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder
import com.infomaniak.mail.utils.extensions.standardize

@SuppressLint("NotifyDataSetChanged")
class ContactAdapter(
    private val usedEmails: MutableSet<String>,
    private val onContactClicked: (item: MergedContact) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
    private val snackbarManager: SnackbarManager,
) : Adapter<ContactViewHolder>() {

    private var allContacts: List<MergedContact> = emptyList()
    private var matchedContacts = listOf<MatchedContact>()

    private var displayAddUnknownContactButton = true
    private var searchQuery = ""

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < matchedContacts.count()) KNOWN_CONTACT.id else UNKNOWN_CONTACT.id
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        if (getItemViewType(position) == KNOWN_CONTACT.id) bindContact(position) else bindAddNewUser()
    }

    private fun ItemContactBinding.bindContact(position: Int) = with(matchedContacts[position]) {
        contactDetails.apply {
            setMergedContact(contact)
            highlight(nameMatchedStartIndex, emailMatchedStartIndex, searchQuery.standardize().count())
        }

        val isAlreadyUsed = usedEmails.contains(contact.email.standardize())
        if (!isAlreadyUsed) root.setOnClickListener { onContactClicked(contact) }
        greyedOutState.isVisible = isAlreadyUsed
        root.isEnabled = !isAlreadyUsed
    }

    private fun ItemContactBinding.bindAddNewUser() {
        contactDetails.setAutocompleteUnknownContact(searchQuery)
        root.setOnClickListener {
            context.trackNewMessageEvent("addNewRecipient")
            if (usedEmails.contains(searchQuery)) {
                snackbarManager.setValue(context.getString(R.string.addUnknownRecipientAlreadyUsed))
            } else {
                onAddUnrecognizedContact()
            }
        }
    }

    override fun getItemCount(): Int = matchedContacts.count() + if (displayAddUnknownContactButton) 1 else 0

    override fun getItemId(position: Int): Long {
        return if (getItemViewType(position) == KNOWN_CONTACT.id) matchedContacts[position].contact.id!! else 0L
    }

    fun addFirstAvailableItem() {
        matchedContacts.firstOrNull()?.let { onContactClicked(it.contact) } ?: onAddUnrecognizedContact()
    }

    fun clear() {
        matchedContacts = listOf()
        notifyDataSetChanged()
    }

    fun searchContacts(text: CharSequence) {

        fun performFiltering(constraint: CharSequence): List<MatchedContact> {
            val searchTerm = constraint.standardize()

            val finalUserList = mutableListOf<MatchedContact>()
            displayAddUnknownContactButton = true
            for (contact in allContacts) {
                val nameMatchedIndex = contact.name.standardize().indexOf(searchTerm)
                val standardizedEmail = contact.email.standardize()
                val emailMatchedIndex = standardizedEmail.indexOf(searchTerm)
                val matches = nameMatchedIndex >= 0 || emailMatchedIndex >= 0

                val displayNewContact = (matches && searchTerm == standardizedEmail && !usedEmails.contains(searchTerm))
                if (displayNewContact) displayAddUnknownContactButton = false

                if (matches) finalUserList.add(MatchedContact(contact, nameMatchedIndex, emailMatchedIndex))

                if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break
            }

            return finalUserList
        }

        searchQuery = text.toString()
        matchedContacts = performFiltering(text)
        notifyDataSetChanged()
    }

    fun removeUsedEmail(email: String): Boolean {
        return usedEmails.remove(email.standardize()).also { isSuccess ->
            if (isSuccess) {
                matchedContacts.forEachIndexed { index, matchedContact ->
                    if (matchedContact.contact.email == email) notifyItemChanged(index)
                }
            }
        }
    }

    fun addUsedContact(email: String) = usedEmails.add(email.standardize())

    fun updateContacts(allContacts: List<MergedContact>) {
        this.allContacts = allContacts
    }

    private enum class ContactType(val id: Int) {
        KNOWN_CONTACT(0),
        UNKNOWN_CONTACT(1),
    }

    private data class MatchedContact(
        val contact: MergedContact,
        val nameMatchedStartIndex: Int,
        val emailMatchedStartIndex: Int,
    )

    companion object {
        private const val MAX_AUTOCOMPLETE_RESULTS = 10
    }

    class ContactViewHolder(val binding: ItemContactBinding) : ViewHolder(binding.root)
}
