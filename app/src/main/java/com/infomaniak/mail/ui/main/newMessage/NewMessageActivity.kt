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
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.navArgs
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.ThemedActivity
import com.infomaniak.mail.utils.getAttributeColor
import com.infomaniak.mail.utils.observeNotNull
import com.google.android.material.R as RMaterial

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
        newMessageViewModel.fetchAndConfigureDraft(navigationArgs)
        handleOnBackPressed()
        setupToolbar()
        setupEditorActions()
        setupEditorFormatActionsToggle()
        listenToCloseActivity()
    }

    private fun handleOnBackPressed() = with(newMessageViewModel) {
        onBackPressedDispatcher.addCallback(this@NewMessageActivity) {
            if (isAutocompletionOpened) {
                newMessageFragment.closeAutocompletion()
            } else {
                saveMail(DraftAction.SAVE)
            }
        }
    }

    private fun setupToolbar() = with(newMessageViewModel) {

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.toolbar.setOnMenuItemClickListener {
            if (mailTo.isNotEmpty()) saveMail(DraftAction.SEND)
            true
        }
    }

    private fun listenToCloseActivity() {
        newMessageViewModel.shouldCloseActivity.observeNotNull(this) { if (it) finish() }
    }

    private fun setupEditorActions() = with(binding) {

        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener { newMessageViewModel.editorAction.value = action to null }
        }

        fun linkEditor(view: ToggleableTextFormatterItemView, action: EditorAction) {
            view.setOnClickListener { newMessageViewModel.editorAction.value = action to view.isToggled }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorLink, EditorAction.LINK)
        linkEditor(editorClock, EditorAction.CLOCK)

        linkEditor(editorBold, EditorAction.BOLD)
        linkEditor(editorItalic, EditorAction.ITALIC)
        linkEditor(editorUnderlined, EditorAction.UNDERLINE)
        linkEditor(editorStrikeThrough, EditorAction.STRIKE_THROUGH)
        linkEditor(editorList, EditorAction.UNORDERED_LIST)
    }

    private fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    fun toggleEditor(isVisible: Boolean) {
        binding.editor.isVisible = isVisible
        if (!isVisible) {
            newMessageViewModel.isEditorExpanded = false
            updateEditorVisibility(false)
        }
    }

    private fun updateEditorVisibility(isEditorExpanded: Boolean) = with(binding) {
        val color = if (isEditorExpanded) getAttributeColor(RMaterial.attr.colorPrimary) else getColor(R.color.iconColor)
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            iconTint = ColorStateList.valueOf(color)
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
