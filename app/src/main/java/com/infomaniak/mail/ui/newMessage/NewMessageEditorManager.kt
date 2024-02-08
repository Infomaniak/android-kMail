/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.WindowManager
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.FilePicker
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.notYetImplemented
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject
import com.google.android.material.R as RMaterial

@FragmentScoped
class NewMessageEditorManager @Inject constructor(
    @ActivityContext private val activityContext: Context,
) : NewMessageManager() {

    private inline val activity get() = activityContext as Activity

    private var _aiManager: NewMessageAiManager? = null
    private inline val aiManager: NewMessageAiManager get() = _aiManager!!
    private var _filePicker: FilePicker? = null
    private inline val filePicker: FilePicker get() = _filePicker!!

    fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        aiManager: NewMessageAiManager,
        filePicker: FilePicker,
    ) {
        super.initValues(newMessageViewModel, binding, fragment, freeReferences = {
            _aiManager = null
            _filePicker = null
        })

        _aiManager = aiManager
        _filePicker = filePicker
    }

    fun observeEditorActions() {
        newMessageViewModel.editorAction.observe(viewLifecycleOwner) { (editorAction, /*isToggled*/ _) ->
            when (editorAction) {
                EditorAction.ATTACHMENT -> {
                    filePicker.open { uris ->
                        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        newMessageViewModel.importAttachmentsToCurrentDraft(uris)
                    }
                }
                EditorAction.CAMERA -> fragment.notYetImplemented()
                EditorAction.LINK -> fragment.notYetImplemented()
                EditorAction.CLOCK -> fragment.notYetImplemented()
                EditorAction.AI -> aiManager.openAiPrompt()
            }
        }
    }

    fun setupEditorActions() = with(binding) {
        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener {
                // TODO: Don't forget to add in this `if` all actions that make the app go to background.
                if (action == EditorAction.ATTACHMENT) newMessageViewModel.shouldExecuteDraftActionWhenStopping = false
                context.trackEvent("editorActions", action.matomoValue)
                newMessageViewModel.editorAction.value = action to null
            }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorLink, EditorAction.LINK)
        linkEditor(editorClock, EditorAction.CLOCK)
        linkEditor(editorAi, EditorAction.AI)
    }

    fun setupEditorFormatActionsToggle() = with(binding) {
        editorTextOptions.setOnClickListener {
            newMessageViewModel.isEditorExpanded = !newMessageViewModel.isEditorExpanded
            updateEditorVisibility(newMessageViewModel.isEditorExpanded)
        }
    }

    private fun updateEditorVisibility(isEditorExpanded: Boolean) = with(binding) {
        val color = if (isEditorExpanded) {
            context.getAttributeColor(RMaterial.attr.colorPrimary)
        } else {
            context.getColor(R.color.iconColor)
        }
        val resId = if (isEditorExpanded) R.string.buttonTextOptionsClose else R.string.buttonTextOptionsOpen

        editorTextOptions.apply {
            iconTint = ColorStateList.valueOf(color)
            contentDescription = context.getString(resId)
        }

        editorActions.isGone = isEditorExpanded
        textEditing.isVisible = isEditorExpanded
    }

    enum class EditorAction(val matomoValue: String) {
        ATTACHMENT("importFile"),
        CAMERA("importFromCamera"),
        LINK("addLink"),
        CLOCK(MatomoMail.ACTION_POSTPONE_NAME),
        AI("aiWriter"),
        // BOLD("bold"),
        // ITALIC("italic"),
        // UNDERLINE("underline"),
        // STRIKE_THROUGH("strikeThrough"),
        // UNORDERED_LIST("unorderedList"),
    }
}
