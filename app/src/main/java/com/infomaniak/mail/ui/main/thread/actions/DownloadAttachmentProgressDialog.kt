/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.setBackNavigationResult
import com.infomaniak.mail.utils.extensions.AttachmentExtensions
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.getIntentOrGoToPlayStore
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadAttachmentProgressDialog : DownloadProgressDialog() {
    private val navigationArgs: DownloadAttachmentProgressDialogArgs by navArgs()
    private val downloadAttachmentViewModel: DownloadAttachmentViewModel by viewModels()

    override val dialogTitle: String? by lazy { navigationArgs.attachmentName }
    override val dialogIconDrawableRes: Int? by lazy { navigationArgs.attachmentType.icon }

    override fun download() {
        downloadAttachmentViewModel.downloadAttachment().observe(this) { cachedAttachment ->
            if (cachedAttachment == null) {
                popBackStackWithError()
            } else {
                cachedAttachment.getIntentOrGoToPlayStore(requireContext(), navigationArgs.intentType)?.let { openWithIntent ->
                    setBackNavigationResult(AttachmentExtensions.DOWNLOAD_ATTACHMENT_RESULT, openWithIntent)
                } ?: run { findNavController().popBackStack() }
            }
        }
    }
}
