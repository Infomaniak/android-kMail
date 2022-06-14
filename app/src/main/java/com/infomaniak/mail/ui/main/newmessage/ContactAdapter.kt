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

import android.util.Patterns
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.firstOrEmpty
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.databinding.ItemContactBinding

class ContactAdapter(
    private val allContacts: List<Contact> = emptyList(),
    private val onItemClick: (item: Contact) -> Unit,
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>(), Filterable {
    private var contacts = ArrayList<Contact>()
    private var alreadyUsedContactIds = ArrayList<String>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int): Unit = with(holder.binding) {
        val contact = contacts[position]
        userName.text = contact.name
        userEmail.text = contact.emails[0] // TODO adapt to all emails?
        userAvatar.loadAvatar(contact.id.hashCode(), null, contact.name.firstOrEmpty().toString())
        root.setOnClickListener { onItemClick(contact) }
    }

    override fun getItemCount(): Int = contacts.count()

    override fun getItemId(position: Int): Long = contacts[position].id.hashCode().toLong()

    fun addFirstAvailableItem(): Boolean {
        val contact = contacts.firstOrNull()
        return if (contact == null) {
            false
        } else {
            onItemClick(contact)
            true
        }
    }

    fun clear() {
        contacts.clear()
        notifyDataSetChanged()
    }

    private fun orderItemList() = contacts.sortBy { it.name }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = allContacts
                    .filter { it.name.standardize().contains(searchTerm) || it.emails[0].standardize().contains(searchTerm) }
                    .filterNot { displayedItem -> alreadyUsedContactIds.any { it == displayedItem.id } }
                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val searchTerm = constraint?.standardize()
                contacts = if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                    arrayListOf()
                } else {
                    results.values as ArrayList<Contact> // Normal warning
                }
                orderItemList()
                notifyDataSetChanged()
            }
        }
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

    private fun String.existsInAvailableItems(): Boolean =
        allContacts.any { availableItem -> availableItem.emails[0].standardize() == this }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
