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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.infomaniak.mail.MatomoMail.trackAiWriterEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.ai.AiPromptOpeningStatus
import com.infomaniak.mail.ui.main.newMessage.AiViewModel

class AiDiscoveryBottomSheetDialog : InformationBottomSheetDialog() {

    private val aiViewModel: AiViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        infoIllustration.setBackgroundResource(R.drawable.illustration_discover_ai)
        title.setText(R.string.aiDiscoveryTitle)
        description.setText(R.string.aiDiscoveryDescription)

        actionButton.apply {
            setText(R.string.buttonTry)
            setOnClickListener {
                trackAiWriterEvent("discoverTry")
                aiViewModel.aiPromptOpeningStatus.value = AiPromptOpeningStatus(isOpened = true)
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            trackAiWriterEvent("discoverLater")
            dismiss()
        }

        dialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    override fun onCancel(dialog: DialogInterface) {
        trackAiWriterEvent("discoverLater")
        super.onCancel(dialog)
    }
}
