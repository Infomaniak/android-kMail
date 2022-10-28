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

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.data.models.draft.Priority
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.ThemedActivity
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivity.EditorAction.*
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.getAttributeColor
import com.infomaniak.mail.utils.observeNotNull
import io.realm.kotlin.ext.realmListOf
import com.google.android.material.R as RMaterial

class NewMessageActivity : ThemedActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    private val newMessageFragment by lazy {
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
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    private fun ActivityNewMessageBinding.updateEditorVisibility(isEditorExpanded: Boolean) {
        val color = if (isEditorExpanded) context.getAttributeColor(RMaterial.attr.colorPrimary) else getColor(R.color.iconColor)
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            iconTint = ColorStateList.valueOf(color)
            contentDescription = getString(resId)
        }

        editorActions.isGone = isEditorExpanded
        textEditing.isVisible = isEditorExpanded
    }

    private fun linkEditor(view: MaterialButton, action: EditorAction) {
        view.setOnClickListener { newMessageViewModel.editorAction.value = action to null }
    }

    private fun linkEditor(view: ToggleableTextFormatterItemView, action: EditorAction) {
        view.setOnClickListener { newMessageViewModel.editorAction.value = action to view.isToggled }
    }

    private fun createDraft() = with(newMessageFragment) {
        Draft().apply {
            initLocalValues("")
            // TODO: should userInformation (here 'from') be stored in mainViewModel ? see ApiRepository.getUser()
            from = realmListOf(Recipient().initLocalValues(getFromMailbox().email))
            subject = getSubject()
            body = getBody()
            priority = Priority.NORMAL.toString()
        }
    }

    private fun sendMail(action: DraftAction): Boolean {
        if (newMessageViewModel.mailTo.isEmpty()) return false

        val mailboxObjectId = MainViewModel.currentMailboxObjectId.value ?: return false

        mainViewModel.getMailbox(mailboxObjectId).observeNotNull(this) { mailbox ->
            newMessageViewModel.sendMail(createDraft(), action, mailbox)
        }

        return true
    }

    fun toggleEditor(isVisible: Boolean) {
        binding.editor.isVisible = isVisible
        if (!isVisible) {
            newMessageViewModel.isEditorExpanded = false
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
