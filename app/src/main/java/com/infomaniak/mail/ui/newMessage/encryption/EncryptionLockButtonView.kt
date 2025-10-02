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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.mail.databinding.ViewEncryptionLockButtonBinding

class EncryptionLockButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewEncryptionLockButtonBinding.inflate(LayoutInflater.from(context), this, true) }

    private val loadingDelayTimer by lazy {
        // Add a delay before displaying the loader, to avoid small blink at draft's opening
        Utils.createRefreshTimer {
            binding.unencryptedRecipientLoader.isVisible = true
            binding.unencryptableGroup.isGone = true
        }
    }

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

    init {
        setToolbarButtonUi()
    }

    override fun onDetachedFromWindow() {
        loadingDelayTimer.cancel()
        super.onDetachedFromWindow()
    }

    private fun setToolbarButtonUi() {
        loadingDelayTimer.cancel()
        setIconUi(encryptionStatus)
    }

    @SuppressLint("SetTextI18n")
    private fun setIconUi(encryptionStatus: EncryptionStatus) = with(encryptionStatus) {
        binding.encryptionButton.apply {
            isEnabled = true
            setIconResource(iconRes)
            setIconTintResource(iconTintRes)
        }
        updateUnencryptableCountUi()
        binding.unencryptableGroup.isVisible = shouldDisplayPastille && !isLoading

        binding.unencryptedRecipientLoader.isVisible = isLoading
        if (isLoading) {
            binding.encryptionButton.isEnabled = false
            loadingDelayTimer.start()
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
}
