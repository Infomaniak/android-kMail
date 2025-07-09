/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.content.res.ColorStateList
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionMessageManager
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.notYetImplemented
import dagger.hilt.android.scopes.FragmentScoped
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.material.R as RMaterial

@FragmentScoped
class NewMessageEditorManager @Inject constructor(private val insertLinkDialog: InsertLinkDialog) : NewMessageManager() {

    private var _aiManager: NewMessageAiManager? = null
    private inline val aiManager: NewMessageAiManager get() = _aiManager!!

    private var _encryptionManager: EncryptionMessageManager? = null
    private inline val encryptionManager: EncryptionMessageManager get() = _encryptionManager!!

    private var _openFilePicker: (() -> Unit)? = null

    fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        aiManager: NewMessageAiManager,
        encryptionManager: EncryptionMessageManager,
        openFilePicker: () -> Unit,
    ) {
        super.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = fragment,
            freeReferences = { _aiManager = null },
        )

        _aiManager = aiManager
        _encryptionManager = encryptionManager
        _openFilePicker = openFilePicker
    }

    fun observeEditorFormatActions() = with(binding) {
        newMessageViewModel.editorAction.observe(viewLifecycleOwner) { (editorAction, _) ->
            when (editorAction) {
                EditorAction.ATTACHMENT -> _openFilePicker?.invoke()
                EditorAction.CAMERA -> fragment.notYetImplemented()
                EditorAction.LINK -> if (buttonLink.isActivated) {
                    editorWebView.unlink()
                } else {
                    insertLinkDialog.show { displayText, url ->
                        editorWebView.createLink(displayText, url)
                    }
                }
                EditorAction.AI -> aiManager.openAiPrompt()
                EditorAction.BOLD -> editorWebView.toggleBold()
                EditorAction.ITALIC -> editorWebView.toggleItalic()
                EditorAction.UNDERLINE -> editorWebView.toggleUnderline()
                EditorAction.STRIKE_THROUGH -> editorWebView.toggleStrikeThrough()
                EditorAction.UNORDERED_LIST -> editorWebView.toggleUnorderedList()
                EditorAction.ENCRYPTION -> encryptionManager.toggleEncryption()
            }
        }
    }

    fun setupEditorFormatActions() = with(binding) {
        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener {
                context.trackEvent("editorActions", action.matomoValue)
                newMessageViewModel.editorAction.value = action to null
            }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorAi, EditorAction.AI)
        linkEditor(encryptionButton, EditorAction.ENCRYPTION)

        linkEditor(buttonBold, EditorAction.BOLD)
        linkEditor(buttonItalic, EditorAction.ITALIC)
        linkEditor(buttonUnderline, EditorAction.UNDERLINE)
        linkEditor(buttonStrikeThrough, EditorAction.STRIKE_THROUGH)
        linkEditor(buttonList, EditorAction.UNORDERED_LIST)
        linkEditor(buttonLink, EditorAction.LINK)
    }

    fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorFormatActionsVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    private fun updateEditorFormatActionsVisibility(isExpanded: Boolean) = with(binding) {
        if (isExpanded) editorWebView.requestFocus()

        val color = if (isExpanded) {
            context.getAttributeColor(RMaterial.attr.colorPrimary)
        } else {
            context.getColor(R.color.iconColor)
        }
        val resId = if (isExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            iconTint = ColorStateList.valueOf(color)
            contentDescription = context.getString(resId)
        }

        editorActions.isGone = isExpanded
        sendLayout.isGone = isExpanded
        formatOptionsLayout.isVisible = isExpanded
    }

    fun observeEditorStatus(): Unit = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            editorWebView.editorStatusesFlow.collect {
                buttonBold.isActivated = it.isBold
                buttonItalic.isActivated = it.isItalic
                buttonUnderline.isActivated = it.isUnderlined
                buttonStrikeThrough.isActivated = it.isStrikeThrough
                buttonList.isActivated = it.isUnorderedListSelected
                buttonLink.isActivated = it.isLinkSelected
            }
        }
    }

    enum class EditorAction(val matomoValue: String) {
        ATTACHMENT("importFile"),
        CAMERA("importFromCamera"),
        LINK("addLink"),
        AI("aiWriter"),
        BOLD("bold"),
        ITALIC("italic"),
        UNDERLINE("underline"),
        STRIKE_THROUGH("strikeThrough"),
        UNORDERED_LIST("unorderedList"),
        ENCRYPTION("messageEncryption")
    }
}
