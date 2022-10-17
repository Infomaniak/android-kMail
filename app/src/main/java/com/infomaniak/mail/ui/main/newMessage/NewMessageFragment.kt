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
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.*
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private lateinit var contactAdapter: ContactAdapter

    private var mailboxes = emptyList<Mailbox>()
    private var selectedMailboxIndex = 0
    private val addressListPopupWindow by lazy { ListPopupWindow(binding.root.context) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // handleOnBackPressed()

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

        observeDraftUuid()
        listenToSubject()
        listenToBody()
        observeMailboxes()
    }

    private fun observeDraftUuid() {
        newMessageViewModel.currentDraftUuid.observeNotNull(viewLifecycleOwner) {
            observeContacts()
            populateUiWithExistingDraftData()
        }
    }

    private fun populateUiWithExistingDraftData() = with(newMessageViewModel) {
        binding.subjectTextField.setText(mailSubject)
        binding.bodyText.setText(mailBody)
    }

    private fun listenToSubject() {
        binding.subjectTextField.doAfterTextChanged { editable ->
            editable?.let { newMessageViewModel.updateMailSubject(it.toString()) }
        }
    }

    private fun listenToBody() {
        binding.bodyText.doAfterTextChanged { editable ->
            editable?.let { newMessageViewModel.updateMailBody(it.toString()) }
        }
    }

    fun closeAutocompletion() {
        fun FieldType.clearField() = getInputView(this).setText("")
        TO.clearField()
        CC.clearField()
        BCC.clearField()
    }

    private fun enableAutocomplete(field: FieldType) = with(newMessageViewModel) {
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

        val selectedEmail = mailboxes[selectedMailboxIndex].email
        fromMailAddress.text = selectedEmail
        newMessageViewModel.mailFrom = selectedEmail

        val adapter = ArrayAdapter(context, RMaterial.layout.support_simple_spinner_dropdown_item, mails)
        addressListPopupWindow.apply {
            setAdapter(adapter)
            isModal = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            anchorView = fromMailAddress
            width = fromMailAddress.width
            setOnItemClickListener { _, _, position, _ ->
                val email = mails[position]
                newMessageViewModel.updateMailFrom(email)
                fromMailAddress.text = email
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

    private fun setupContactsAdapter(allContacts: List<UiContact>) = with(binding) {
        val toAlreadyUsedContactMails = newMessageViewModel.mailTo.map { it.email }.toMutableList()
        val ccAlreadyUsedContactMails = newMessageViewModel.mailCc.map { it.email }.toMutableList()
        val bccAlreadyUsedContactMails = newMessageViewModel.mailBcc.map { it.email }.toMutableList()

        displayChips()

        contactAdapter = ContactAdapter(
            allContacts = allContacts,
            toAlreadyUsedContactIds = toAlreadyUsedContactMails,
            ccAlreadyUsedContactIds = ccAlreadyUsedContactMails,
            bccAlreadyUsedContactIds = bccAlreadyUsedContactMails,
            onItemClick = { contact, field ->
                getInputView(field).setText("")
                addContactToField(field, contact)
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

    private fun addUnrecognizedMail(field: FieldType): Boolean {
        val input = getInputView(field).text.toString().trim()
        val isEmail = input.isEmail()
        if (isEmail) {
            val alreadyUsedEmails = contactAdapter.getAlreadyUsedEmails(field)
            if (alreadyUsedEmails.none { it == input }) {
                alreadyUsedEmails.add(input)
                val contact = UiContact(input)
                addContactToField(field, contact)
                createChip(field, contact)
            }
        }

        return isEmail
    }

    private fun addContactToField(field: FieldType, contact: UiContact) = with(newMessageViewModel) {
        when (field) {
            TO -> updateMailTo(mailTo.toMutableList().apply { add(contact) })
            CC -> updateMailCc(mailCc.toMutableList().apply { add(contact) })
            BCC -> updateMailBcc(mailBcc.toMutableList().apply { add(contact) })
        }
    }

    private fun removeContactFromField(field: FieldType, index: Int) = with(newMessageViewModel) {
        when (field) {
            TO -> updateMailTo(mailTo.toMutableList().apply { removeAt(index) })
            CC -> updateMailCc(mailCc.toMutableList().apply { removeAt(index) })
            BCC -> updateMailBcc(mailBcc.toMutableList().apply { removeAt(index) })
        }
    }

    //region Chips behavior
    private fun getContacts(field: FieldType): List<UiContact> = when (field) {
        TO -> newMessageViewModel.mailTo
        CC -> newMessageViewModel.mailCc
        BCC -> newMessageViewModel.mailBcc
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

    private fun removeEmail(field: FieldType, contact: UiContact) {
        val index = getContacts(field).indexOfFirst { it.email == contact.email }
        removeEmail(field, index)
    }

    private fun removeEmail(field: FieldType, index: Int) {
        val email = getContacts(field)[index].email
        contactAdapter.removeEmail(field, email)
        removeContactFromField(field, index)
        getChipGroup(field).removeViewAt(index)
        if (field == TO) {
            updateSingleChipText()
            updateToAutocompleteInputLayout()
        }
    }

    private fun updateSingleChipText() {
        newMessageViewModel.mailTo.firstOrNull()?.let { binding.singleChip.root.text = it.name ?: it.email }
    }

    private fun refreshChips() = with(binding) {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        newMessageViewModel.mailTo.forEach { createChip(TO, it) }
        newMessageViewModel.mailCc.forEach { createChip(CC, it) }
        newMessageViewModel.mailBcc.forEach { createChip(BCC, it) }
    }

    private fun createChip(field: FieldType, contact: UiContact) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            val name = if (contact.name?.isBlank() == true) null else contact.name
            text = name ?: contact.email
            setOnClickListener { removeEmail(field, contact) }
            getChipGroup(field).addView(this)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateChipVisibility() = with(newMessageViewModel) {
        binding.singleChipGroup.isInvisible = !(!isAutocompletionOpened
                && !areAdvancedFieldsOpened
                && mailTo.isNotEmpty())

        binding.toItemsChipGroup.isInvisible = !areAdvancedFieldsOpened

        // TODO: Do we want this button?
        // toTransparentButton.isVisible = !isAutocompletionOpened
        //         && viewModel.recipients.isNotEmpty()
        //         && !viewModel.areAdvancedFieldsOpened

        val mailToCount = mailTo.count()

        binding.plusOthers.isInvisible = !(!isAutocompletionOpened
                && mailToCount > 1
                && !areAdvancedFieldsOpened)

        binding.plusOthersChip.root.text = "+${mailToCount - 1}"

        binding.advancedFields.isVisible = areAdvancedFieldsOpened
    }

    private fun openAutocompletionView(field: FieldType) = with(newMessageViewModel) {
        areAdvancedFieldsOpened = true
        openAdvancedFields()

        isAutocompletionOpened = true
        toggleAutocompletion(field)
    }

    private fun closeAutocompletionView() = with(newMessageViewModel) {
        isAutocompletionOpened = false
        toggleAutocompletion()
    }

    private fun toggleAutocompletion(field: FieldType? = null) = with(newMessageViewModel) {
        binding.fromGroup.isGone = isAutocompletionOpened
        binding.subjectGroup.isGone = isAutocompletionOpened
        binding.bodyLayout.isGone = isAutocompletionOpened
        binding.chevron.isGone = isAutocompletionOpened

        binding.toGroup.isVisible = !isAutocompletionOpened || field == TO
        binding.ccGroup.isVisible = !isAutocompletionOpened || field == CC
        binding.bccGroup.isVisible = !isAutocompletionOpened || field == BCC

        binding.autoCompleteRecyclerView.isVisible = isAutocompletionOpened
    }

    private fun openAdvancedFields() = with(newMessageViewModel) {

        updateToAutocompleteInputLayout()

        binding.advancedFields.isVisible = areAdvancedFieldsOpened
        binding.chevron.toggleChevron(!areAdvancedFieldsOpened)

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
                    newMessageViewModel.mailTo.isEmpty() -> R.id.divider1
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
