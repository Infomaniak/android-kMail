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
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toggleChevron

class NewMessageFragment : Fragment() {

    private val binding: FragmentNewMessageBinding by lazy { FragmentNewMessageBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by activityViewModels()
    private var mailboxes = MailboxInfoController.getMailboxesSync(AccountUtils.currentUserId)
    private var mails = mailboxes.map { it.email }
    private var selectedMailboxIndex = mailboxes.indexOfFirst { it.objectId == MailData.currentMailboxFlow.value?.objectId }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = with(binding) {
        setupFromField()
        focusCurrentField()
        displayChips()

        toTransparentButton.setOnClickListener { openAdvancedFields() }
        chevron.setOnClickListener { openAdvancedFields() }

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
                EditorAction.ATTACHMENT -> Log.e("gibran", "onCreateView: ATTACHMENT")
                EditorAction.CAMERA -> Log.e("gibran", "onCreateView: CAMERA")
                EditorAction.LINK -> Log.e("gibran", "onCreateView: LINK")
                EditorAction.CLOCK -> Log.e("gibran", "onCreateView: CLOCK")
                EditorAction.BOLD -> Log.e("gibran", "onCreateView: BOLD")
                EditorAction.ITALIC -> Log.e("gibran", "onCreateView: ITALIC")
                EditorAction.UNDERLINE -> Log.e("gibran", "onCreateView: UNDERLINE")
                EditorAction.STRIKE_THROUGH -> Log.e("gibran", "onCreateView: STRIKE_THROUGH")
                EditorAction.UNORDERED_LIST -> Log.e("gibran", "onCreateView: UNORDERED_LIST")
            }
        }

        return root
    }

    private fun focusCurrentField() {
        val field = viewModel.selectedField
        if (field != null) getInputView(field).requestFocus()
    }

    private fun FragmentNewMessageBinding.setupFromField() {
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
        getInputView(field).let {
            it.disableCopyPaste()

            it.doOnTextChanged { text, _, _, _ ->
                if (text?.isNotEmpty() == true) {
                    it.setText("")
                    openFieldFragment(field, text)
                }
            }
        }
    }

    private fun MaterialAutoCompleteTextView.disableCopyPaste() {
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(actionMode: ActionMode?, item: MenuItem?): Boolean {
                return false
            }

            override fun onDestroyActionMode(actionMode: ActionMode?) {}
        }

        isLongClickable = false
        setTextIsSelectable(false)
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

    private fun getInputView(field: FieldType): MaterialAutoCompleteTextView =
        when (field) {
            TO -> binding.toAutocompleteInput
            CC -> binding.ccAutocompleteInput
            BCC -> binding.bccAutocompleteInput
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

    @SuppressLint("SetTextI18n")
    private fun FragmentNewMessageBinding.updateChipVisibility() {
        singleChipGroup.isVisible = !viewModel.areAdvancedFieldsOpened && viewModel.recipients.isNotEmpty()
        toItemsChipGroup.isVisible = viewModel.areAdvancedFieldsOpened
        toTransparentButton.isVisible = viewModel.recipients.isNotEmpty() && !viewModel.areAdvancedFieldsOpened
        doNotAnimate(constraintLayout) {
            plusOthers.isVisible = viewModel.recipients.count() > 1 && !viewModel.areAdvancedFieldsOpened
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

    private fun openFieldFragment(fieldType: FieldType, text: CharSequence) {
        viewModel.areAdvancedFieldsOpened = true
        viewModel.selectedField = fieldType

        safeNavigate(
            NewMessageFragmentDirections.actionNewMessageFragmentToFieldFragment(
                field = fieldType,
                text = text.toString()
            )
        )
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
    ) {
        TO(R.string.toTitle),
        CC(R.string.ccTitle),
        BCC(R.string.bccTitle);
    }
}
