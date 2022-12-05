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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.newMessage.ContactAdapter2.ContactViewHolder

class ContactAdapter2(
    private val allContacts: List<MergedContact>,
    private val usedContacts: MutableSet<String>,
    private val onContactClicked: (item: MergedContact) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
) : RecyclerView.Adapter<ContactViewHolder>(), Filterable {

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
        root.setOnClickListener { selectContact(contact) }
    }

    override fun getItemCount(): Int = contacts.count()

    override fun getItemId(position: Int): Long = contacts[position].id

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
        usedContacts.add(contact.email.standardize())
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val s = System.nanoTime()
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = allContacts
                    .filter {
                        val standardizedEmail = it.email.standardize()
                        (it.name.standardize().contains(searchTerm) || standardizedEmail.contains(searchTerm))
                                && !usedContacts.contains(standardizedEmail)
                    }


                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.count()
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val s = System.nanoTime()

                @Suppress("UNCHECKED_CAST")
                contacts = results.values as MutableList<MergedContact>

                // DiffUtil.calculateDiff(MergedContactsCallback(contacts, newContacts)).dispatchUpdatesTo(this@ContactAdapter2)
                // contacts = newContacts
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

    // class MergedContactsCallback(private val oldList: List<MergedContact>, private val newList: List<MergedContact>) : DiffUtil.Callback() {
    //     override fun getOldListSize(): Int = oldList.count()
    //     override fun getNewListSize(): Int = newList.count()
    //
    //     override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
    //         return oldList[oldItemPosition].id == newList[newItemPosition].id
    //     }
    //
    //     override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = true
    // }

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
