/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
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
import androidx.core.net.MailTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.FilePicker
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.parcelableArrayListExtra
import com.infomaniak.lib.core.utils.parcelableExtra
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction
import com.infomaniak.mail.ui.main.newMessage.NewMessageFragment.FieldType.*
import com.infomaniak.mail.ui.main.newMessage.NewMessageViewModel.ImportationResult
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.workers.DraftsActionsWorker
import com.google.android.material.R as RMaterial

class NewMessageFragment : Fragment() {

    private lateinit var binding: FragmentNewMessageBinding
    private val newMessageActivityArgs by lazy {
        // When opening this fragment via deeplink, it can happen that the navigation
        // extras aren't yet initialized, so we don't use the `navArgs` here.
        requireActivity().intent?.extras?.let(NewMessageActivityArgs::fromBundle) ?: NewMessageActivityArgs()
    }
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    private val addressListPopupWindow by lazy { ListPopupWindow(binding.root.context) }
    private lateinit var filePicker: FilePicker

    private val attachmentAdapter = AttachmentAdapter(shouldDisplayCloseButton = true, onDelete = ::onDeleteAttachment)

    private var mailboxes = emptyList<Mailbox>()
    private var selectedMailboxIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewMessageBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        filePicker = FilePicker(this@NewMessageFragment)

        initUi()
        initDraftAndViewModel()

        doAfterSubjectChange()
        doAfterBodyChange()

