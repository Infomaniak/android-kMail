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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.google.android.material.chip.Chip
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.utils.toggleChevron

class NewMessageFragment : Fragment() {

    private val binding: FragmentNewMessageBinding by lazy { FragmentNewMessageBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private var mails = MailboxInfoController.getMailboxesSync().map { it.email }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        with(binding) {
            fromMailAddress.text = MailData.currentMailboxFlow.value?.email
            fromMailAddress.setOnClickListener { it.showMenu() }

            displayChips()

            toAutocompleteInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    parentFragmentManager.commit {
                        setReorderingAllowed(true)
                        addSharedElement(toPrefix, "prefixTextView")
                        addSharedElement(toAutocompleteInput, "fieldTransition")
                        addSharedElement(toItemsChipGroup, "chipGroup")
                        replace(R.id.fragmentContainer, FieldFragment())
                        addToBackStack("mailWritingMain")
                    }
                }
            }

            chevron.setOnClickListener {
                advancedFields.isVisible = !advancedFields.isVisible
                chevron.toggleChevron(advancedFields.isGone)
            }

            return root
        }

    // TODO : Merge logic with field fragment?
    private fun FragmentNewMessageBinding.displayChips() {
        toItemsChipGroup.removeAllViews()
        // TODO: Merge logic of all 4 createChip.setOnClickListener
        for (recipient in viewModel.recipients) createChip(recipient).setOnClickListener {
            Log.e("gibran", "displayChips: chip ${recipient.name} got clicked", );
            Log.e("gibran", "displayChips - The value viewModel.recipients before is: ${viewModel.recipients.map { it.name }}")
            viewModel.recipients.remove(recipient)
//            availableUsersAdapter.alreadyUsedContactIds.remove(contact.id) // TODO : Remove email from list of forbbiden emails
            toItemsChipGroup.removeView(it)
            Log.e("gibran", "displayChips - The value viewModel.recipients before is: ${viewModel.recipients.map { it.name }}")
        }
    }

    private fun FragmentNewMessageBinding.createChip(contact: Contact): Chip {
        val chip = ChipContactBinding.inflate(layoutInflater).root.apply { text = contact.name }
        toItemsChipGroup.addView(chip)
        return chip
    }

    private fun View.showMenu() {
        val adapter = ArrayAdapter(context, com.google.android.material.R.layout.support_simple_spinner_dropdown_item, mails)
        ListPopupWindow(context).apply {
            setAdapter(adapter)
            anchorView = this@showMenu
            width = this@showMenu.width
            setOnItemClickListener { _, _, position, _ ->
                binding.fromMailAddress.text = mails[position]
                dismiss()
            }
        }.show()
    }
}
