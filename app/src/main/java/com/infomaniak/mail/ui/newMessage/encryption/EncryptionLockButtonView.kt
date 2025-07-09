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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewEncryptionLockButtonBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch

class EncryptionLockButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewEncryptionLockButtonBinding.inflate(LayoutInflater.from(context), this, true) }
    private var loadingJob: Job? = null

    val encryptionButton get() = binding.encryptionButton
    var unencryptableRecipientsCount: Int? = null
        set(value) {
            field = value
            updateUnencryptableCountUi()
        }

    var encryptionStatus: EncryptionStatus = EncryptionStatus.Unencrypted
        set(value) {
            if (value == field) return
            field = value
            setToolbarButtonUi()
        }

    private fun setToolbarButtonUi() {
        resetLoadingJob()

        when (encryptionStatus) {
            EncryptionStatus.Unencrypted -> setIconUi(
                iconRes = R.drawable.ic_lock_open_filled,
                iconTintRes = R.color.iconColor,
                shouldDisplayPastille = false,
            )
            EncryptionStatus.PartiallyEncrypted -> setIconUi(
                iconRes = R.drawable.ic_lock_filled,
                iconTintRes = R.color.encryptionIconColor,
                shouldDisplayPastille = true,
            )
            EncryptionStatus.Encrypted -> setIconUi(
                iconRes = R.drawable.ic_lock_filled,
                iconTintRes = R.color.encryptionIconColor,
                shouldDisplayPastille = false,
            )
            EncryptionStatus.Loading -> with(binding) {
                encryptionButton.isEnabled = false
                loadWithDelay()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setIconUi(@DrawableRes iconRes: Int, @ColorRes iconTintRes: Int, shouldDisplayPastille: Boolean) {
        with(binding) {
            unencryptedRecipientLoader.isGone = true
            encryptionButton.apply {
                isEnabled = true
                setIconResource(iconRes)
                setIconTintResource(iconTintRes)
            }
            updateUnencryptableCountUi()
            unencryptableGroup.isVisible = shouldDisplayPastille
        }
    }

    private fun updateUnencryptableCountUi() {
        val count = when (val count = unencryptableRecipientsCount) {
            null, 0 -> null
            in 1..9 -> count.toString()
            else -> "9+"
        }

        binding.unencryptedRecipientText.text = count
    }

    /**
     * Add a delay before displaying the loader, to avoid small blink at draft's opening
     */
    private fun loadWithDelay() {
        loadingJob = CoroutineScope(Dispatchers.Default).launch {
            delay(200)
            Dispatchers.Main {
                binding.unencryptedRecipientLoader.isVisible = true
                binding.unencryptableGroup.isGone = true
            }
        }
    }

    private fun resetLoadingJob() {
        loadingJob?.cancel()
        loadingJob = null
    }
}
