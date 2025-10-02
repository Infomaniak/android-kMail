/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ItemContactBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactType.AutocompletableAddressBook
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
        return getContactTypeByItemPosition(position).id
    }

    private fun getContactTypeByItemPosition(position: Int): ContactType {
        if (position >= matchedContacts.count()) return UnknownContact

        return when (matchedContacts[position].contact) {
            is MergedContact -> AutocompletableContact
            is AddressBook -> AutocompletableAddressBook
            else -> AutocompletableGroup
        }

    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = with(holder.binding) {
        when (getItemViewType(position)) {
            AutocompletableContact.id -> bindContact(position)
            AutocompletableAddressBook.id -> bindAddressBook(position)
            AutocompletableGroup.id -> bindGroup(position)
            else -> bindAddNewUser()
        }
    }

    private fun ItemContactBinding.bindContact(position: Int) = with(matchedContacts[position]) {
        val mergedContact = contact as MergedContact
        contactDetails.apply {
            setMergedContact(mergedContact)
            highlight(nameMatchedStartIndex, emailMatchedStartIndex, searchQuery.standardize().count())
        }

        val isAlreadyUsed = usedEmails.contains(mergedContact.email.standardize())
        if (!isAlreadyUsed) root.setOnClickListener { onContactClicked(mergedContact) } else root.setOnClickListener(null)

        setVisuallyUsed(isAlreadyUsed)
    }

    private fun ItemContactBinding.bindAddressBook(position: Int) = with(matchedContacts[position]) {
        contactDetails.apply {
            setAddressBook(contact as AddressBook)
            highlight(
                nameStartIndex = nameMatchedStartIndex,
                emailStartIndex = emailMatchedStartIndex,
                length = searchQuery.standardize().count(),
                prefixSizeOfName = context.getPrefixSizeFromRes(R.string.addressBookTitle),
                prefixSizeOfEmail = context.getPrefixSizeFromRes(R.string.organizationName),
            )
        }
        root.setOnClickListener { onContactClicked(contact) }
    }

    private fun ItemContactBinding.bindGroup(position: Int) = with(matchedContacts[position]) {
        val contactGroup = contact as ContactGroup
        contactDetails.apply {
            setContactGroup(contactGroup, getAddressBookWithGroup?.invoke(contactGroup))
            highlight(
                nameStartIndex = nameMatchedStartIndex,
                emailStartIndex = emailMatchedStartIndex,
                length = searchQuery.standardize().count(),
                prefixSizeOfName = context.getPrefixSizeFromRes(R.string.groupContactsTitle),
                prefixSizeOfEmail = context.getPrefixSizeFromRes(R.string.addressBookTitle),
            )
        }
        root.setOnClickListener { onContactClicked(contactGroup) }
    }

    private fun Context.getPrefixSizeFromRes(@StringRes titleRes: Int): Int {
        return getLengthStringTitle(title = getString(titleRes)) ?: 0
    }

    private fun getLengthStringTitle(title: String): Int? = Regex(".*: ").find(title)?.groups[0]?.value?.length

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
        // To check that each contactautocomplatable has a different id, even if the types are different,
        // we concatenate their ids and types
        val viewType = getContactTypeByItemPosition(position)
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

    private fun getMatchedContact(
        contact: ContactAutocompletable,
        nameMatched: String,
        emailMatched: String,
        searchTerm: String,
    ): MatchedContact? {
        val nameMatchedIndex = nameMatched.standardize().indexOf(searchTerm)
        val standardizedEmail = emailMatched.standardize()
        val emailMatchedIndex = standardizedEmail.indexOf(searchTerm)
        val matches = nameMatchedIndex >= 0 || emailMatchedIndex >= 0

        val displayNewContact = (matches && searchTerm == standardizedEmail && !usedEmails.contains(searchTerm))
        if (displayNewContact) displayAddUnknownContactButton = false

        return if (matches) {
            MatchedContact(
                contact = contact,
                nameMatchedStartIndex = nameMatchedIndex,
                emailMatchedStartIndex = emailMatchedIndex,
            )
        } else {
            null
        }
    }

    private fun performFiltering(constraint: CharSequence): List<MatchedContact> {
        val searchTerm = constraint.standardize()

        val finalUserList = mutableListOf<MatchedContact>()
        displayAddUnknownContactButton = true
        for (contact in allContacts) {
            val matchedContact = getMatchedContact(
                contact = contact,
                nameMatched = contact.name,
                emailMatched = getEmailMatched(contact),
                searchTerm = searchTerm,
            )
            if (matchedContact != null) finalUserList.add(matchedContact)
            if (finalUserList.count() >= MAX_AUTOCOMPLETE_RESULTS) break
        }

        return finalUserList.sortedWith(
            compareByDescending { it.contact.contactId }
        ).toMutableList()
    }

    private fun getEmailMatched(contact: ContactAutocompletable): String {
        return when (contact) {
            is MergedContact -> contact.email
            is AddressBook -> contact.organization
            is ContactGroup -> {
                getAddressBookWithGroup?.invoke(contact)?.let { addressBook ->
                    if (addressBook.isDynamicOrganisationMemberDirectory) {
                        addressBook.organization.standardize()
                    } else {
                        addressBook.name.standardize()
                    }
                }
            }
            else -> null
        } ?: ""
    }

    fun searchContacts(text: CharSequence) {
        searchQuery = text.toString()
        matchedContacts = performFiltering(text)
        notifyDataSetChanged()
    }

    fun removeUsedEmail(email: String): Boolean {
        return usedEmails.remove(email.standardize()).also { isSuccess ->
            if (isSuccess) {
                matchedContacts.forEachIndexed { index, matchedContact ->
                    if (matchedContact.contact is MergedContact && matchedContact.contact.email == email) notifyItemChanged(index)
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
        AutocompletableAddressBook(2),
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
