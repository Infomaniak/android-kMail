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

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Draft
import com.infomaniak.mail.data.models.Draft.DraftAction
import com.infomaniak.mail.data.models.MessagePriority
import com.infomaniak.mail.data.models.MessagePriority.getPriority
import com.infomaniak.mail.data.models.Recipient
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.ThemedActivity
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction.*
import io.realm.kotlin.ext.realmListOf

class NewMessageActivity : ThemedActivity() {

    private val viewModel: NewMessageViewModel by viewModels()

    private val binding: ActivityNewMessageBinding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }

    private val newMessageFragment: NewMessageFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
            it.childFragmentManager.primaryNavigationFragment as NewMessageFragment
        }!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.apply {
            setContentView(root)

            toolbar.setNavigationOnClickListener {
                sendMail(DraftAction.SAVE)
                onBackPressed()
            }
            toolbar.setOnMenuItemClickListener {
                if (sendMail(DraftAction.SEND)) finish()
                true
            }

            linkEditor(editorAttachment, ATTACHMENT)
            linkEditor(editorCamera, CAMERA)
            linkEditor(editorLink, LINK)
            linkEditor(editorClock, CLOCK)

            linkEditor(editorBold, BOLD)
            linkEditor(editorItalic, ITALIC)
            linkEditor(editorUnderlined, UNDERLINE)
            linkEditor(editorStrikeThrough, STRIKE_THROUGH)
            linkEditor(editorList, UNORDERED_LIST)

            handleEditorToggle()
        }
    }

    private fun ActivityNewMessageBinding.handleEditorToggle() {
        editorTextOptions.setOnClickListener {
            viewModel.isEditorExpanded = !viewModel.isEditorExpanded
            updateEditorVisibility(viewModel.isEditorExpanded)
        }
    }

    private fun ActivityNewMessageBinding.updateEditorVisibility(isEditorExpanded: Boolean) {
        val color = if (isEditorExpanded) R.color.pinkMail else R.color.iconColor
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            setIconTintResource(color)
            contentDescription = getString(resId)
        }

        editorActions.isGone = isEditorExpanded
        textEditing.isVisible = isEditorExpanded
    }

    private fun linkEditor(view: MaterialButton, action: EditorAction) {
        view.setOnClickListener { viewModel.editorAction.value = action }
    }

    private fun createDraft() = with(newMessageFragment) {
        Draft().apply {
            initLocalValues("")
            // TODO: should userInformation (here 'from') be stored in mainViewModel ? see ApiRepository.getUser()
            from = realmListOf(Recipient().apply { email = getFromMailbox().email })
            subject = getSubject()
            body = getBody()
            priority = MessagePriority.Priority.NORMAL.getPriority()
        }
    }

    private fun sendMail(action: DraftAction): Boolean {
        if (viewModel.recipients.isEmpty()) return false
        val mailbox = MainViewModel.currentMailbox.value ?: return false
        viewModel.sendMail(createDraft(), action, mailbox)

        return true
    }

    fun toggleEditor(isVisible: Boolean) {
        binding.editor.isVisible = isVisible
        if (!isVisible) {
            viewModel.isEditorExpanded = false
            binding.updateEditorVisibility(false)
        }
    }

    enum class EditorAction {
        ATTACHMENT,
        CAMERA,
        LINK,
        CLOCK,
        BOLD,
        ITALIC,
        UNDERLINE,
        STRIKE_THROUGH,
        UNORDERED_LIST,
    }
}
