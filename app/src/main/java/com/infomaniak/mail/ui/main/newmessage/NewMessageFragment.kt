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

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
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
    private var areAdvancedFieldsOpened = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        with(binding) {
            fromMailAddress.text = MailData.currentMailboxFlow.value?.email
            if (mails.count() > 1) fromMailAddress.setOnClickListener(::chooseFromAddress)
            else fromMailAddress.isClickable = false

            displayChips()

            toAutocompleteInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openFieldFragment() }
            toTransparentButton.setOnClickListener { openAdvancedFields() }
            chevron.setOnClickListener { openAdvancedFields() }

            return root
        }

    private fun chooseFromAddress(view: View) {
        val adapter = ArrayAdapter(view.context, com.google.android.material.R.layout.support_simple_spinner_dropdown_item, mails)
        ListPopupWindow(view.context).apply {
            setAdapter(adapter)
            anchorView = view
            width = view.width
            setOnItemClickListener { _, _, position, _ ->
                binding.fromMailAddress.text = mails[position]
                dismiss()
            }
        }.show()
    }

    private fun FragmentNewMessageBinding.displayChips() {
        refreshChips()
        updateSingleChipText()

        singleChip.root.setOnClickListener {
            removeRecipient(0)
            updateChipVisibility()
        }

        updateChipVisibility()
    }

    private fun FragmentNewMessageBinding.removeRecipient(index: Int) {
        val contact = viewModel.recipients[index]
        viewModel.recipients.remove(contact)
//        availableUsersAdapter.alreadyUsedContactIds.remove(contact.id) // TODO : Remove email from list of forbbiden emails
        toItemsChipGroup.removeViewAt(index)
        updateSingleChipText()
    }

    private fun FragmentNewMessageBinding.removeRecipient(contact: Contact) {
        val index = viewModel.recipients.indexOfFirst { it.id == contact.id }
        removeRecipient(index)
    }

    private fun FragmentNewMessageBinding.updateSingleChipText() {
        viewModel.recipients.firstOrNull()?.let { singleChip.root.text = it.name }
    }

    private fun FragmentNewMessageBinding.refreshChips() {
        toItemsChipGroup.removeAllViews()
        for (contact in viewModel.recipients) createChip(contact).setOnClickListener { _ -> removeRecipient(contact) }
    }

    private fun FragmentNewMessageBinding.createChip(contact: Contact): Chip {
        val chip = ChipContactBinding.inflate(layoutInflater).root.apply { text = contact.name }
        toItemsChipGroup.addView(chip)
        return chip
    }

    private fun FragmentNewMessageBinding.updateChipVisibility() {
        singleChipGroup.isVisible = !areAdvancedFieldsOpened && viewModel.recipients.isNotEmpty()
        toItemsChipGroup.isVisible = areAdvancedFieldsOpened
        toTransparentButton.isVisible = viewModel.recipients.isNotEmpty() && !areAdvancedFieldsOpened
        doNotAnimate(root) { plusOthers.isVisible = viewModel.recipients.count() > 1 && !areAdvancedFieldsOpened }
        plusOthers.text = "+${viewModel.recipients.count() - 1} others" // TODO : extract string
    }

    private fun doNotAnimate(parent: View, body: () -> Unit) {
        val lt: LayoutTransition = (parent as ViewGroup).layoutTransition
        lt.disableTransitionType(LayoutTransition.DISAPPEARING)
        body()
        lt.enableTransitionType(LayoutTransition.DISAPPEARING)
    }

    private fun FragmentNewMessageBinding.openFieldFragment() {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            addSharedElement(toPrefix, "prefixTextView")
            addSharedElement(toAutocompleteInput, "fieldTransition")
            addSharedElement(toItemsChipGroup, "chipGroup")
            replace(R.id.fragmentContainer, FieldFragment())
            addToBackStack("mailWritingMain")
        }
    }

    private fun FragmentNewMessageBinding.openAdvancedFields() {
        areAdvancedFieldsOpened = !areAdvancedFieldsOpened

        advancedFields.isVisible = areAdvancedFieldsOpened
        chevron.toggleChevron(!areAdvancedFieldsOpened)

        refreshChips()
        updateChipVisibility()
    }
}
