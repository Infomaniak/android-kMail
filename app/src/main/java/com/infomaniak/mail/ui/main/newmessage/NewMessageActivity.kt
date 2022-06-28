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
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.main.newmessage.NewMessageActivity.EditorAction.*

class NewMessageActivity : AppCompatActivity() {

    private val viewModel: NewMessageViewModel by viewModels()

    private val binding: ActivityNewMessageBinding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.apply {
            setContentView(root)

            toolbar.setNavigationOnClickListener { onBackPressed() }
            toolbar.setOnMenuItemClickListener {
                if (sendMail()) finish()
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
        val color = if (isEditorExpanded) R.color.pink_mail else R.color.iconColor
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

    private fun sendMail(): Boolean {
        val newMessageFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
            it.childFragmentManager.primaryNavigationFragment as NewMessageFragment
        }!!

        // TODO : Replace logs with actual API call
        Log.d("sendingMail", "FROM: ${newMessageFragment.getFromMailbox().email}")
        Log.d("sendingMail", "TO: ${viewModel.recipients.map { "${it.name} (${it.email})" }}")
        Log.d("sendingMail", "CC: ${viewModel.cc.map { "${it.name} (${it.email})" }}")
        Log.d("sendingMail", "BCC: ${viewModel.bcc.map { "${it.name} (${it.email})" }}")
        Log.d("sendingMail", "SUBJECT: ${newMessageFragment.getSubject()}")
        Log.d("sendingMail", "BODY: ${newMessageFragment.getBody()}")

        if (viewModel.recipients.isEmpty()) return false // Do not send mail if required fields are not filled
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
