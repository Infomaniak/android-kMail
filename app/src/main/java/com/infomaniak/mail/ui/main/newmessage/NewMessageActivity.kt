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
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.models.drafts.Draft
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.apply {
            setContentView(root)
            setupClickListeners()
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

    private fun setupClickListeners() = with(binding) {

        toolbar.setNavigationOnClickListener {
            toolbar.setNavigationOnClickListener(null)
            closeDraft()
        }

        toolbar.setOnMenuItemClickListener {
            toolbarOnMenuItemClickListener()
            true
        }
    }

    private fun toolbarOnMenuItemClickListener() {
        binding.toolbar.setOnMenuItemClickListener(null)
        newMessageViewModel.sendDraft { isSuccess ->
            if (isSuccess) finish() else {
                binding.toolbar.setOnMenuItemClickListener {
                    toolbarOnMenuItemClickListener()
                    true
                }
                showSnackbar(RCore.string.anErrorHasOccurred)
            }
        }
    }

    private fun linkEditor(view: MaterialButton, action: EditorAction) {
        view.setOnClickListener { newMessageViewModel.editorAction.value = action }
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

    private fun loadDraft() {
        lifecycleScope.launch(Dispatchers.IO) {

            val draft = getDraftFromApi()
                ?: getDraftFromRealm()
                ?: createNewDraft()

            newMessageViewModel.currentDraftUuid.postValue(draft.uuid)
        }
    }

    private fun getDraftFromApi(): Draft? = with(navigationArgs) {
        if (draftResource != null && messageUid != null) {
            mainViewModel.fetchDraft(draftResource!!, messageUid!!)
        } else {
            null
        }
    }

    private fun getDraftFromRealm(): Draft? = navigationArgs.draftUuid?.let(DraftController::getDraftSync)

    private fun createNewDraft(): Draft {
        newMessageViewModel.isNewMessage = true
        return Draft().apply {
            isOffline = true
            initLocalValues()
        }.also(DraftController::upsertDraft)
    }

    fun closeDraft() {
        newMessageViewModel.saveDraft { isSuccess ->
            if (!isSuccess && newMessageViewModel.isNewMessage) {
                newMessageViewModel.currentDraftUuid.value?.let(DraftController::deleteDraft)
            }
            finish()
        }
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
