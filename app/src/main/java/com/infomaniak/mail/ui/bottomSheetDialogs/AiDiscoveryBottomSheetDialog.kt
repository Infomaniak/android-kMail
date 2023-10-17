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

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.infomaniak.mail.MatomoMail.trackAiWriterEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.ai.AiPromptOpeningStatus
import com.infomaniak.mail.ui.newMessage.AiViewModel

class AiDiscoveryBottomSheetDialog : DiscoveryBottomSheetDialog() {

    private val aiViewModel: AiViewModel by activityViewModels()

    override val titleRes = R.string.aiDiscoveryTitle
    override val descriptionRes = R.string.aiDiscoveryDescription
    override val illustrationRes = R.drawable.illustration_discover_ai
    override val positiveButtonRes = R.string.buttonTry
    override val trackMatomoWithCategory: (name: String) -> Unit = { trackAiWriterEvent(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    override fun onPositiveButtonClicked() {
        aiViewModel.aiPromptOpeningStatus.value = AiPromptOpeningStatus(isOpened = true)
    }
}
