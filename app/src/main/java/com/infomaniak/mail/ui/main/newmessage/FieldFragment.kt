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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.databinding.FragmentFieldBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.isEmail

class FieldFragment : Fragment() {

    private val binding: FragmentFieldBinding by lazy { FragmentFieldBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private val navigationArgs: FieldFragmentArgs by navArgs()
    private var contacts = mutableListOf<UiContact>()
    private lateinit var contactAdapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        super.onCreate(savedInstanceState)
        contacts = when (navigationArgs.field as NewMessageFragment.FieldType) {
            TO -> viewModel.recipients
            CC -> viewModel.cc
            BCC -> viewModel.bcc
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = with(binding) {
        prefix.setText(navigationArgs.field.displayedName)
        autocompleteInput.apply {
            setText(navigationArgs.text)
            requestFocus()

            doOnTextChanged { text, _, _, _ ->
                if (text?.isEmpty() == true) findNavController().popBackStack()
                else if ((text?.trim()?.count() ?: 0) > 0) contactAdapter.filter.filter(text)
                else contactAdapter.clear()
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) contactAdapter.addFirstAvailableItem()
                true // Keep keyboard open
            }
        }

//        displayChips()

        //region Test contacts
//        val cont1 = Contact().apply { name = "Gibran Chevalley"; emails = realmListOf("gibran.chevalley@infomaniak.com"); id = "22132021" }
//        val cont2 = Contact().apply { name = "Kevin Boulongne"; emails = realmListOf("kevin.boulongne@infomaniak.com"); id = "233871341" }
//        val cont3 = Contact().apply { name = "Fabian Devel"; emails = realmListOf("fabian.devel@infomaniak.com"); id = "295232131" }
//        val cont4 = Contact().apply { name = "Abdourahamane Boinaidi"; emails = realmListOf("abdourahamane.boinaidi@infomaniak.com"); id = "296232131" }
//        vsl allContacts = listOf(cont1, cont2, cont3, cont4)
        //endregion
        val allContacts = MailData.contactsFlow.value?.map { UiContact(it.emails[0], it.name) } ?: emptyList()
        val toAlreadyUsedContactMails = (viewModel.recipients.map { it.email }).toMutableList()
        val ccAlreadyUsedContactMails = (viewModel.cc.map { it.email }).toMutableList()
        val bccAlreadyUsedContactMails = (viewModel.bcc.map { it.email }).toMutableList()

        contactAdapter = ContactAdapter(
            allContacts,
            navigationArgs.field,
            toAlreadyUsedContactMails,
            ccAlreadyUsedContactMails,
            bccAlreadyUsedContactMails,
            {
                autocompleteInput.setText("")
                addMail(it)
            },
            {
                val isEmail = addUnrecognizedMail()
                if (isEmail) autocompleteInput.setText("")
                isEmail
            }
        )
        contactAdapter.filter.filter(navigationArgs.text)
        autoCompleteRecyclerView.adapter = contactAdapter

        return root
    }

    private fun addUnrecognizedMail(): Boolean = with(binding) {
        val input = autocompleteInput.text.toString()
        val isEmail = input.isEmail()
        if (isEmail) contacts.add(UiContact(input))
        return isEmail
    }

//    private fun FragmentFieldBinding.displayChips() {
//        for (recipient in contacts) createChip(recipient)
//    }

    private fun addMail(contact: UiContact) {
        contacts.add(contact)
//        createChip(contact)
    }

//    private fun FragmentFieldBinding.createChip(contact: Contact) {
//        ChipContactBinding.inflate(layoutInflater).root.apply {
//            text = contact.name
//            setOnClickListener {
//                viewModel.recipients.remove(contact)
//                contactAdapter.removeUsedContact(contact)
//                itemsChipGroup.removeView(it)
//            }
//            itemsChipGroup.addView(this)
//        }
//    }
}