        observeContacts()
        observeMailboxes()
        observeEditorActions()
        observeNewAttachments()
    }

    private fun initUi() = with(binding) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(signatureWebView.settings, true)
        }
        attachmentsRecyclerView.adapter = attachmentAdapter

        setupAutoCompletionFields()

        subjectTextField.apply {
            // Enables having imeOptions="actionNext" and inputType="textMultiLine" at the same time
            setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

            filters = arrayOf<InputFilter>(object : InputFilter {
                override fun filter(source: CharSequence?, s: Int, e: Int, d: Spanned?, dS: Int, dE: Int): CharSequence? {
                    source?.toString()?.let { if (it.contains("\n")) return it.replace("\n", "") }
                    return null
                }
            })
        }

        bodyText.setOnFocusChangeListener { _, hasFocus -> toggleEditor(hasFocus) }
        setOnKeyboardListener { isOpened -> toggleEditor(bodyText.hasFocus() && isOpened) }
    }

    private fun initDraftAndViewModel() {
        newMessageViewModel.initDraftAndViewModel(newMessageActivityArgs).observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                populateViewModelWithExternalMailData()
                populateUiWithViewModel()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun setupAutoCompletionFields() = with(binding) {
        toField.initRecipientField(
            autoComplete = autoCompleteTo,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(TO, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, TO) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, TO) },
            onToggleCallback = ::openAdvancedFields,
        )

        ccField.initRecipientField(
            autoComplete = autoCompleteCc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(CC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, CC) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, CC) },
        )

        bccField.initRecipientField(
            autoComplete = autoCompleteBcc,
            onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(BCC, hasOpened) },
            onContactAddedCallback = { newMessageViewModel.addRecipientToField(it, BCC) },
            onContactRemovedCallback = { newMessageViewModel.removeRecipientFromField(it, BCC) },
        )
    }

    private fun openAdvancedFields(isCollapsed: Boolean) = with(binding) {
        cc.isGone = isCollapsed
        bcc.isGone = isCollapsed
    }

    private fun setOnKeyboardListener(callback: (isOpened: Boolean) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(requireActivity().window.decorView) { _, insets ->
            insets.also {
                val isKeyboardVisible = it.isVisible(WindowInsetsCompat.Type.ime())
                callback(isKeyboardVisible)
            }
        }
    }

    private fun observeContacts() {
        newMessageViewModel.getMergedContacts().observe(viewLifecycleOwner, ::updateContactsAdapter)
    }

    private fun updateContactsAdapter(allContacts: List<MergedContact>) = with(binding) {
        toField.updateContacts(allContacts)
        ccField.updateContacts(allContacts)
        bccField.updateContacts(allContacts)
    }

    private fun toggleAutoCompletion(field: FieldType? = null, isAutoCompletionOpened: Boolean) = with(binding) {
        preFields.isGone = isAutoCompletionOpened
        to.isVisible = !isAutoCompletionOpened || field == TO
        cc.isVisible = !isAutoCompletionOpened || field == CC
        bcc.isVisible = !isAutoCompletionOpened || field == BCC
        postFields.isGone = isAutoCompletionOpened

        newMessageViewModel.isAutoCompletionOpened = isAutoCompletionOpened
    }

    private fun populateUiWithViewModel() = with(binding) {
        val draft = newMessageViewModel.draft

        attachmentAdapter.addAll(draft.attachments)
        attachmentsRecyclerView.isGone = attachmentAdapter.itemCount == 0
        subjectTextField.setText(draft.subject)
        bodyText.setText(draft.uiBody)

        draft.uiSignature?.let {
            signatureWebView.loadDataWithBaseURL("", it, ClipDescription.MIMETYPE_TEXT_HTML, Utils.UTF_8, "")
            removeSignature.setOnClickListener {
                draft.uiSignature = null
                separatedSignature.isGone = true
            }
            separatedSignature.isVisible = true
        }

        toField.initRecipients(draft.to)
        ccField.initRecipients(draft.cc)
        bccField.initRecipients(draft.bcc)
    }

    private fun populateViewModelWithExternalMailData() {
        when (requireActivity().intent?.action) {
            Intent.ACTION_SEND -> handleSingleSendIntent()
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleSendIntent()
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> handleMailTo()
        }
    }

    /**
     * Handle `MailTo` from [Intent.ACTION_VIEW] or [Intent.ACTION_SENDTO]
     * Get [Intent.ACTION_VIEW] data with [MailTo] and [Intent.ACTION_SENDTO] with [Intent]
     */
    private fun handleMailTo() = with(newMessageViewModel) {
        fun String.splitToRecipientList() = split(",").map {
            val email = it.trim()
            Recipient().initLocalValues(email, email)
        }

        val intent = requireActivity().intent
        intent?.data?.let { uri ->
            if (!MailTo.isMailTo(uri)) return@with

            val mailToIntent = MailTo.parse(uri)
            val to = mailToIntent.to?.splitToRecipientList()
                ?: emptyList()
            val cc = mailToIntent.cc?.splitToRecipientList()
                ?: intent.getStringArrayExtra(Intent.EXTRA_CC)?.map { Recipient().initLocalValues(it, it) }
                ?: emptyList()
            val bcc = mailToIntent.bcc?.splitToRecipientList()
                ?: intent.getStringArrayExtra(Intent.EXTRA_BCC)?.map { Recipient().initLocalValues(it, it) }
                ?: emptyList()

            draft.to.addAll(to)
            draft.cc.addAll(cc)
            draft.bcc.addAll(bcc)

            draft.subject = mailToIntent.subject ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            draft.uiBody = mailToIntent.body ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            saveDraftDebouncing()
        }
    }

    private fun handleSingleSendIntent() = with(requireActivity().intent) {
        if (hasExtra(Intent.EXTRA_TEXT)) {
            getStringExtra(Intent.EXTRA_SUBJECT)?.let { newMessageViewModel.draft.subject = it }
            getStringExtra(Intent.EXTRA_TEXT)?.let { newMessageViewModel.draft.uiBody = it }
        } else {
            (parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                newMessageViewModel.importAttachments(listOf(uri))
            }
        }
    }

    private fun handleMultipleSendIntent() {
        requireActivity().intent.parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>()?.let { uris ->
            newMessageViewModel.importAttachments(uris)
        }
    }

    private fun doAfterSubjectChange() {
        binding.subjectTextField.doAfterTextChanged { editable ->
            editable?.toString()?.let { newMessageViewModel.updateMailSubject(it.ifBlank { null }) }
        }
    }

    private fun doAfterBodyChange() {
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

    private fun observeEditorActions() {
        newMessageViewModel.editorAction.observe(requireActivity()) { (editorAction, /*isToggled*/ _) ->
            when (editorAction) {
                EditorAction.ATTACHMENT -> {
                    filePicker.open { uris ->
                        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        newMessageViewModel.importAttachments(uris)
                    }
                }
                EditorAction.CAMERA -> notYetImplemented()
                EditorAction.CLOCK -> notYetImplemented()
                else -> Log.wtf("SelectedText", "Impossible action got triggered: $editorAction")
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

    fun closeAutoCompletion() = with(binding) {
        toField.clearField()
        ccField.clearField()
        bccField.clearField()
    }

    private fun onDeleteAttachment(position: Int, itemCountLeft: Int) = with(binding) {
        val draft = newMessageViewModel.draft

        if (itemCountLeft == 0) {
            TransitionManager.beginDelayedTransition(binding.root)
            attachmentsRecyclerView.isGone = true
        }

        draft.attachments[position].getUploadLocalFile(requireContext(), draft.localUuid).delete()
        draft.attachments.removeAt(position)
    }

    private fun toggleEditor(hasFocus: Boolean) {
        (activity as NewMessageActivity).toggleEditor(hasFocus)
    }

    enum class FieldType {
        TO,
        CC,
        BCC,
    }
}
