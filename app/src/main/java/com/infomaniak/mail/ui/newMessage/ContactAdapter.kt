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
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.AutocompletableAdressBook
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.AutocompletableContact
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.AutocompletableGroup
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.UnknownContact
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder
import com.infomaniak.mail.utils.extensions.standardize

@SuppressLint("NotifyDataSetChanged")
class ContactAdapter(
    private val usedEmails: MutableSet<String>,
    private val onContactClicked: (item: ContactAutocompletable) -> Unit,
    private val onAddUnrecognizedContact: () -> Unit,
    private val snackbarManager: SnackbarManager,
    private var getAddressBookWithGroup: ((ContactGroup) -> AddressBook?)?
) : Adapter<ContactViewHolder>() {

    private var allContacts: List<ContactAutocompletable> = emptyList()
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
        return getItemViewTypeEnum(position).id
    }

    private fun getItemViewTypeEnum(position: Int): ContactType {
        return if (position < matchedContacts.count()) {
            if (matchedContacts[position].contact is MergedContact) {
                AutocompletableContact
            } else if (matchedContacts[position].contact is AddressBook) {
                AutocompletableAdressBook
            } else {
                AutocompletableGroup
            }
        } else {
            UnknownContact
        }
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        if (getItemViewType(position) == AutocompletableContact.id) {
            bindContact(position, matchedContacts[position].contact as MergedContact)
        } else if (getItemViewType(position) == AutocompletableAdressBook.id) {
            bindAdressBook(position, matchedContacts[position].contact as AddressBook)
        } else if (getItemViewType(position) == AutocompletableGroup.id) {
            bindGroup(position, matchedContacts[position].contact as ContactGroup)
        } else {
            bindAddNewUser()
        }
    }

    private fun ItemContactBinding.bindContact(position: Int, contact: MergedContact) = with(matchedContacts[position]) {
        contactDetails.apply {
            setMergedContact(contact)
            highlight(nameMatchedStartIndex, emailMatchedStartIndex, searchQuery.standardize().count())
        }
        val isAlreadyUsed = usedEmails.contains(contact.email.standardize())

        if (!isAlreadyUsed) root.setOnClickListener { onContactClicked(contact) } else root.setOnClickListener(null)
        setVisuallyUsed(isAlreadyUsed)
    }

    private fun ItemContactBinding.bindAdressBook(position: Int, contact: AddressBook) = with(matchedContacts[position]) {
        contactDetails.apply {
            setAddressBook(contact)
            highlight(
                nameMatchedStartIndex,
                emailMatchedStartIndex,
                searchQuery.standardize().count(),
                prefixSizeOfName = getLengthStringTitle(context.getString(R.string.addressBookTitle)) ?: 0,
                prefixSizeOfEmail = getLengthStringTitle(context.getString(R.string.organizationName)) ?: 0
            )
        }
        root.setOnClickListener { onContactClicked(contact) }
    }

    private fun ItemContactBinding.bindGroup(position: Int, contact: ContactGroup) = with(matchedContacts[position]) {
        contactDetails.apply {
            setContactGroup(contact, getAddressBookWithGroup?.invoke(contact))
            highlight(
                nameMatchedStartIndex,
                emailMatchedStartIndex,
                searchQuery.standardize().count(),
                prefixSizeOfName = getLengthStringTitle(context.getString(R.string.groupContactsTitle)) ?: 0,
                prefixSizeOfEmail = getLengthStringTitle(context.getString(R.string.addressBookTitle)) ?: 0
            )
        }
        root.setOnClickListener { onContactClicked(contact) }
    }

    fun getLengthStringTitle(title: String): Int? {
        val regex = """.*: """.toRegex()
        return regex.find(title)?.groups[0]?.value?.length
    }

    private fun ItemContactBinding.setVisuallyUsed(isVisuallyUsed: Boolean) {
        greyedOutState.isVisible = isVisuallyUsed
        root.isEnabled = !isVisuallyUsed
    }

    private fun ItemContactBinding.bindAddNewUser() {
        contactDetails.setAutocompleteUnknownContact(searchQuery)
        root.setOnClickListener {
            trackNewMessageEvent(MatomoName.AddNewRecipient)
            if (usedEmails.contains(searchQuery)) {
                snackbarManager.setValue(context.getString(R.string.addUnknownRecipientAlreadyUsed))
            } else {
                onAddUnrecognizedContact()
            }
        }
    }

    override fun getItemCount(): Int = matchedContacts.count() + if (displayAddUnknownContactButton) 1 else 0

    override fun getItemId(position: Int): Long {
        val viewType = getItemViewTypeEnum(position)
        val contactIdHash = if (viewType == UnknownContact) 0 else matchedContacts[position].contact.contactId.hashCode()
        return (viewType.id.toLong() shl Int.SIZE_BITS) + contactIdHash
    }

    fun addFirstAvailableItem() {
        matchedContacts.firstOrNull()?.let { onContactClicked(it.contact as MergedContact) } ?: onAddUnrecognizedContact()
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
                if (contact is MergedContact) {
                    val nameMatchedIndex = contact.name.standardize().indexOf(searchTerm)
                    val standardizedEmail = contact.email.standardize()
                    val emailMatchedIndex = standardizedEmail.indexOf(searchTerm)
                    val matches = nameMatchedIndex >= 0 || emailMatchedIndex >= 0

                    val displayNewContact = (matches && searchTerm == standardizedEmail && !usedEmails.contains(searchTerm))
                    if (displayNewContact) displayAddUnknownContactButton = false

                    if (matches) finalUserList.add(MatchedContact(contact, nameMatchedIndex, emailMatchedIndex))

                    if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break
                } else if (contact is AddressBook) {
                    val nameMatchedIndex = contact.name.standardize().indexOf(searchTerm)
                    val standardizedEmail = contact.organization.standardize()
                    val emailMatchedIndex = standardizedEmail.indexOf(searchTerm)
                    val matches = nameMatchedIndex >= 0 || emailMatchedIndex >= 0

                    val displayNewContact = (matches && searchTerm == standardizedEmail && !usedEmails.contains(searchTerm))
                    if (displayNewContact) displayAddUnknownContactButton = false

                    if (matches) finalUserList.add(MatchedContact(contact, nameMatchedIndex, emailMatchedIndex))

                    if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break

                } else if (contact is ContactGroup) {
                    val nameMatchedIndex = contact.name.standardize().indexOf(searchTerm)
                    val addressBook: AddressBook = getAddressBookWithGroup?.invoke(contact)!!

                    val standardizedEmail =
                        if (addressBook.isDynamicOrganisationMemberDirectory == true) {
                            addressBook.organization.standardize()
                        } else {
                            addressBook.name.standardize()
                        }
                    val emailMatchedIndex = standardizedEmail.indexOf(searchTerm)
                    val matches = nameMatchedIndex >= 0 || emailMatchedIndex >= 0

                    val displayNewContact = (matches && searchTerm == standardizedEmail && !usedEmails.contains(searchTerm))
                    if (displayNewContact) displayAddUnknownContactButton = false

                    if (matches) finalUserList.add(MatchedContact(contact, nameMatchedIndex, emailMatchedIndex))

                    if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break
                }
            }
            return finalUserList.sortedWith(
                compareByDescending<MatchedContact> { it.contact.contactId }
            ).toMutableList()
        }

        searchQuery = text.toString()
        matchedContacts = performFiltering(text)
        notifyDataSetChanged()
    }

    fun removeUsedEmail(email: String): Boolean {
        return usedEmails.remove(email.standardize()).also { isSuccess ->
            if (isSuccess) {
                matchedContacts.forEachIndexed { index, matchedContact ->
                    if (matchedContact.contact is MergedContact) {
                        if (matchedContact.contact.email == email) notifyItemChanged(index)
                    }
                }
            }
        }
    }

    fun addUsedContact(email: String) = usedEmails.add(email.standardize())

    fun updateContacts(allContacts: List<ContactAutocompletable>) {
        this.allContacts = allContacts
    }

    private enum class ContactType(val id: Int) {
        AutocompletableContact(0),
        AutocompletableGroup(1),
        AutocompletableAdressBook(2),
        UnknownContact(3),
    }

    private data class MatchedContact(
        val contact: ContactAutocompletable,
        val nameMatchedStartIndex: Int,
        val emailMatchedStartIndex: Int,
    )

    companion object {
        private const val MAX_AUTOCOMPLETE_RESULTS = 10
    }

    class ContactViewHolder(val binding: ItemContactBinding) : ViewHolder(binding.root)
}
