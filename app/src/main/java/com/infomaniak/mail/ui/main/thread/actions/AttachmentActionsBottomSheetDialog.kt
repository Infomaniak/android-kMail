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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.hasSupportedApplications
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.databinding.BottomSheetAttachmentActionsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AttachmentActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetAttachmentActionsBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val attachmentActionsViewModel: AttachmentActionsViewModel by viewModels()

    private val permissionUtils by lazy { PermissionUtils(this) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetAttachmentActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        attachmentActionsViewModel.attachmentLive.observe(viewLifecycleOwner) { attachment ->
            if (attachment == null) {
                findNavController().popBackStack()
                return@observe
            }

            attachmentDetails.setDetails(attachment)

            supportedApplication.setOnClickListener {
                if (attachment.openWithIntent(requireContext()).hasSupportedApplications(requireContext())) {
                    trackAttachmentActionsEvent("open")
                    attachment.display()
                } else {
                    trackAttachmentActionsEvent("download")
                    mainViewModel.snackBarManager.setValue(getString(R.string.snackbarDownloadInProgress))
                    scheduleDownloadManager(attachment.downloadUrl, attachment.name)
                }
            }
        }
    }


    private fun Attachment.display() {
        if (hasUsableCache(requireContext()) || isInlineCachedFile(requireContext())) {
            startActivity(openWithIntent(requireContext()))
        } else {
            safeNavigate(
                resId = R.id.downloadAttachmentProgressDialog,
                args = DownloadAttachmentProgressDialogArgs(
                    attachmentResource = resource!!,
                    attachmentName = name,
                    attachmentType = getFileTypeFromMimeType(),
                ).toBundle(),
            )
        }
    }

    private fun scheduleDownloadManager(downloadUrl: String, filename: String) {

        fun scheduleDownloadManager() = mainViewModel.scheduleDownload(downloadUrl, filename)

        if (permissionUtils.hasDownloadManagerPermission) {
            scheduleDownloadManager()
        } else {
            permissionUtils.requestDownloadManagerPermission { scheduleDownloadManager() }
        }
    }
}
