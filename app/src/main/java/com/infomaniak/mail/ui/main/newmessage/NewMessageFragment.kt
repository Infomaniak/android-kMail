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

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.*
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.cache.userInfos.ContactController
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newmessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.lib.core.R as RCore

class NewMessageFragment : Fragment() {

    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private lateinit var binding: FragmentNewMessageBinding

    private lateinit var contactAdapter: ContactAdapter

    private var mailboxes = emptyList<Mailbox>()
    private var selectedMailboxIndex = 0
    private var isAutocompletionOpened = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        handleOnBackPressed()
        displayChips()

        // TODO: Do we want this button?
        // toTransparentButton.setOnClickListener {
        //     newMessageViewModel.areAdvancedFieldsOpened = !newMessageViewModel.areAdvancedFieldsOpened
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

            val selectedText = with(bodyText) { text?.substring(selectionStart, selectionEnd) ?: "" }
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

            newMessageViewModel.startDraftAutoSave()
        }

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        listenToAllContacts()
        listenToMailboxes()
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
                (activity as NewMessageActivity).closeDraft()
            }
        }
    }

    private fun List<Recipient>?.toUiContact(): MutableList<UiContact> {
        return (this?.map { UiContact(it.email, it.name) } ?: mutableListOf()) as MutableList<UiContact>
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

    private fun listenToAllContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            ContactController.getContactsAsync().collect {
                val contacts = mutableListOf<UiContact>()
                it.list.forEach { contact ->
                    contacts.addAll(contact.emails.map { email -> UiContact(email, contact.name) })
                }
                setupContactsAdapter(contacts)
            }
        }
    }

    private fun setupContactsAdapter(allContacts: List<UiContact>) = with(binding) {
        val toAlreadyUsedContactMails = newMessageViewModel.draftTo.map { it.email }.toMutableList()
        val ccAlreadyUsedContactMails = newMessageViewModel.draftCc.map { it.email }.toMutableList()
        val bccAlreadyUsedContactMails = newMessageViewModel.draftBcc.map { it.email }.toMutableList()

        contactAdapter = ContactAdapter(
            allContacts = allContacts,
            toAlreadyUsedContactIds = toAlreadyUsedContactMails,
            ccAlreadyUsedContactIds = ccAlreadyUsedContactMails,
            bccAlreadyUsedContactIds = bccAlreadyUsedContactMails,
            onItemClick = { contact, field ->
                getInputView(field).setText("")
                getContacts(field).add(contact)
                createChip(field, contact)
                newMessageViewModel.startDraftAutoSave()
            },
            addUnrecognizedContact = { field ->
                val isEmail = addUnrecognizedMail(field)
                if (isEmail) getInputView(field).setText("")
            },
        )

        autoCompleteRecyclerView.adapter = contactAdapter
    }

    private fun listenToMailboxes() {
        lifecycleScope.launch(Dispatchers.IO) {
            MailboxController.getMailboxesAsync(AccountUtils.currentUserId).collect {
                withContext(Dispatchers.Main) { setupDraftUI(it.list) }
            }
        }
    }

    private fun setupDraftUI(mailboxes: List<Mailbox>) = with(binding) {

        this@NewMessageFragment.mailboxes = mailboxes
        selectedMailboxIndex = mailboxes.indexOfFirst { it.objectId == MainViewModel.currentMailboxObjectId.value }
        val mails = mailboxes.map { it.email }

        if (mails.count() > 1) {
            fromMailAddress.apply {
                // TODO: Put this back when we know exactly how this feature is supposed to work.
                // setOnClickListener { view -> chooseFromAddress(view, mails) }
                // isClickable = true
                // isFocusable = true
            }
        }

        with(newMessageViewModel) {
            currentDraftUuid.observeNotNull(this@NewMessageFragment) { draftUuid ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val draft = DraftController.getDraftSync(draftUuid) ?: return@launch

                    withContext(Dispatchers.Main) {
                        draftTo = draft.to.toUiContact()
                        draftCc = draft.cc.toUiContact()
                        draftBcc = draft.bcc.toUiContact()
                        displayChips()
                        updateToAutocompleteInputLayout()

                        (draft.from.firstOrNull()?.email ?: mailboxes[selectedMailboxIndex].email).also { from ->
                            fromMailAddress.text = from
                            draftEmail = from
                        }

                        SpannableStringBuilder(draft.subject).also { subject ->
                            subjectTextField.text = subject
                            draftSubject = subject.toString()
                        }

                        (Html.fromHtml(draft.body, Html.FROM_HTML_MODE_COMPACT) as Editable).also { body ->
                            bodyText.text = body
                            draftBody = body.toString()
                        }

                        hasStartedEditing = false

                        setupAutoSave()

                        // TODO: Check if we can do this differently. We need to listen only once to this datum.
                        currentDraftUuid.removeObservers(this@NewMessageFragment)
                    }
                }
            }
        }
    }

    // TODO: Put this back when we know exactly how this feature is supposed to work.
    // private fun chooseFromAddress(view: View, mails: List<String>) = with(binding) {
    //     val adapter = ArrayAdapter(context, RMaterial.layout.support_simple_spinner_dropdown_item, mails)
    //     ListPopupWindow(context).apply {
    //         setAdapter(adapter)
    //         anchorView = view
    //         width = view.width
    //         setOnItemClickListener { _, _, position, _ ->
    //             val email = mails[position]
    //             fromMailAddress.text = email
    //             selectedMailboxIndex = position
    //             newMessageViewModel.draftEmail = email
    //             if (newMessageViewModel.hasStartedEditing) newMessageViewModel.startDraftAutoSave()
    //             dismiss()
    //         }
    //     }.show()
    // }

    private fun setupAutoSave() = with(binding) {
        subjectTextField.setupAutoSave { newMessageViewModel.draftSubject = getDraftSubject() }
        bodyText.setupAutoSave { newMessageViewModel.draftBody = getDraftBody() }
    }

    private fun TextView.setupAutoSave(afterTextChanged: () -> Unit) {
        addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                afterTextChanged()
                newMessageViewModel.startDraftAutoSave()
            }
        })
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
                val contact = UiContact(input)
                getContacts(fieldType).add(contact)
                createChip(fieldType, contact)
                newMessageViewModel.startDraftAutoSave()
            }
        }

        return isEmail
    }

    //region Chips behavior
    private fun getContacts(field: FieldType): MutableList<UiContact> = when (field) {
        TO -> newMessageViewModel.draftTo
        CC -> newMessageViewModel.draftCc
        BCC -> newMessageViewModel.draftBcc
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
        newMessageViewModel.startDraftAutoSave()
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
        updateChipVisibility()
    }

    private fun updateSingleChipText() {
        newMessageViewModel.draftTo.firstOrNull()?.let { binding.singleChip.root.text = it.name ?: it.email }
    }

    private fun refreshChips() = with(binding) {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        newMessageViewModel.draftTo.forEach { createChip(TO, it) }
        newMessageViewModel.draftCc.forEach { createChip(CC, it) }
        newMessageViewModel.draftBcc.forEach { createChip(BCC, it) }
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
    private fun updateChipVisibility() = with(binding) {
        singleChipGroup.isInvisible = !(!isAutocompletionOpened
                && !newMessageViewModel.areAdvancedFieldsOpened
                && newMessageViewModel.draftTo.isNotEmpty())

        toItemsChipGroup.isInvisible = !newMessageViewModel.areAdvancedFieldsOpened

        // TODO: Do we want this button?
        // toTransparentButton.isVisible = !isAutocompletionOpened
        //         && newMessageViewModel.recipients.isNotEmpty()
        //         && !newMessageViewModel.areAdvancedFieldsOpened

        plusOthers.isInvisible = !(!isAutocompletionOpened
                && newMessageViewModel.draftTo.count() > 1
                && !newMessageViewModel.areAdvancedFieldsOpened)

        plusOthersChip.root.text = "+${newMessageViewModel.draftTo.count() - STICKY_RECIPIENT_COUNT}"

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
                    newMessageViewModel.draftTo.isEmpty() -> R.id.divider1
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

    private fun getDraftSubject(): String = binding.subjectTextField.text.toString()

    private fun getDraftBody(): String = binding.bodyText.text.toString()

    enum class FieldType(@StringRes val displayedName: Int) {
        TO(R.string.toTitle),
        CC(R.string.ccTitle),
        BCC(R.string.bccTitle),
    }

    companion object {
        private const val STICKY_RECIPIENT_COUNT = 1
    }
}
