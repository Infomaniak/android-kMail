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
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.google.android.material.chip.ChipGroup
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.toggleChevron

class NewMessageFragment : Fragment() {

    private val binding: FragmentNewMessageBinding by lazy { FragmentNewMessageBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private var mailboxes = MailboxInfoController.getMailboxesSync()
    private var mails = mailboxes.map { it.email }
    private var selectedMailboxIndex = mailboxes.indexOfFirst { it.objectId == MailData.currentMailboxFlow.value?.objectId }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        with(binding) {
            fromMailAddress.text = mailboxes[selectedMailboxIndex].email
            if (mails.count() > 1) fromMailAddress.setOnClickListener(::chooseFromAddress)
            else fromMailAddress.isClickable = false

            displayChips()

            toTransparentButton.setOnClickListener { openAdvancedFields() }
            chevron.setOnClickListener { openAdvancedFields() }

            toAutocompleteInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openFieldFragment(TO) }
            ccAutocompleteInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openFieldFragment(CC) }
            bccAutocompleteInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openFieldFragment(BCC) }

            bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }
            setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }

            return root
        }

    private fun toggleEditor(hasFocus: Boolean) = (activity as NewMessageActivity).toggleEditor(hasFocus)

    private fun chooseFromAddress(view: View) {
        val adapter = ArrayAdapter(view.context, com.google.android.material.R.layout.support_simple_spinner_dropdown_item, mails)
        ListPopupWindow(view.context).apply {
            setAdapter(adapter)
            anchorView = view
            width = view.width
            setOnItemClickListener { _, _, position, _ ->
                binding.fromMailAddress.text = mails[position]
                selectedMailboxIndex = position
                dismiss()
            }
        }.show()
    }

    private fun setOnKeyboardListener(callback: (isOpened: Boolean) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(requireActivity().window.decorView) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            callback(isKeyboardVisible)
            insets
        }
    }

    //region Chips behavior
    private fun getContacts(field: FieldType): MutableList<Contact> =
        when (field) {
            TO -> viewModel.recipients
            CC -> viewModel.cc
            BCC -> viewModel.bcc
        }

    private fun getChipView(field: FieldType): ChipGroup =
        when (field) {
            TO -> binding.toItemsChipGroup
            CC -> binding.ccItemsChipGroup
            BCC -> binding.bccItemsChipGroup
        }

    private fun FragmentNewMessageBinding.displayChips() {
        refreshChips()
        updateSingleChipText()
        updateChipVisibility()

        singleChip.root.setOnClickListener {
            removeMail(TO, 0)
            updateChipVisibility()
        }
    }

    private fun FragmentNewMessageBinding.removeMail(field: FieldType, index: Int) {
        getContacts(field).removeAt(index)
        getChipView(field).removeViewAt(index)
        if (field == TO) updateSingleChipText()
    }

    private fun FragmentNewMessageBinding.removeMail(field: FieldType, contact: Contact) {
        val index = getContacts(field).indexOfFirst { it.id == contact.id }
        removeMail(field, index)
    }

    private fun FragmentNewMessageBinding.updateSingleChipText() {
        viewModel.recipients.firstOrNull()?.let { singleChip.root.text = it.name }
    }

    private fun FragmentNewMessageBinding.refreshChips() {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        for (contact in viewModel.recipients) createChip(TO, contact)
        for (contact in viewModel.cc) createChip(CC, contact)
        for (contact in viewModel.bcc) createChip(BCC, contact)
    }

    private fun FragmentNewMessageBinding.createChip(field: FieldType, contact: Contact) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            text = contact.name
            setOnClickListener { removeMail(field, contact) }
            getChipView(field).addView(this)
        }
    }

    private fun FragmentNewMessageBinding.updateChipVisibility() {
        singleChipGroup.isVisible = !viewModel.areAdvancedFieldsOpened && viewModel.recipients.isNotEmpty()
        toItemsChipGroup.isVisible = viewModel.areAdvancedFieldsOpened
        toTransparentButton.isVisible = viewModel.recipients.isNotEmpty() && !viewModel.areAdvancedFieldsOpened
        doNotAnimate(constraintLayout) {
            plusOthers.isVisible = viewModel.recipients.count() > 1 && !viewModel.areAdvancedFieldsOpened
        }
        plusOthers.text = "+${viewModel.recipients.count() - 1} others" // TODO : extract string
        advancedFields.isVisible = viewModel.areAdvancedFieldsOpened
    }

    private fun doNotAnimate(parent: View, body: () -> Unit) {
        val lt: LayoutTransition = (parent as ViewGroup).layoutTransition
        lt.disableTransitionType(LayoutTransition.DISAPPEARING)
        body()
        lt.enableTransitionType(LayoutTransition.DISAPPEARING)
    }

    private fun FragmentNewMessageBinding.openFieldFragment(fieldType: FieldType) {
        val (prefix, field, chips) = when (fieldType) {
            TO -> Triple(toPrefix, toAutocompleteInput, toItemsChipGroup)
            CC -> Triple(ccPrefix, ccAutocompleteInput, ccItemsChipGroup)
            BCC -> Triple(bccPrefix, bccAutocompleteInput, bccItemsChipGroup)
        }

        parentFragmentManager.commit {
            setReorderingAllowed(true)
            addSharedElement(prefix, fieldType.prefixTransition)
            addSharedElement(field, fieldType.fieldTransition)
            addSharedElement(chips, fieldType.chipsTransition)
            replace(R.id.fragmentContainer, FieldFragment(fieldType))
            addToBackStack("mailWritingMain")
        }
    }

    private fun FragmentNewMessageBinding.openAdvancedFields() {
        viewModel.areAdvancedFieldsOpened = !viewModel.areAdvancedFieldsOpened

        advancedFields.isVisible = viewModel.areAdvancedFieldsOpened
        chevron.toggleChevron(!viewModel.areAdvancedFieldsOpened)

        refreshChips()
        updateChipVisibility()
    }
    //endregion

    fun getFromMailbox(): Mailbox = mailboxes[selectedMailboxIndex]

    fun getSubject(): String = binding.subjectTextField.text.toString()

    fun getBody(): String = binding.bodyText.text.toString()

    enum class FieldType(
        @StringRes val displayedName: Int,
        val prefixTransition: String,
        val fieldTransition: String,
        val chipsTransition: String
    ) {
        TO(R.string.toTitle, "toPrefixTextView", "toFieldTransition", "toChipGroup"),
        CC(R.string.ccTitle, "ccPrefixTextView", "ccFieldTransition", "ccChipGroup"),
        BCC(R.string.bccTitle, "bccPrefixTextView", "bccFieldTransition", "bccChipGroup");
    }
}
