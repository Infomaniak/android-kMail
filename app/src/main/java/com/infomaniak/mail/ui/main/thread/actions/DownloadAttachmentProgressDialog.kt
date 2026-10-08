/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.utils.attachment.AttachmentOpeningManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadAttachmentProgressDialog : DownloadProgressDialog() {
    private val navigationArgs: DownloadAttachmentProgressDialogArgs by navArgs()
    private var hasStartedDownload = false

    @Inject
    lateinit var attachmentOpeningManager: AttachmentOpeningManager

    override val dialogTitle: String by lazy { navigationArgs.attachmentName }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding.icon.isVisible = true
        binding.icon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), navigationArgs.attachmentType.icon))
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun download() {
        if (hasStartedDownload) return

        hasStartedDownload = true
        val navController = findNavController()
        val backStackEntry = navController.currentBackStackEntry
        attachmentOpeningManager.requestOpen(
            localUuid = navigationArgs.attachmentLocalUuid,
            scope = lifecycleScope,
            intentType = navigationArgs.intentType,
            onFinished = {
                if (navController.currentBackStackEntry === backStackEntry) navController.popBackStack()
            },
        )
    }
}
