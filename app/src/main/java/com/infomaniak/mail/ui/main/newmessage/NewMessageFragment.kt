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
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.isEmail
import com.infomaniak.mail.utils.toggleChevron

class NewMessageFragment : Fragment() {

    private val binding: FragmentNewMessageBinding by lazy { FragmentNewMessageBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private var mailboxes = MailboxInfoController.getMailboxesSync(AccountUtils.currentUserId)
    private var mails = mailboxes.map { it.email }
    private var selectedMailboxIndex = mailboxes.indexOfFirst { it.objectId == MailData.currentMailboxFlow.value?.objectId }
    private var isAutocompletionOpened = false
    private lateinit var contactAdapter: ContactAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = with(binding) {
        setupFromField()
        displayChips()

        toTransparentButton.setOnClickListener {
            viewModel.areAdvancedFieldsOpened = !viewModel.areAdvancedFieldsOpened
            openAdvancedFields()
        }
        chevron.setOnClickListener {
            viewModel.areAdvancedFieldsOpened = !viewModel.areAdvancedFieldsOpened
            openAdvancedFields()
        }

        enableAutocomplete(TO)
        enableAutocomplete(CC)
        enableAutocomplete(BCC)

        bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }
        setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }

        viewModel.editorAction.observe(requireActivity()) {
            var selectedText = ""
            bodyText.apply { selectedText = text?.substring(selectionStart, selectionEnd) ?: "" }

            when (it) {
                // TODO: Replace logs with actual code
                EditorAction.ATTACHMENT -> Log.e("gibran", "ATTACHMENT")
                EditorAction.CAMERA -> Log.e("gibran", "CAMERA")
                EditorAction.LINK -> Log.e("gibran", "LINK")
                EditorAction.CLOCK -> Log.e("gibran", "CLOCK")
                EditorAction.BOLD -> Log.e("gibran", "BOLD")
                EditorAction.ITALIC -> Log.e("gibran", "ITALIC")
                EditorAction.UNDERLINE -> Log.e("gibran", "UNDERLINE")
                EditorAction.STRIKE_THROUGH -> Log.e("gibran", "STRIKE_THROUGH")
                EditorAction.UNORDERED_LIST -> Log.e("gibran", "UNORDERED_LIST")
                null -> Unit
            }
        }

        val allContacts = MailData.contactsFlow.value?.map { UiContact(it.emails.first(), it.name) } ?: emptyList()
        val toAlreadyUsedContactMails = (viewModel.recipients.map { it.email }).toMutableList()
        val ccAlreadyUsedContactMails = (viewModel.cc.map { it.email }).toMutableList()
        val bccAlreadyUsedContactMails = (viewModel.bcc.map { it.email }).toMutableList()

        contactAdapter = ContactAdapter(
            allContacts,
            toAlreadyUsedContactMails,
            ccAlreadyUsedContactMails,
            bccAlreadyUsedContactMails,
            { contact, field ->
                getInputView(field).setText("")
                getContacts(field).add(contact)
                createChip(field, contact)
            },
            { field ->
                // TODO : Update AlreadyUsedContactIds in Adapter with the unrecognized email
                val isEmail = addUnrecognizedMail(field)
                if (isEmail) {
                    getInputView(field).setText("")
                }
                isEmail
            }
        )
        autoCompleteRecyclerView.adapter = contactAdapter

