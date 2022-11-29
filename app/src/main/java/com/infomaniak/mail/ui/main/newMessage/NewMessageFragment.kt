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

import android.content.ClipDescription
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.navArgs
import com.infomaniak.lib.core.utils.FilePicker
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.workers.DraftsActionsWorker
import com.google.android.material.R as RMaterial

class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val newMessageActivityArgs by lazy { requireActivity().navArgs<NewMessageActivityArgs>().value }
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

        initDraftAndUi()
        observeSubject()
        observeBody()
        observeMailboxes()

        observeEditorActions()
        observeNewAttachments()
    }

    private fun initDraftAndUi() = with(binding) {
        attachmentsRecyclerView.adapter = attachmentAdapter

        setupAutoCompletionFields()

        subjectTextField.apply {
            // Enables having imeOptions="actionNext" and inputType="textMultiLine" at the same time
            setRawInputType(InputType.TYPE_CLASS_TEXT)

            filters = arrayOf<InputFilter>(object : InputFilter {
                override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                    source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                    return null
                }
            })
        }

        bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }
        setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }

        newMessageViewModel.initDraftAndUi(newMessageActivityArgs).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                observeContacts()
                populateUiWithExistingDraftData()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun setOnKeyboardListener(callback: (isOpened: Boolean) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(requireActivity().window.decorView) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            callback(isKeyboardVisible)
            insets
        }
    }

    private fun observeContacts() {
        newMessageViewModel.getMergedContacts().observe(viewLifecycleOwner, ::setupContactsAdapter)
    }

    private fun setupContactsAdapter(allContacts: List<MergedContact>) = with(newMessageViewModel) {
        binding.toField.apply {
            initContacts(allContacts, mutableListOf())
            onAutoCompletionToggled { hasOpened -> toggleAutoCompletion(TO, hasOpened) }
        }

        binding.ccField.apply {
            initContacts(allContacts, mutableListOf())
            onAutoCompletionToggled { hasOpened -> toggleAutoCompletion(CC, hasOpened) }
        }

        binding.bccField.apply {
            initContacts(allContacts, mutableListOf())
            onAutoCompletionToggled { hasOpened -> toggleAutoCompletion(BCC, hasOpened) }
        }
    }

    private fun toggleAutoCompletion(field: FieldType? = null, isAutocompletionOpened: Boolean) = with(newMessageViewModel) {
        binding.preFields.isGone = isAutocompletionOpened

        binding.to.isVisible = !isAutocompletionOpened || field == TO
        binding.cc.isVisible = !isAutocompletionOpened || field == CC
        binding.bcc.isVisible = !isAutocompletionOpened || field == BCC
        binding.autoCompleteRecyclerView.isVisible = isAutocompletionOpened

        binding.postFields.isGone = isAutocompletionOpened
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

    private fun setupAutoCompletionFields() = with(binding) {
        toField.apply {
            setOnToggleListener(::openAdvancedFields)
            // onFocusNext {
            //     openAdvancedFields(false)
            //     ccField.requestFocus()
            // }
        }

        // ccField.apply {
        //     onFocusNext { bccField.requestFocus() }
        //     onFocusPrevious { toField.requestFocus() }
        // }
        //
        // bccField.apply {
        //     onFocusNext { subjectTextField.requestFocus() }
        //     onFocusPrevious { ccField.requestFocus() }
        // }
    }

    private fun openAdvancedFields(isCollapsed: Boolean) = with(binding) {
        cc.isGone = isCollapsed
        bcc.isGone = isCollapsed
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

    private fun observeEditorActions() = with(binding) {
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
    }

    private fun observeNewAttachments() = with(binding) {
        newMessageViewModel.importedAttachments.observe(requireActivity()) { (attachments, importationResult) ->
            attachmentAdapter.addAll(attachments)
            attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0

            if (importationResult == ImportationResult.FILE_SIZE_TOO_BIG) showSnackbar(R.string.attachmentFileLimitReached)
        }
    }

    override fun onStop() {
        DraftsActionsWorker.scheduleWork(requireContext())
        super.onStop()
    }

    fun closeAutocompletion() = with(binding) {
        toField.clearField()
        ccField.clearField()
        bccField.clearField()
    }

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(newMessageViewModel) {
        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            binding.attachmentsRecyclerView.isGone = true
        }
        mailAttachments[position].getUploadLocalFile(requireContext(), currentDraftLocalUuid).delete()
        mailAttachments.removeAt(position)
    }

    private fun toggleEditor(hasFocus: Boolean) = (activity as NewMessageActivity).toggleEditor(hasFocus)

    // private fun addUnrecognizedMail(field: FieldType): Boolean {
    //     val input = getInputView(field).text.toString().trim()
    //     val isEmail = input.isEmail()
    //     if (isEmail) {
    //         val usedEmails = contactAdapter.getUsedEmails(field)
    //         if (usedEmails.none { it == input }) {
    //             usedEmails.add(input)
    //             val recipient = Recipient().initLocalValues(email = input, name = input)
    //             addRecipientToField(field, recipient)
    //             createChip(field, recipient)
    //         }
    //     }
    //
    //     return isEmail
    // }

    //region Chips behavior

    // @SuppressLint("SetTextI18n")
    // private fun updateChipVisibility() = with(newMessageViewModel) {
    //     binding.singleChipGroup.isInvisible = !(!isAutocompletionOpened
    //             && !areAdvancedFieldsOpened
    //             && mailTo.isNotEmpty())
    //
    //     binding.toItemsChipGroup.isInvisible = !areAdvancedFieldsOpened
    //
    //     // TODO: Do we want this button?
    //     // toTransparentButton.isVisible = !isAutocompletionOpened
    //     //         && viewModel.recipients.isNotEmpty()
    //     //         && !viewModel.areAdvancedFieldsOpened
    //
    //     val mailToCount = mailTo.count()
    //
    //     binding.plusOthers.isInvisible = !(!isAutocompletionOpened
    //             && mailToCount > 1
    //             && !areAdvancedFieldsOpened)
    //
    //     binding.plusOthersChip.root.text = "+${mailToCount - 1}"
    //
    //     binding.cc.isVisible = areAdvancedFieldsOpened
    //     binding.bcc.isVisible = areAdvancedFieldsOpened
    // }

    // private fun openAutocompletionView(field: FieldType) = with(newMessageViewModel) {
    //     areAdvancedFieldsOpened = true
    //     openAdvancedFields()
    //
    //     isAutocompletionOpened = true
    //     toggleAutocompletion(field)
    // }
    //
    // private fun closeAutocompletionView() {
    //     newMessageViewModel.isAutocompletionOpened = false
    //     toggleAutocompletion()
    // }

    // private fun toggleAutocompletion(field: FieldType? = null) = with(newMessageViewModel) {
    //     binding.preFields.isGone = isAutocompletionOpened
    //
    //     binding.to.isVisible = !isAutocompletionOpened || field == TO
    //     binding.cc.isVisible = !isAutocompletionOpened || field == CC
    //     binding.bcc.isVisible = !isAutocompletionOpened || field == BCC
    //     binding.autoCompleteRecyclerView.isVisible = isAutocompletionOpened
    //
    //     binding.postFields.isGone = isAutocompletionOpened
    // }

    // private fun updateToAutocompleteInputLayout() = with(binding) {
    //
    //     fun updateToAutocompleteInputConstraints() {
    //         ConstraintSet().apply {
    //             clone(to)
    //             val topView = when {
    //                 newMessageViewModel.areAdvancedFieldsOpened -> R.id.toItemsChipGroup
    //                 newMessageViewModel.mailTo.isEmpty() -> R.id.divider1
    //                 else -> R.id.singleChipGroup
    //             }
    //             connect(R.id.toAutocompleteInput, ConstraintSet.TOP, topView, ConstraintSet.BOTTOM, 0)
    //             applyTo(to)
    //         }
    //     }
    //
    //     fun updateToAutocompleteInputMargins() {
    //         val margin = resources.getDimension(RCore.dimen.marginStandardVerySmall).toInt()
    //         toAutocompleteInput.setMarginsRelative(top = margin, start = margin, end = margin)
    //     }
    //
    //     updateToAutocompleteInputConstraints()
    //     updateToAutocompleteInputMargins()
    // }
    //endregion

    enum class FieldType {
        TO,
        CC,
        BCC,
    }
}
