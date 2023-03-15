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
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.context
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
) : RecyclerView.Adapter<ContactViewHolder>(), Filterable {

    private var allContacts: List<MergedContact> = emptyList()
    private var contacts = mutableListOf<MergedContact>()

    private var displayAddUnknownContactButton = true
    private var searchQuery = ""

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < contacts.count()) KNOWN_CONTACT.id else UNKNOWN_CONTACT.id
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        if (getItemViewType(position) == KNOWN_CONTACT.id) bindContact(position) else bindAddNewUser()
    }

    private fun ItemContactBinding.bindContact(position: Int) {
        val contact = contacts[position]
        userName.text = contact.name
        userEmail.text = contact.email
        userAvatar.loadAvatar(contact)
        root.setOnClickListener { onContactClicked(contact) }
    }

    private fun ItemContactBinding.bindAddNewUser() {
        userName.text = context.getString(R.string.addUnknownRecipientTitle)
        userEmail.text = searchQuery
        userAvatar.loadUnknownUserAvatar()
        root.setOnClickListener {
            context.trackNewMessageEvent("addNewRecipient")
            if (usedContacts.contains(searchQuery)) setSnackBar(R.string.addUnknownRecipientAlreadyUsed) else onAddUnrecognizedContact()
        }
    }

    override fun getItemCount(): Int = contacts.count() + if (displayAddUnknownContactButton) 1 else 0

    override fun getItemId(position: Int) = if (getItemViewType(position) == KNOWN_CONTACT.id) contacts[position].id!! else 0

    fun addFirstAvailableItem() {
        contacts.firstOrNull()?.let(onContactClicked) ?: onAddUnrecognizedContact()
    }

    fun clear() {
        contacts.clear()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""

                val finalUserList = mutableListOf<MergedContact>()
                displayAddUnknownContactButton = true
                allContacts.forEach {
                    val standardizedEmail = it.email.standardize()
                    val matches = it.name.standardize().contains(searchTerm) || standardizedEmail.contains(searchTerm)

                    val displayNewContact = (matches && searchTerm == standardizedEmail && !usedContacts.contains(searchTerm))
                    if (displayNewContact) displayAddUnknownContactButton = false

                    val isAlreadyUsed = usedContacts.contains(standardizedEmail)
                    if (matches && !isAlreadyUsed) finalUserList.add(it)
                }

                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.count()
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                contacts = results.values as MutableList<MergedContact>
                notifyDataSetChanged()
            }
        }
    }

    fun filterField(text: CharSequence) {
        searchQuery = text.toString()
        filter.filter(text)
    }

    fun removeUsedEmail(email: String) = usedContacts.remove(email.standardize())

    fun addUsedContact(email: String) = usedContacts.add(email.standardize())

    private fun CharSequence.standardize(): String = toString().trim().lowercase()

    fun updateContacts(allContacts: List<MergedContact>) {
        this.allContacts = allContacts
    }

    private enum class ContactType(val id: Int) {
        KNOWN_CONTACT(0),
        UNKNOWN_CONTACT(1),
    }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
