/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.newmessage

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.firstOrEmpty
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.isEmail

class ContactAdapter(
    private val allContacts: List<UiContact> = emptyList(),
    private val toAlreadyUsedContactIds: MutableList<String> = mutableListOf(),
    private val ccAlreadyUsedContactIds: MutableList<String> = mutableListOf(),
    private val bccAlreadyUsedContactIds: MutableList<String> = mutableListOf(),
    private val onItemClick: (item: UiContact, field: FieldType) -> Unit,
    private val addUnrecognizedContact: (field: FieldType) -> Boolean,
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>(), Filterable {
    private var contacts = mutableListOf<UiContact>()
    private var currentField: FieldType = TO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int): Unit = with(holder.binding) {
        val contact = contacts[position]
        userName.text = contact.name
        userEmail.text = contact.email
        userAvatar.loadAvatar(contact.email.hashCode(), null, contact.name?.firstOrEmpty().toString())
        root.setOnClickListener { selectContact(contact) }
    }

    override fun getItemCount(): Int = contacts.count()

    fun addFirstAvailableItem(): Boolean {
        val contact = contacts.firstOrNull()
        return if (contact == null) {
            Log.e("gibran", "addFirstAvailableItem: unrecognized email !!!", );
            addUnrecognizedContact(currentField)
        } else {
            selectContact(contact)
            true
        }
    }

    fun clear() {
        contacts.clear()
        notifyDataSetChanged()
    }

    private fun orderItemList() = contacts.sortBy { it.name }

    private fun getAlreadyUsedEmails(field: FieldType) = when (field) {
        TO -> toAlreadyUsedContactIds
        CC -> ccAlreadyUsedContactIds
        BCC -> bccAlreadyUsedContactIds
    }

    private fun selectContact(contact: UiContact) {
        onItemClick(contact, currentField)
        getAlreadyUsedEmails(currentField).add(contact.email)
    }

    fun removeUsedContact(contact: UiContact) {
        getAlreadyUsedEmails(currentField).remove(contact.email)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = allContacts
                    .filter {
                        it.name?.standardize()?.contains(searchTerm) == true || it.email.standardize().contains(searchTerm)
                    }
                    .filterNot { displayedItem -> getAlreadyUsedEmails(currentField).any { it == displayedItem.email } }
                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val searchTerm = constraint?.standardize()
                contacts = if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                    mutableListOf()
                } else {
                    results.values as MutableList<UiContact> // Normal warning
                }
                orderItemList()
                notifyDataSetChanged()
            }
        }
    }

    fun filterField(selectedField: FieldType, text: CharSequence) {
        currentField = selectedField
        filter.filter(text)
    }

    fun removeEmail(field: FieldType, email: String) {
        getAlreadyUsedEmails(field).remove(email)
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.existsInAvailableItems(): Boolean =
        allContacts.any { availableItem -> availableItem.email.standardize() == this }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
