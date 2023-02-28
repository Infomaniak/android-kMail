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

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    private val newMessageFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
            it.childFragmentManager.primaryNavigationFragment as NewMessageFragment
        }!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        handleOnBackPressed()

        setupSnackBar()
        setupToolbar()
        setupSystemBars()
        setupEditorActions()
        setupEditorFormatActionsToggle()

        observeCloseActivity()
    }

    private fun handleOnBackPressed() = with(newMessageViewModel) {
        onBackPressedDispatcher.addCallback(this@NewMessageActivity) {
            if (isAutoCompletionOpened) newMessageFragment.closeAutoCompletion() else saveDraftAndShowToast(DraftAction.SAVE)
        }
    }

    private fun setupSnackBar() {
        newMessageViewModel.snackBarManager.setup(this)
    }

    private fun setupToolbar() = with(binding) {

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        newMessageViewModel.isSendingAllowed.observe(this@NewMessageActivity) {
            sendButton.isEnabled = it
        }

        sendButton.setOnClickListener { saveDraftAndShowToast(DraftAction.SEND) }
    }

    private fun setupSystemBars() {
        val backgroundColor = getColor(R.color.newMessageBackgroundColor)
        window.apply {
            statusBarColor = backgroundColor
            navigationBarColor = backgroundColor
        }
    }

    private fun saveDraftAndShowToast(action: DraftAction) {
        newMessageViewModel.saveToLocalAndFinish(action) {
            displayDraftActionToast(action)
        }
    }

    private fun displayDraftActionToast(action: DraftAction) {
        val text = if (action == DraftAction.SAVE) R.string.snackbarDraftSaved else R.string.snackbarEmailSending
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun observeCloseActivity() {
        newMessageViewModel.shouldCloseActivity.observeNotNull(this) { if (it) finish() }
    }

    private fun setupEditorActions() = with(binding) {

        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener { newMessageViewModel.editorAction.value = action to null }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorClock, EditorAction.CLOCK)
    }

    private fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
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
