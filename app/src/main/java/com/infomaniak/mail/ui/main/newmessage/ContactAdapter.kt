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

import android.content.Context
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import com.infomaniak.lib.core.utils.firstOrEmpty
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.databinding.ItemContactBinding

class ContactAdapter(
    val context: Context,
    var allContacts: ArrayList<Contact> = arrayListOf(),
    var alreadyUsedContactIds: ArrayList<Int>,
    private val onItemClick: (item: Contact) -> Unit,
) : BaseAdapter(), Filterable {
    private var contacts: ArrayList<Contact> = allContacts

    init {
        cleanItemList()
    }

    fun addFirstAvailableItem(): Boolean {
        val item = contacts.firstOrNull()
        return if (item == null) {
            false
        } else {
            onItemClick(item)
            true
        }
    }

    private fun cleanItemList() = contacts.sortBy { it.name }

    override fun notifyDataSetChanged() {
        cleanItemList()
        super.notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contact = getItem(position)

        return (convertView ?: ItemContactBinding.inflate(inflater).root).apply {
            findViewById<TextView>(R.id.userName).text = contact.name
            findViewById<TextView>(R.id.userEmail).text = contact.emails[0] // TODO adapt to all emails?
            findViewById<ShapeableImageView>(R.id.userAvatar).loadAvatar(
                contact.id.toInt(),
                null,
                contact.name.firstOrEmpty().toString()
            )

            setOnClickListener { onItemClick(contact) }
        }
    }

    override fun getCount(): Int = contacts.count()

    override fun getItem(position: Int): Contact = contacts[position]

    override fun getItemId(position: Int): Long = getItem(position).id.toLong()

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchTerm = constraint?.standardize() ?: ""
                val finalUserList = allContacts
                    .filter { it.name.standardize().contains(searchTerm) || it.emails[0].standardize().contains(searchTerm) }
                    .filterNot { displayedItem -> alreadyUsedContactIds.any { it == displayedItem.id.toInt() } }
                return FilterResults().apply {
                    values = finalUserList
                    count = finalUserList.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                val searchTerm = constraint?.standardize()
                if (searchTerm?.isEmail() == true && !searchTerm.existsInAvailableItems()) {
                    contacts = arrayListOf()
                    notifyDataSetChanged()
                } else {
                    contacts = results.values as ArrayList<Contact> // Normal warning
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun CharSequence.standardize(): String = this.toString().trim().lowercase()

    private fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

    private fun String.existsInAvailableItems(): Boolean =
        allContacts.any { availableItem -> availableItem.emails[0].standardize() == this }
}
