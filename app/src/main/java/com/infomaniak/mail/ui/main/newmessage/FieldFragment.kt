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
import androidx.transition.TransitionInflater
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentFieldBinding

class FieldFragment : Fragment() {

    private val binding: FragmentFieldBinding by lazy { FragmentFieldBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private lateinit var contactAdapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = with(binding) {
        ccAutocompleteInput.requestFocus()

        displayChips()

//        val cont1 = Contact().apply { name = "Gibran Chevalley"; emails = realmListOf("gibran.chevalley@infomaniak.com"); uuid = "22132021" }
//        val cont2 = Contact().apply { name = "Kevin Boulongne"; emails = realmListOf("kevin.boulongne@infomaniak.com"); uuid = "233871341" }
//        val cont3 = Contact().apply { name = "Fabian Devel"; emails = realmListOf("fabian.devel@infomaniak.com"); uuid = "295232131" }
//        val cont4 = Contact().apply { name = "Abdourahamane Boinaidi"; emails = realmListOf("abdourahamane.boinaidi@infomaniak.com"); uuid = "296232131" }
//        val contactAdapter = Contact2Adapter(arrayListOf(cont1, cont2, cont3, cont4)) {
//        contactAdapter = ContactAdapter(MailData.contactsFlow.value ?: emptyList()) {
//            ccAutocompleteInput.setText("")
//            addRecipient(it)
//        }

        val allContacts = MailData.contactsFlow.value ?: emptyList()
        val alreadyUsedContactIds = ArrayList(viewModel.recipients.map { it.id })

        contactAdapter = ContactAdapter(allContacts, alreadyUsedContactIds) {
            binding.ccAutocompleteInput.setText("")
            binding.addRecipient(it)
        }
        autoCompleteRecyclerView.adapter = contactAdapter

        ccAutocompleteInput.apply {
            doOnTextChanged { text, _, _, _ ->
                if ((text?.count() ?: 0) > 0) contactAdapter.filter.filter(text)
                else contactAdapter.clear()
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) contactAdapter.addFirstAvailableItem()
                true // Keep keyboard open
            }
        }
        return root
    }

    private fun FragmentFieldBinding.displayChips() {
        for (recipient in viewModel.recipients) createChip(recipient)
    }

    private fun FragmentFieldBinding.addRecipient(contact: Contact) {
        viewModel.recipients.add(contact)
        createChip(contact)
    }

    private fun FragmentFieldBinding.createChip(contact: Contact) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            text = contact.name
            setOnClickListener {
                viewModel.recipients.remove(contact)
                contactAdapter.removeUsedContact(contact)
                ccItemsChipGroup.removeView(it)
            }
            ccItemsChipGroup.addView(this)
        }
    }
}
