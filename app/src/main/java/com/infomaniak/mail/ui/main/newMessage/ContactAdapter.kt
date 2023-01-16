/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.ui.main.newMessage

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter.ContactViewHolder

@SuppressLint("NotifyDataSetChanged")
class ContactAdapter(
    private val usedContacts: MutableSet<String>,
    private val onContactClicked: (item: MergedContact) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
) : RecyclerView.Adapter<ContactViewHolder>(), Filterable {

    private var allContacts: List<MergedContact> = emptyList()
    private var contacts = mutableListOf<MergedContact>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int): Unit = with(holder.binding) {
        val contact = contacts[position]
        userName.text = contact.name
        userEmail.text = contact.email
        userAvatar.loadAvatar(contact)
        root.setOnClickListener { onContactClicked(contact) }
    }

    override fun getItemCount(): Int = contacts.count()

    override fun getItemId(position: Int): Long = contacts[position].id!!

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

                val finalUserList = allContacts.filter {
                    val standardizedEmail = it.email.standardize()
                    val isFound = it.name.standardize().contains(searchTerm) || standardizedEmail.contains(searchTerm)
                    val isAlreadyUsed = usedContacts.contains(standardizedEmail)
                    isFound && !isAlreadyUsed
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
        filter.filter(text)
    }

    fun removeUsedEmail(email: String) = usedContacts.remove(email.standardize())

    fun addUsedContact(email: String) = usedContacts.add(email.standardize())

    private fun CharSequence.standardize(): String = toString().trim().lowercase()

    fun updateContacts(allContacts: List<MergedContact>) {
        this.allContacts = allContacts
    }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
