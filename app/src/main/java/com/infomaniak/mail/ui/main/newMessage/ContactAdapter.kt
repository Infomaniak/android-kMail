/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.newMessage

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.removeAccents
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter.ContactType.*
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter.ContactViewHolder

@SuppressLint("NotifyDataSetChanged")
class ContactAdapter(
    private val usedContacts: MutableSet<String>,
    private val onContactClicked: (item: MergedContact) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
    private val setSnackBar: (titleRes: Int) -> Unit,
) : RecyclerView.Adapter<ContactViewHolder>() {

    private var allContacts: List<MergedContact> = emptyList()
    private var contactResults = mutableListOf<SearchResult>()

    private var displayAddUnknownContactButton = true
    private var searchQuery = ""

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < contactResults.count()) KNOWN_CONTACT.id else UNKNOWN_CONTACT.id
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        if (getItemViewType(position) == KNOWN_CONTACT.id) bindContact(position) else bindAddNewUser()
    }

    private fun ItemContactBinding.bindContact(position: Int) {
        val searchResult = contactResults[position]
        val contact = searchResult.contact

        contactDetails.apply {
            setMergedContact(contact)
            highlight(searchResult.nameMatchStartIndex, searchResult.emailMatchStartIndex, searchQuery.standardize().count())
        }

        val isAlreadyUsed = usedContacts.contains(contact.email)
        if (!isAlreadyUsed) root.setOnClickListener { onContactClicked(contact) }
        greyedOutState.isVisible = isAlreadyUsed
        root.isEnabled = !isAlreadyUsed
    }

    private fun ItemContactBinding.bindAddNewUser() {
        contactDetails.setAutocompleteUnknownContact(searchQuery)
        root.setOnClickListener {
            context.trackNewMessageEvent("addNewRecipient")
            if (usedContacts.contains(searchQuery)) setSnackBar(R.string.addUnknownRecipientAlreadyUsed) else onAddUnrecognizedContact()
        }
    }

    override fun getItemCount(): Int = contactResults.count() + if (displayAddUnknownContactButton) 1 else 0

    override fun getItemId(position: Int) =
        if (getItemViewType(position) == KNOWN_CONTACT.id) contactResults[position].contact.id!! else 0

    fun addFirstAvailableItem() {
        contactResults.firstOrNull()?.let { onContactClicked(it.contact) } ?: onAddUnrecognizedContact()
    }

    fun clear() {
        contactResults.clear()
        notifyDataSetChanged()
    }

    private data class SearchResult(
        val contact: MergedContact,
        val nameMatchStartIndex: Int,
        val emailMatchStartIndex: Int,
    )

    fun searchContacts(text: CharSequence) {
        fun performFiltering(constraint: CharSequence): MutableList<SearchResult> {
            val searchTerm = constraint.standardize()

            val finalUserList = mutableListOf<SearchResult>()
            displayAddUnknownContactButton = true
            for (contact in allContacts) {
                val standardizedEmail = contact.email.standardize()
                val nameIndex = contact.name.standardize().indexOf(searchTerm)
                val emailIndex = standardizedEmail.indexOf(searchTerm)
                val matches = nameIndex > -1 || emailIndex > -1

                val displayNewContact = (matches && searchTerm == standardizedEmail && !usedContacts.contains(searchTerm))
                if (displayNewContact) displayAddUnknownContactButton = false

                if (matches) finalUserList.add(SearchResult(contact, nameIndex, emailIndex))

                if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break
            }

            return finalUserList
        }

        fun publishResults(results: MutableList<SearchResult>) {
            contactResults = results
            notifyDataSetChanged()
        }

        searchQuery = text.toString()
        publishResults(performFiltering(text))
    }

    fun removeUsedEmail(email: String) = usedContacts.remove(email.standardize())

    fun addUsedContact(email: String) = usedContacts.add(email.standardize())

    private fun CharSequence.standardize(): String = toString().removeAccents().trim().lowercase()

    fun updateContacts(allContacts: List<MergedContact>) {
        this.allContacts = allContacts
    }

    private enum class ContactType(val id: Int) {
        KNOWN_CONTACT(0),
        UNKNOWN_CONTACT(1),
    }

    private companion object {
        const val MAX_AUTOCOMPLETE_RESULTS = 10
    }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
