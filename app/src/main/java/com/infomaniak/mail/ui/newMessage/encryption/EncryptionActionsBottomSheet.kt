/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage.encryption

import android.os.Bundle
import android.text.SpannedString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetEncryptionActionsBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.thread.SubjectFormatter
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.ui.newMessage.NewMessageFragment
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel
import com.infomaniak.mail.utils.extensions.postfixWithTag
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EncryptionActionsBottomSheet : ActionsBottomSheetDialog() {

    private var binding: BottomSheetEncryptionActionsBinding by safeBinding()
    private val navigationArgs: EncryptionActionsBottomSheetArgs by navArgs()
    private val encryptionViewModel: EncryptionViewModel by activityViewModels()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    override val mainViewModel = null

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetEncryptionActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val unencryptableRecipientsCount = encryptionViewModel.unencryptableRecipients.value?.toSet()?.count() ?: 0
        val isValidEncryption = unencryptableRecipientsCount == 0 || navigationArgs.password.isNotBlank()

        val tagColor = SubjectFormatter.TagColor(
            backgroundColorRes = if (isValidEncryption) R.color.validEncryptionTagColor else R.color.partialEncryptionTagColor,
            textColorRes = R.color.onEncryptionTagColor,
        )

        encryptionStatusTitle.text = context.postfixWithTag(
            original = encryptionStatusTitle.text,
            tagRes = if (isValidEncryption) R.string.settingsEnabled else R.string.encryptedStatePanelStatePartialLabel,
            tagColor = tagColor,
        )

        encryptionStatusDescription.text = if (isValidEncryption) {
            getString(R.string.encryptedStatePanelEnable)
        } else {
            computePartialEncryptionDescription(unencryptableRecipientsCount)
        }

        protectWithPassword.apply {
            // Password must be visible when there are users needing password but no password,
            // or when a password is set even if there are no recipients
            isVisible = !isValidEncryption || navigationArgs.password.isNotBlank()
            val title = if (isValidEncryption) {
                R.string.encryptedMessageUpdatePasswordButton
            } else {
                R.string.encryptedMessageAddPasswordButton
            }
            setTitle(title)
        }

        setupListeners()
    }

    /**
     * Computes a spanned string with consists of a first plural string with the plural argument in bold,
     * then another string that represents a second sentence.
     */
    private fun computePartialEncryptionDescription(unencryptableRecipientsCount: Int): SpannedString {
        val plural = resources.getQuantityString(
            R.plurals.encryptedMessageIncompleteUser,
            unencryptableRecipientsCount,
            unencryptableRecipientsCount,
        )

        return buildSpannedString {
            val matchedValues = Regex("([^0-9]+)(.+)\\.").find(plural)?.groupValues ?: emptyList()
            if (matchedValues.isNotEmpty()) {
                val (_, start, boldEnd) = matchedValues
                append(start)
                bold { append(boldEnd) }
                append(". ") // Add back the full stop of the sentence, and a space before the next sentence.
            }

            append(getString(R.string.encryptedStatePanelIncomplete))
        }
    }

    private fun setupListeners() {
        binding.protectWithPassword.setClosingOnClickListener {
            MatomoMail.trackEncryptionEvent(MatomoName.OpenPasswordView)
            safelyNavigate(
                resId = R.id.encryptionPasswordFragment,
                substituteClassName = NewMessageFragment::class.java.name,
            )
        }
        binding.disableEncryption.setClosingOnClickListener {
            MatomoMail.trackEncryptionEvent(MatomoName.Disable)
            newMessageViewModel.isEncryptionActivated.postValue(false)
            snackbarManager.postValue(getString(R.string.encryptedMessageSnackbarEncryptionDisabled))
        }
    }
}
