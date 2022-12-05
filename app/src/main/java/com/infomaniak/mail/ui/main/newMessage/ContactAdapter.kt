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
package com.infomaniak.mail.ui.main.newMessage

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter.ContactViewHolder
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.isEmail
import kotlin.math.log

class ContactAdapter(
    private val allContacts: List<MergedContact> = emptyList(),
    private val toUsedEmails: MutableList<String> = mutableListOf(),
    private val ccUsedEmails: MutableList<String> = mutableListOf(),
    private val bccUsedEmails: MutableList<String> = mutableListOf(),
    private val onItemClick: (item: MergedContact, field: FieldType) -> Unit,
    private val addUnrecognizedContact: (field: FieldType) -> Unit,
) : RecyclerView.Adapter<ContactViewHolder>(), Filterable {

    private var contacts = mutableListOf<MergedContact>()
    private var currentField: FieldType = TO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int): Unit = with(holder.binding) {
        val contact = contacts[position]
        userName.text = contact.name
        userEmail.text = contact.email
        userAvatar.loadAvatar(contact)
        root.setOnClickListener { selectContact(contact) }
    }

    override fun getItemCount(): Int = contacts.count()

    fun addFirstAvailableItem() {
        contacts.firstOrNull()?.let(::selectContact) ?: addUnrecognizedContact(currentField)
    }

    fun clear() {
        contacts.clear()
        notifyDataSetChanged()
    }

    private fun orderItemList() = contacts.sortBy { it.name }

    fun getUsedEmails(field: FieldType) = when (field) {
        TO -> toUsedEmails
        CC -> ccUsedEmails
        BCC -> bccUsedEmails
    }

    private fun selectContact(contact: MergedContact) {
        onItemClick(contact, currentField)
        getUsedEmails(currentField).add(contact.email)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = allContacts
                    .filter { it.name.standardize().contains(searchTerm) || it.email.standardize().contains(searchTerm) }
                    .filterNot { displayedItem -> getUsedEmails(currentField).any { it == displayedItem.email } }

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
                    @Suppress("UNCHECKED_CAST")
                    results.values as MutableList<MergedContact>
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
        getUsedEmails(field).remove(email)
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.existsInAvailableItems(): Boolean {
        return allContacts.any { availableItem -> availableItem.email.standardize() == this }
    }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
