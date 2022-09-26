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
package com.infomaniak.mail.ui.main.newMessage

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.*
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.isEmail
import com.infomaniak.mail.utils.setMargins
import com.infomaniak.mail.utils.toggleChevron
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

class NewMessageFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private lateinit var binding: FragmentNewMessageBinding

    private lateinit var contactAdapter: ContactAdapter

    private var mailboxes = emptyList<Mailbox>()
    private var selectedMailboxIndex = 0
    private var isAutocompletionOpened = false
    private val addressListPopupWindow by lazy { ListPopupWindow(binding.root.context) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        handleOnBackPressed()
        displayChips()

        // TODO: Do we want this button?
        // toTransparentButton.setOnClickListener {
        //     viewModel.areAdvancedFieldsOpened = !viewModel.areAdvancedFieldsOpened
        //     openAdvancedFields()
        // }
        chevron.setOnClickListener {
            newMessageViewModel.areAdvancedFieldsOpened = !newMessageViewModel.areAdvancedFieldsOpened
            openAdvancedFields()
        }

        enableAutocomplete(TO)
        enableAutocomplete(CC)
        enableAutocomplete(BCC)

        bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }

        setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }

        newMessageViewModel.editorAction.observe(requireActivity()) {

            val selectedText = with(bodyText) { text?.substring(selectionStart, selectionEnd) }
            // TODO: Do stuff here with this `selectedText`?

            when (it) {
                // TODO: Replace logs with actual code
                EditorAction.ATTACHMENT -> Log.d("SelectedText", "ATTACHMENT")
                EditorAction.CAMERA -> Log.d("SelectedText", "CAMERA")
                EditorAction.LINK -> Log.d("SelectedText", "LINK")
                EditorAction.CLOCK -> Log.d("SelectedText", "CLOCK")
                EditorAction.BOLD -> Log.d("SelectedText", "BOLD")
                EditorAction.ITALIC -> Log.d("SelectedText", "ITALIC")
                EditorAction.UNDERLINE -> Log.d("SelectedText", "UNDERLINE")
                EditorAction.STRIKE_THROUGH -> Log.d("SelectedText", "STRIKE_THROUGH")
                EditorAction.UNORDERED_LIST -> Log.d("SelectedText", "UNORDERED_LIST")
                null -> Unit
            }
        }

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        observeContacts()
        observeMailboxes()
    }

    private fun handleOnBackPressed() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (isAutocompletionOpened) {
                fun FieldType.clearField() = getInputView(this).setText("")
                TO.clearField()
                CC.clearField()
                BCC.clearField()
            } else {
                isEnabled = false
                activity?.onBackPressed()
            }
        }
    }

    private fun enableAutocomplete(field: FieldType) {
        getInputView(field).apply {

            doOnTextChanged { text, _, _, _ ->
                if (text?.isNotEmpty() == true) {
                    if ((text.trim().count()) > 0) contactAdapter.filterField(field, text) else contactAdapter.clear()
                    if (!isAutocompletionOpened) openAutocompletionView(field)
                } else if (isAutocompletionOpened) {
                    closeAutocompletionView()
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) contactAdapter.addFirstAvailableItem()
                true // Keep keyboard open
            }
        }
    }

    private fun observeContacts() {
        newMessageViewModel.getContacts().observe(viewLifecycleOwner, ::setupContactsAdapter)
    }

    private fun observeMailboxes() {
        mainViewModel.listenToMailboxes().observe(viewLifecycleOwner, ::setupFromField)
    }

    private fun setupFromField(mailboxes: List<Mailbox>) = with(binding) {

        this@NewMessageFragment.mailboxes = mailboxes
        selectedMailboxIndex = mailboxes.indexOfFirst { it.objectId == MainViewModel.currentMailboxObjectId.value }
        val mails = mailboxes.map { it.email }

        fromMailAddress.text = mailboxes[selectedMailboxIndex].email

        val adapter = ArrayAdapter(context, RMaterial.layout.support_simple_spinner_dropdown_item, mails)
        addressListPopupWindow.apply {
            setAdapter(adapter)
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = fromMailAddress
            width = fromMailAddress.width
            setOnItemClickListener { _, _, position, _ ->
                fromMailAddress.text = mails[position]
                selectedMailboxIndex = position
                dismiss()
            }
        }

        if (mails.count() > 1) {
            fromMailAddress.apply {
                setOnClickListener { _ -> addressListPopupWindow.show() }
                isClickable = true
                isFocusable = true
            }
        }
    }

    private fun setupContactsAdapter(allContacts: List<MergedContact>) = with(binding) {
        val toAlreadyUsedContactMails = newMessageViewModel.recipients.map { it.email }.toMutableList()
        val ccAlreadyUsedContactMails = newMessageViewModel.newMessageCc.map { it.email }.toMutableList()
        val bccAlreadyUsedContactMails = newMessageViewModel.newMessageBcc.map { it.email }.toMutableList()

        contactAdapter = ContactAdapter(
            allContacts = allContacts,
            toAlreadyUsedContactIds = toAlreadyUsedContactMails,
            ccAlreadyUsedContactIds = ccAlreadyUsedContactMails,
            bccAlreadyUsedContactIds = bccAlreadyUsedContactMails,
            onItemClick = { contact, field ->
                getInputView(field).setText("")
                getContacts(field).add(contact)
                createChip(field, contact)
            },
            addUnrecognizedContact = { field ->
                val isEmail = addUnrecognizedMail(field)
                if (isEmail) getInputView(field).setText("")
            },
        )
        autoCompleteRecyclerView.adapter = contactAdapter
    }

    private fun toggleEditor(hasFocus: Boolean) = (activity as NewMessageActivity).toggleEditor(hasFocus)

    private fun setOnKeyboardListener(callback: (isOpened: Boolean) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(requireActivity().window.decorView) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            callback(isKeyboardVisible)
            insets
        }
    }

    private fun addUnrecognizedMail(fieldType: FieldType): Boolean {
        val input = getInputView(fieldType).text.toString().trim()
        val isEmail = input.isEmail()
        if (isEmail) {
            val alreadyUsedEmails = contactAdapter.getAlreadyUsedEmails(fieldType)
            if (alreadyUsedEmails.none { it == input }) {
                alreadyUsedEmails.add(input)
                val contact = MergedContact().initLocalValues(input, input)
                getContacts(fieldType).add(contact)
                createChip(fieldType, contact)
            }
        }

        return isEmail
    }

    //region Chips behavior
    private fun getContacts(field: FieldType): MutableList<MergedContact> = when (field) {
        TO -> newMessageViewModel.recipients
        CC -> newMessageViewModel.newMessageCc
        BCC -> newMessageViewModel.newMessageBcc
    }

    private fun getChipGroup(field: FieldType): ChipGroup = when (field) {
        TO -> binding.toItemsChipGroup
        CC -> binding.ccItemsChipGroup
        BCC -> binding.bccItemsChipGroup
    }

    private fun getInputView(field: FieldType): MaterialAutoCompleteTextView = when (field) {
        TO -> binding.toAutocompleteInput
        CC -> binding.ccAutocompleteInput
        BCC -> binding.bccAutocompleteInput
    }

    private fun displayChips() {
        refreshChips()
        updateSingleChipText()
        updateChipVisibility()

        binding.singleChip.root.setOnClickListener {
            removeEmail(TO, 0)
            updateChipVisibility()
        }
    }

    private fun removeEmail(field: FieldType, contact: MergedContact) {
        val index = getContacts(field).indexOfFirst { it.email == contact.email }
        removeEmail(field, index)
    }

    private fun removeEmail(field: FieldType, index: Int) {
        val email = getContacts(field)[index].email
        contactAdapter.removeEmail(field, email)
        getContacts(field).removeAt(index)
        getChipGroup(field).removeViewAt(index)
        if (field == TO) {
            updateSingleChipText()
            updateToAutocompleteInputLayout()
        }
    }

    private fun updateSingleChipText() {
        newMessageViewModel.recipients.firstOrNull()?.let { binding.singleChip.root.text = it.name ?: it.email }
    }

    private fun refreshChips() = with(binding) {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        newMessageViewModel.recipients.forEach { createChip(TO, it) }
        newMessageViewModel.newMessageCc.forEach { createChip(CC, it) }
        newMessageViewModel.newMessageBcc.forEach { createChip(BCC, it) }
    }

    private fun createChip(field: FieldType, contact: MergedContact) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            text = contact.getNameOrEmail()
            setOnClickListener { removeEmail(field, contact) }
            getChipGroup(field).addView(this)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateChipVisibility() = with(binding) {
        singleChipGroup.isInvisible = !(!isAutocompletionOpened
                && !newMessageViewModel.areAdvancedFieldsOpened
                && newMessageViewModel.recipients.isNotEmpty())

        toItemsChipGroup.isInvisible = !newMessageViewModel.areAdvancedFieldsOpened

        // TODO: Do we want this button?
        // toTransparentButton.isVisible = !isAutocompletionOpened
        //         && viewModel.recipients.isNotEmpty()
        //         && !viewModel.areAdvancedFieldsOpened

        plusOthers.isInvisible = !(!isAutocompletionOpened
                && newMessageViewModel.recipients.count() > 1
                && !newMessageViewModel.areAdvancedFieldsOpened)

        plusOthersChip.root.text = "+${newMessageViewModel.recipients.count() - 1}"

        advancedFields.isVisible = newMessageViewModel.areAdvancedFieldsOpened
    }

    private fun openAutocompletionView(fieldType: FieldType) {
        newMessageViewModel.areAdvancedFieldsOpened = true
        openAdvancedFields()

        isAutocompletionOpened = true
        toggleAutocompletion(fieldType)
    }

    private fun closeAutocompletionView() {
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

        updateToAutocompleteInputLayout()

        advancedFields.isVisible = newMessageViewModel.areAdvancedFieldsOpened
        chevron.toggleChevron(!newMessageViewModel.areAdvancedFieldsOpened)

        refreshChips()
        updateSingleChipText()
        updateChipVisibility()
    }

    private fun updateToAutocompleteInputLayout() = with(binding) {

        fun updateToAutocompleteInputConstraints() {
            ConstraintSet().apply {
                clone(constraintLayout)
                val topView = when {
                    newMessageViewModel.areAdvancedFieldsOpened -> R.id.toItemsChipGroup
                    newMessageViewModel.recipients.isEmpty() -> R.id.divider1
                    else -> R.id.singleChipGroup
                }
                connect(R.id.toAutocompleteInput, ConstraintSet.TOP, topView, ConstraintSet.BOTTOM, 0)
                applyTo(constraintLayout)
            }
        }

        fun updateToAutocompleteInputMargins() {
            val margin = resources.getDimension(RCore.dimen.marginStandardVerySmall).toInt()
            toAutocompleteInput.setMargins(top = margin, left = margin, right = margin)
        }

        updateToAutocompleteInputConstraints()
        updateToAutocompleteInputMargins()
    }
    //endregion

    fun getFromMailbox(): Mailbox = mailboxes[selectedMailboxIndex]

    fun getSubject(): String = binding.subjectTextField.text.toString()

    fun getBody(): String = binding.bodyText.text.toString()

    enum class FieldType(@StringRes val displayedName: Int) {
        TO(R.string.toTitle),
        CC(R.string.ccTitle),
        BCC(R.string.bccTitle),
    }
}
