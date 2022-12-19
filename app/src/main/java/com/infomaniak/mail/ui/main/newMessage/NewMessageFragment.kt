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
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.Spanned
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.net.MailTo
import androidx.core.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.infomaniak.lib.core.utils.FilePicker
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.parcelableArrayListExtra
import com.infomaniak.lib.core.utils.parcelableExtra
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.isEmail
import com.infomaniak.mail.utils.toggleChevron
import com.infomaniak.mail.workers.DraftsActionsWorker
import com.google.android.material.R as RMaterial
import com.infomaniak.lib.core.R as RCore

class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val newMessageActivityArgs by lazy {
        // When deeplink it happens that the navigation extras are not yet initialized, so we don't use the `navArgs` here
        requireActivity().intent?.extras?.let { NewMessageActivityArgs.fromBundle(it) } ?: NewMessageActivityArgs()
    }
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val addressListPopupWindow by lazy { ListPopupWindow(binding.root.context) }
    private lateinit var filePicker: FilePicker

    private lateinit var contactAdapter: ContactAdapter
    private val attachmentAdapter = AttachmentAdapter(shouldDisplayCloseButton = true, onDelete = ::onDeleteAttachment)

    private var mailboxes = emptyList<Mailbox>()
    private var selectedMailboxIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        filePicker = FilePicker(this@NewMessageFragment)

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

        attachmentsRecyclerView.adapter = attachmentAdapter
        bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }

        setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }

        newMessageViewModel.editorAction.observe(requireActivity()) { (editorAction, isToggled) ->

            val selectedText = with(bodyText) { text?.substring(selectionStart, selectionEnd) }
            // TODO: Do stuff here with this `selectedText`?

            when (editorAction) {
                // TODO: Replace logs with actual code
                EditorAction.ATTACHMENT -> {
                    Log.d("SelectedText", "ATTACHMENT")
                    filePicker.open { uris ->
                        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        newMessageViewModel.importAttachments(uris)
                    }
                }
                EditorAction.CAMERA -> Log.d("SelectedText", "CAMERA")
                EditorAction.LINK -> Log.d("SelectedText", "LINK")
                EditorAction.CLOCK -> Log.d("SelectedText", "CLOCK")
                EditorAction.BOLD -> Log.d("SelectedText", "BOLD: $isToggled")
                EditorAction.ITALIC -> Log.d("SelectedText", "ITALIC: $isToggled")
                EditorAction.UNDERLINE -> Log.d("SelectedText", "UNDERLINE: $isToggled")
                EditorAction.STRIKE_THROUGH -> Log.d("SelectedText", "STRIKE_THROUGH: $isToggled")
                EditorAction.UNORDERED_LIST -> Log.d("SelectedText", "UNORDERED_LIST")
            }
        }

        newMessageViewModel.importedAttachments.observe(requireActivity()) { (attachments, importationResult) ->
            attachmentAdapter.addAll(attachments)
            attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0

            if (importationResult == ImportationResult.FILE_SIZE_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
        }

        subjectTextField.filters = arrayOf<InputFilter>(object : InputFilter {
            override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                return null
            }
        })

        initDraftAndUi()
        observeSubject()
        observeBody()
        observeMailboxes()
    }

    override fun onStop() {
        DraftsActionsWorker.scheduleWork(requireContext())
        super.onStop()
    }

    fun closeAutocompletion() {
        fun FieldType.clearField() = getInputView(this).setText("")
        TO.clearField()
        CC.clearField()
        BCC.clearField()
    }

    private fun initDraftAndUi() {
        newMessageViewModel.initDraftAndUi(newMessageActivityArgs).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                observeContacts()
                populateUiWithExistingDraftData()
                handleActionSend()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun handleActionSend() {
        when (requireActivity().intent?.action) {
            Intent.ACTION_SEND -> handleSingleSendIntent()
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSendIntent()
        }
    }

    /**
     * Handle `Mailto` from [Intent.ACTION_VIEW] or [Intent.ACTION_SENDTO]
     * Get [Intent.ACTION_VIEW] data with [MailTo] and [Intent.ACTION_SENDTO] with [Intent]
     */
    private fun handleMailTo() = with(binding) {
        val intent = requireActivity().intent
        intent?.data?.let { uri ->
            if (!MailTo.isMailTo(uri)) return@with

            val mailTo = MailTo.parse(uri)
            val to = mailTo.to?.split(",")?.map { it.trim() } ?: emptyList()
            val cc = mailTo.cc?.split(",") ?: intent.getStringArrayExtra(Intent.EXTRA_CC)?.toList() ?: emptyList()
            val bcc = mailTo.bcc?.split(",") ?: intent.getStringArrayExtra(Intent.EXTRA_BCC)?.toList() ?: emptyList()

            to.forEach {
                toAutocompleteInput.setText(it)
                contactAdapter.addFirstAvailableItem()
            }

            cc.forEach {
                ccAutocompleteInput.setText(it)
                contactAdapter.addFirstAvailableItem()
            }

            bcc.forEach {
                bccAutocompleteInput.setText(it)
                contactAdapter.addFirstAvailableItem()
            }

            subjectTextField.setText(mailTo.subject ?: intent.getStringExtra(Intent.EXTRA_SUBJECT))
            bodyText.setText(mailTo.body ?: intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun handleSingleSendIntent() = with(requireActivity().intent) {
        if (hasExtra(Intent.EXTRA_TEXT)) {
            binding.subjectTextField.setText(getStringExtra(Intent.EXTRA_SUBJECT) ?: "")
            binding.bodyText.setText(getStringExtra(Intent.EXTRA_TEXT) ?: "")
        } else {
            (parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                newMessageViewModel.importAttachments(listOf(uri))
            }
        }
    }

    private fun handleMultipleSendIntent() = with(requireActivity().intent) {
        parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>()?.let { uris ->
            newMessageViewModel.importAttachments(uris)
        }
    }

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(newMessageViewModel) {
        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            binding.attachmentsRecyclerView.isGone = true
        }
        mailAttachments[position].getUploadLocalFile(requireContext(), currentDraftLocalUuid).delete()
        mailAttachments.removeAt(position)
    }

    private fun populateUiWithExistingDraftData() = with(newMessageViewModel) {
        attachmentAdapter.addAll(mailAttachments)
        binding.attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0
        binding.subjectTextField.setText(mailSubject)
        binding.bodyText.setText(mailBody)
        mailSignature?.let {
            binding.signatureWebView.loadDataWithBaseURL("", it, ClipDescription.MIMETYPE_TEXT_HTML, "utf-8", "")
            binding.removeSignature.setOnClickListener {
                mailSignature = null
                binding.separatedSignature.isGone = true
            }
            binding.separatedSignature.isVisible = true
        }
    }

    private fun observeSubject() {
        binding.subjectTextField.doAfterTextChanged { editable ->
            editable?.toString()?.let(newMessageViewModel::updateMailSubject)
        }
    }

    private fun observeBody() {
        binding.bodyText.doAfterTextChanged { editable ->
            editable?.toString()?.let(newMessageViewModel::updateMailBody)
        }
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
        newMessageViewModel.getMergedContacts().observe(viewLifecycleOwner, ::setupContactsAdapter)
    }

    private fun observeMailboxes() {
        newMessageViewModel.observeMailboxes().observe(viewLifecycleOwner) {
            setupFromField(it.first, it.second)
        }
    }

    // TODO: Since we don't want to allow changing email & signature, maybe this code could be simplified?
    private fun setupFromField(mailboxes: List<Mailbox>, currentMailboxIndex: Int) = with(binding) {

        this@NewMessageFragment.mailboxes = mailboxes
        selectedMailboxIndex = currentMailboxIndex
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

        // TODO: This is disabled for now, because we probably don't want to allow changing email & signature.
        // if (mails.count() > 1) {
        //     fromMailAddress.apply {
        //         setOnClickListener { _ -> addressListPopupWindow.show() }
        //         isClickable = true
        //         isFocusable = true
        //     }
        // }
    }

    private fun setupContactsAdapter(allContacts: List<MergedContact>) = with(newMessageViewModel) {
        val toUsedEmails = mailTo.map { it.email }.toMutableList()
        val ccUsedEmails = mailCc.map { it.email }.toMutableList()
        val bccUsedEmails = mailBcc.map { it.email }.toMutableList()

        displayChips()

        contactAdapter = ContactAdapter(
            allContacts = allContacts,
            toUsedEmails = toUsedEmails,
            ccUsedEmails = ccUsedEmails,
            bccUsedEmails = bccUsedEmails,
            onItemClick = { contact, field ->
                getInputView(field).setText("")
                val recipient = Recipient().initLocalValues(email = contact.email, name = contact.name)
                addRecipientToField(field, recipient)
                createChip(field, recipient)
            },
            addUnrecognizedContact = { field ->
                val isEmail = addUnrecognizedMail(field)
                if (isEmail) getInputView(field).setText("")
            },
        )

        binding.autoCompleteRecyclerView.adapter = contactAdapter
        handleMailTo()
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
            val usedEmails = contactAdapter.getUsedEmails(field)
            if (usedEmails.none { it == input }) {
                usedEmails.add(input)
                val recipient = Recipient().initLocalValues(email = input, name = input)
                addRecipientToField(field, recipient)
                createChip(field, recipient)
            }
        }

        return isEmail
    }

    private fun addRecipientToField(field: FieldType, recipient: Recipient) {
        getRecipients(field).add(recipient)
        newMessageViewModel.saveDraftDebouncing()
    }

    private fun removeRecipientFromField(field: FieldType, index: Int) {
        getRecipients(field).removeAt(index)
        newMessageViewModel.saveDraftDebouncing()
    }

    //region Chips behavior
    private fun getRecipients(field: FieldType): MutableList<Recipient> = when (field) {
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

    private fun removeEmail(field: FieldType, recipient: Recipient) {
        val index = getRecipients(field).indexOfFirst { it.email == recipient.email }
        removeEmail(field, index)
    }

    private fun removeEmail(field: FieldType, index: Int) {
        val email = getRecipients(field)[index].email
        contactAdapter.removeEmail(field, email)
        removeRecipientFromField(field, index)
        getChipGroup(field).removeViewAt(index)
        if (field == TO) {
            updateSingleChipText()
            updateToAutocompleteInputLayout()
        }
    }

    private fun updateSingleChipText() {
        newMessageViewModel.mailTo.firstOrNull()?.let { binding.singleChip.root.text = it.getNameOrEmail() }
    }

    private fun refreshChips() = with(binding) {
        toItemsChipGroup.removeAllViews()
        ccItemsChipGroup.removeAllViews()
        bccItemsChipGroup.removeAllViews()
        newMessageViewModel.mailTo.forEach { createChip(TO, it) }
        newMessageViewModel.mailCc.forEach { createChip(CC, it) }
        newMessageViewModel.mailBcc.forEach { createChip(BCC, it) }
    }

    private fun createChip(field: FieldType, recipient: Recipient) {
        ChipContactBinding.inflate(layoutInflater).root.apply {
            text = recipient.getNameOrEmail()
            setOnClickListener { removeEmail(field, recipient) }
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

    private fun closeAutocompletionView() {
        newMessageViewModel.isAutocompletionOpened = false
        toggleAutocompletion()
    }

    private fun toggleAutocompletion(field: FieldType? = null) = with(newMessageViewModel) {
        binding.fromGroup.isGone = isAutocompletionOpened
        binding.subjectGroup.isGone = isAutocompletionOpened
        binding.bodyLayout.isGone = isAutocompletionOpened
        binding.separatedSignature.isGone = isAutocompletionOpened
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
            toAutocompleteInput.setMarginsRelative(top = margin, start = margin, end = margin)
        }

        updateToAutocompleteInputConstraints()
        updateToAutocompleteInputMargins()
    }
    //endregion

    enum class FieldType {
        TO,
        CC,
        BCC,
    }
}
