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
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter2.ContactViewHolder
import com.infomaniak.mail.utils.isEmail

class ContactAdapter2(
    private val allContacts: List<MergedContact>,
    private val usedContacts: MutableList<String>,
    private val onContactClicked: (item: MergedContact) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
) : RecyclerView.Adapter<ContactViewHolder>(), Filterable {

    private var contacts = mutableListOf<MergedContact>()

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
        contacts.firstOrNull()?.let(::selectContact) ?: onAddUnrecognizedContact()
    }

    fun clear() {
        contacts.clear()
        notifyDataSetChanged()
    }

    private fun orderItemList() = contacts.sortBy { it.name }

    private fun selectContact(contact: MergedContact) {
        onContactClicked(contact)
        usedContacts.add(contact.email)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                Log.e("gibran", "performFiltering - searchTerm: ${searchTerm}")
                val finalUserList = allContacts
                    .filter { it.name.standardize().contains(searchTerm) || it.email.standardize().contains(searchTerm) }
                    .filterNot { displayedItem -> usedContacts.any { it == displayedItem.email } }

                Log.e("gibran", "performFiltering - finalUserList: ${finalUserList}")

                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val searchTerm = constraint?.standardize()
                Log.e("gibran", "publishResults - searchTerm: ${searchTerm}")
                contacts = if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                    mutableListOf()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    results.values as MutableList<MergedContact>
                }
                Log.e("gibran", "publishResults - contacts: ${contacts}")
                orderItemList()
                notifyDataSetChanged()
            }
        }
    }

    fun filterField(text: CharSequence) {
        filter.filter(text)
    }

    fun removeEmail(email: String) {
        usedContacts.remove(email)
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.existsInAvailableItems(): Boolean {
        return allContacts.any { availableItem -> availableItem.email.standardize() == this } // TODO : Opti
    }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
