/*
 * Infomaniak ikMail - Android
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

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.ACTION_POSTPONE_NAME
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.utils.*
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.R as RMaterial

@AndroidEntryPoint
class NewMessageActivity : BaseActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(binding.root)

        if (!isAuth()) {
            finish()
            return
        }

        setupSnackBar()
        setupSendButton()
        setupSystemBars()
        setupExternalBanner()

        observeInitSuccess()
    }

    private fun setupExternalBanner() = with(binding) {
        var manuallyClosed = false

        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        closeButton.setOnClickListener {
            manuallyClosed = true
            externalBanner.isGone = true
        }

        informationButton.setOnClickListener {
            val description = resources.getQuantityString(
                R.plurals.externalDialogDescriptionExpeditor,
                externalRecipientQuantity,
                externalRecipientEmail,
            )

            // TODO : Reuse instance?
            createInformationDialog(
                title = getString(R.string.externalDialogTitleRecipient),
                description = description,
                confirmButtonText = R.string.externalDialogConfirmButton,
            ).show()
        }

        newMessageViewModel.isExternalBannerVisible.observe(this@NewMessageActivity) { (email, externalQuantity) ->
            externalBanner.isGone = manuallyClosed || externalQuantity == 0
            externalRecipientEmail = email
            externalRecipientQuantity = externalQuantity
        }
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == AppSettings.DEFAULT_ID) {
            startActivity(Intent(this, LaunchActivity::class.java))
            return false
        }
        return true
    }

    fun finishAppAndRemoveTaskIfNeeded() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    private fun setupSnackBar() {
        newMessageViewModel.snackBarManager.setup(view = binding.root, activity = this)
    }

    private fun setupSendButton() = with(binding) {
        newMessageViewModel.isSendingAllowed.observe(this@NewMessageActivity) {
            sendButton.isEnabled = it
        }

        sendButton.setOnClickListener { tryToSendEmail() }
    }

    private fun tryToSendEmail() {

        fun setSnackBarActivityResult() {
            val resultIntent = Intent()
            resultIntent.putExtra(MainActivity.DRAFT_ACTION_KEY, DraftAction.SEND.name)
            setResult(RESULT_OK, resultIntent)
        }

        fun sendEmail() {
            newMessageViewModel.shouldSendInsteadOfSave = true
            setSnackBarActivityResult()
            finishAppAndRemoveTaskIfNeeded()
        }

        if (newMessageViewModel.draft.subject.isNullOrBlank()) {
            trackNewMessageEvent("sendWithoutSubject")
            createDescriptionDialog(
                title = getString(R.string.emailWithoutSubjectTitle),
                description = getString(R.string.emailWithoutSubjectDescription),
                confirmButtonText = R.string.buttonContinue,
                onPositiveButtonClicked = {
                    trackNewMessageEvent("sendWithoutSubjectConfirm")
                    sendEmail()
                },
            ).show()
        } else {
            sendEmail()
        }
    }

    private fun setupSystemBars() {
        val backgroundColor = getColor(R.color.newMessageBackgroundColor)
        window.apply {
            statusBarColor = backgroundColor
            updateNavigationBarColor(backgroundColor)
        }
    }

    private fun observeInitSuccess() {
        newMessageViewModel.isInitSuccess.observe(this) { isSuccess ->
            if (isSuccess) {
                setupEditorActions()
                setupEditorFormatActionsToggle()
            }
        }
    }

    private fun setupEditorActions() = with(binding) {

        fun linkEditor(view: MaterialButton, action: EditorAction) {
            view.setOnClickListener {
                // TODO: Don't forget to add in this `if` all actions that make the app go to background.
                if (action == EditorAction.ATTACHMENT) newMessageViewModel.shouldExecuteDraftActionWhenStopping = false
                trackEvent("editorActions", action.matomoValue)
                newMessageViewModel.editorAction.value = action to null
            }
        }

        linkEditor(editorAttachment, EditorAction.ATTACHMENT)
        linkEditor(editorCamera, EditorAction.CAMERA)
        linkEditor(editorLink, EditorAction.LINK)
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

    enum class EditorAction(val matomoValue: String) {
        ATTACHMENT("importFile"),
        CAMERA("importFromCamera"),
        LINK("addLink"),
        CLOCK(ACTION_POSTPONE_NAME),
        BOLD("bold"),
        ITALIC("italic"),
        UNDERLINE("underline"),
        STRIKE_THROUGH("strikeThrough"),
        UNORDERED_LIST("unorderedList"),
    }
}
