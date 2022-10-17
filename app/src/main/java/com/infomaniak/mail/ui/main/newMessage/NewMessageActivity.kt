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

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.navArgs
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.ThemedActivity

class NewMessageActivity : ThemedActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val navigationArgs: NewMessageActivityArgs by navArgs()
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    private val newMessageFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
            it.childFragmentManager.primaryNavigationFragment as NewMessageFragment
        }!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        newMessageViewModel.setupDraft(navigationArgs.draftUuid)
        handleOnBackPressed()
        setupToolbar()
        setupEditorActions()
        setupEditorFormatActionsToggle()
    }

    private fun handleOnBackPressed() {
        onBackPressedDispatcher.addCallback(this@NewMessageActivity) {
            if (newMessageViewModel.isAutocompletionOpened) {
                newMessageFragment.closeAutocompletion()
            } else {
                newMessageViewModel.saveMail {
                    newMessageViewModel.deleteDraft()
                }
                finish()
            }
        }
    }

    private fun setupToolbar() = with(binding) {

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        toolbar.setOnMenuItemClickListener {
            newMessageViewModel.sendMail { isSuccess ->
                if (isSuccess) {
                    newMessageViewModel.deleteDraft()
                    finish()
                }
            }
            true
        }
    }

    private fun setupEditorActions() = with(binding) {

        fun setEditorActionClickListener(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener { newMessageViewModel.editorAction.value = action }
        }

        setEditorActionClickListener(editorAttachment, EditorAction.ATTACHMENT)
        setEditorActionClickListener(editorCamera, EditorAction.CAMERA)
        setEditorActionClickListener(editorLink, EditorAction.LINK)
        setEditorActionClickListener(editorClock, EditorAction.CLOCK)

        setEditorActionClickListener(editorBold, EditorAction.BOLD)
        setEditorActionClickListener(editorItalic, EditorAction.ITALIC)
        setEditorActionClickListener(editorUnderlined, EditorAction.UNDERLINE)
        setEditorActionClickListener(editorStrikeThrough, EditorAction.STRIKE_THROUGH)
        setEditorActionClickListener(editorList, EditorAction.UNORDERED_LIST)
    }

    private fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    // This function is called from NewMessageFragment
    fun toggleEditor(isVisible: Boolean) {
        binding.editor.isVisible = isVisible
        if (!isVisible) {
            newMessageViewModel.isEditorExpanded = false
            updateEditorVisibility(false)
        }
    }

    private fun updateEditorVisibility(isEditorExpanded: Boolean) = with(binding) {
        val color = if (isEditorExpanded) R.color.pinkMail else R.color.iconColor
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            setIconTintResource(color)
            contentDescription = getString(resId)
        }

        editorActions.isGone = isEditorExpanded
        textEditing.isVisible = isEditorExpanded
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
