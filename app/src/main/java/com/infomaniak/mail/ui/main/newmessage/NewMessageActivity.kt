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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.navArgs
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.drafts.Draft.DraftAction
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.ThemedActivity
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.infomaniak.lib.core.R as RCore

class NewMessageActivity : ThemedActivity() {

    private val navigationArgs: NewMessageActivityArgs by navArgs()

    private val mainViewModel: MainViewModel by viewModels()
    private val newMessageViewModel: NewMessageViewModel by viewModels()

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

            toolbar.setNavigationOnClickListener { closeDraft() }

            toolbar.setOnMenuItemClickListener {
                with(newMessageFragment) {
                    if (newMessageViewModel.sendDraftAction(DraftAction.SEND, getFromMailbox().email, getSubject(), getBody())) {
                        finish()
                    } else {
                        showSnackbar(RCore.string.anErrorHasOccurred)
                    }
                    true
                }
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

        loadDraft()
    }

    private fun loadDraft() = with(navigationArgs) {
        lifecycleScope.launch(Dispatchers.IO) {
            val draft = if (draftResource != null && messageUid != null) {
                mainViewModel.fetchDraft(draftResource!!, messageUid!!)
            } else {
                null
            }
            newMessageViewModel.loadDraft(draft, navigationArgs.draftUuid)
        }
    }

    private fun ActivityNewMessageBinding.handleEditorToggle() {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
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
        view.setOnClickListener { newMessageViewModel.editorAction.value = action }
    }

    fun closeDraft() = with(newMessageFragment) {
        newMessageViewModel.sendDraftAction(DraftAction.SAVE, getFromMailbox().email, getSubject(), getBody())
        finish()
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