        return root
    }

    private fun addUnrecognizedMail(fieldType: FieldType): Boolean {
        val input = getInputView(fieldType).text.toString()
        val isEmail = input.isEmail()
        if (isEmail) {
            val contact = UiContact(input)
            getContacts(fieldType).add(contact)
            createChip(fieldType, contact)
        }
        return isEmail
    }

    private fun setupFromField() = with(binding) {
        fromMailAddress.text = mailboxes[selectedMailboxIndex].email
        if (mails.count() > 1) {
            fromMailAddress.apply {
                setOnClickListener(::chooseFromAddress)
                isClickable = true
                isFocusable = true
            }
        }
    }

    private fun enableAutocomplete(field: FieldType) {
        getInputView(field).apply {

            doOnTextChanged { text, _, _, _ ->
                if (text?.isNotEmpty() == true) {
                    if ((text.trim().count()) > 0) contactAdapter.filterField(field, text)
                    else contactAdapter.clear()
                    if (!isAutocompletionOpened) openAutocompletionView(field, text)
                } else if (isAutocompletionOpened) {
                    closeAutocompletionView(field)
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) contactAdapter.addFirstAvailableItem()
                true // Keep keyboard open
            }
        }
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
    private fun getContacts(field: FieldType): MutableList<UiContact> =
        when (field) {
            TO -> viewModel.recipients
            CC -> viewModel.cc
            BCC -> viewModel.bcc
        }

    private fun getChipGroup(field: FieldType): ChipGroup =
        when (field) {
            TO -> binding.toItemsChipGroup
            CC -> binding.ccItemsChipGroup
            BCC -> binding.bccItemsChipGroup
        }

    private fun getInputView(field: FieldType): MaterialAutoCompleteTextView =
        when (field) {
            TO -> binding.toAutocompleteInput
            CC -> binding.ccAutocompleteInput
            BCC -> binding.bccAutocompleteInput
        }

    private fun displayChips() = with(binding) {
        refreshChips()
        updateSingleChipText()
        updateChipVisibility()

        singleChip.root.setOnClickListener {
            removeMail(TO, 0)
            updateChipVisibility()
        }
    }

    private fun removeMail(field: FieldType, index: Int) {
        val email = getContacts(field)[index].email
        contactAdapter.removeEmail(field, email)
        getContacts(field).removeAt(index)
        getChipGroup(field).removeViewAt(index)
        if (field == TO) updateSingleChipText()
    }

    private fun removeMail(field: FieldType, contact: UiContact) {
        val index = getContacts(field).indexOfFirst { it.email == contact.email }
        removeMail(field, index)
    }

    private fun updateSingleChipText() = with(binding) {
        viewModel.recipients.firstOrNull()?.let { singleChip.root.text = it.name }
    }

    private fun refreshChips() = with(binding) {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        for (contact in viewModel.recipients) createChip(TO, contact)
        for (contact in viewModel.cc) createChip(CC, contact)
        for (contact in viewModel.bcc) createChip(BCC, contact)
    }

    private fun createChip(field: FieldType, contact: UiContact) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            val name = if (contact.name?.isBlank() == true) null else contact.name
            text = name ?: contact.email
            setOnClickListener { removeMail(field, contact) }
            getChipGroup(field).addView(this)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateChipVisibility() = with(binding) {
        singleChipGroup.isVisible = !isAutocompletionOpened
                && !viewModel.areAdvancedFieldsOpened
                && viewModel.recipients.isNotEmpty()

        toItemsChipGroup.isVisible = viewModel.areAdvancedFieldsOpened

        toTransparentButton.isVisible = !isAutocompletionOpened
                && viewModel.recipients.isNotEmpty()
                && !viewModel.areAdvancedFieldsOpened

        doNotAnimate(constraintLayout) {
            plusOthers.isVisible = !isAutocompletionOpened
                    && viewModel.recipients.count() > 1
                    && !viewModel.areAdvancedFieldsOpened
        }
        plusOthersChip.root.text = "+${viewModel.recipients.count() - 1}"

        advancedFields.isVisible = viewModel.areAdvancedFieldsOpened
    }

    private fun doNotAnimate(parent: View, body: () -> Unit) {
        (parent as ViewGroup).layoutTransition.apply {
            disableTransitionType(LayoutTransition.DISAPPEARING)
            body()
            enableTransitionType(LayoutTransition.DISAPPEARING)
        }
    }

    private fun openAutocompletionView(fieldType: FieldType, text: CharSequence) = with(binding) {
        viewModel.areAdvancedFieldsOpened = true
        openAdvancedFields()
        isAutocompletionOpened = true

        isAutocompletionOpened = true
        toggleAutocompletion(fieldType)

        getInputView(fieldType).requestFocus()
    }

    private fun closeAutocompletionView(fieldType: FieldType) = with(binding) {
        isAutocompletionOpened = false
        toggleAutocompletion()
    }

    private fun toggleAutocompletion(fieldType: FieldType? = null) = with(binding) {
        fromGroup.isGone = isAutocompletionOpened
        subjectGroup.isGone = isAutocompletionOpened
        bodyLayout.isGone = isAutocompletionOpened
        chevron.isGone = isAutocompletionOpened

        toGroup.isVisible = !isAutocompletionOpened || fieldType == TO
        ccGroup.isVisible = !isAutocompletionOpened || fieldType == CC
        bccGroup.isVisible = !isAutocompletionOpened || fieldType == BCC

        autoCompleteRecyclerView.isVisible = isAutocompletionOpened
    }

    private fun openAdvancedFields() = with(binding) {
        advancedFields.isVisible = viewModel.areAdvancedFieldsOpened
        chevron.toggleChevron(!viewModel.areAdvancedFieldsOpened)

        refreshChips()
        updateSingleChipText()
        updateChipVisibility()
    }
    //endregion

    fun getFromMailbox(): Mailbox = mailboxes[selectedMailboxIndex]

    fun getSubject(): String = binding.subjectTextField.text.toString()

    fun getBody(): String = binding.bodyText.text.toString()

    enum class FieldType(
        @StringRes val displayedName: Int,
    ) {
        TO(R.string.toTitle),
        CC(R.string.ccTitle),
        BCC(R.string.bccTitle);
    }
}
