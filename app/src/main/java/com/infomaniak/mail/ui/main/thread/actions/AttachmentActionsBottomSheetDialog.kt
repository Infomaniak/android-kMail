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
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackAttachmentActionsEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.databinding.BottomSheetAttachmentActionsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType.SAVE_TO_DRIVE
import com.infomaniak.mail.utils.AttachmentIntentUtils.getIntentOrGoToPlaystore
import com.infomaniak.mail.utils.AttachmentIntentUtils.openWithIntent
import com.infomaniak.mail.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class AttachmentActionsBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetAttachmentActionsBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val attachmentActionsViewModel: AttachmentActionsViewModel by viewModels()

    @Inject
    lateinit var permissionUtils: PermissionUtils

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetAttachmentActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(attachmentActionsViewModel) {
        super.onViewCreated(view, savedInstanceState)

        if (attachment == null) {
            findNavController().popBackStack()
            return@with
        }

        permissionUtils.registerDownloadManagerPermission(this@AttachmentActionsBottomSheetDialog)
        binding.attachmentDetails.setDetails(attachment)
        setupListeners(attachment)
    }

    private fun setupListeners(attachment: Attachment) = with(binding) {
        openWithItem.setClosingOnClickListener {
            trackAttachmentActionsEvent("open")
            if (attachment.openWithIntent(context).hasSupportedApplications(context)) {
                attachment.executeIntent(OPEN_WITH)
            } else {
                mainViewModel.snackBarManager.setValue(getString(RCore.string.errorNoSupportingAppFound))
            }
        }
        kDriveItem.setClosingOnClickListener {
            trackAttachmentActionsEvent("saveToKDrive")
            attachment.executeIntent(SAVE_TO_DRIVE)
        }
        deviceItem.setOnClickListener {
            trackAttachmentActionsEvent("download")
            scheduleDownloadManager(attachment.downloadUrl, attachment.name)
        }
    }

    private fun Attachment.executeIntent(intentType: AttachmentIntentType) {
        if (hasUsableCache(requireContext()) || isInlineCachedFile(requireContext())) {
            getIntentOrGoToPlaystore(requireContext(), intentType)?.let(::startActivity)
        } else {
            navigateToDownloadProgressDialog(intentType)
        }
    }

    private fun Attachment.navigateToDownloadProgressDialog(intentType: AttachmentIntentType) {
        safeNavigate(
            resId = R.id.downloadAttachmentProgressDialog,
            args = DownloadAttachmentProgressDialogArgs(
                attachmentResource = resource!!,
                attachmentName = name,
                attachmentType = getFileTypeFromMimeType(),
                intentType = intentType,
            ).toBundle(),
            currentClassName = AttachmentActionsBottomSheetDialog::class.java.name,
        )
    }

    private fun scheduleDownloadManager(downloadUrl: String, filename: String) {
        if (permissionUtils.hasDownloadManagerPermission) {
            scheduleDownloadAndPopBack(downloadUrl, filename)
        } else {
            permissionUtils.requestDownloadManagerPermission { scheduleDownloadAndPopBack(downloadUrl, filename) }
        }
    }

    private fun scheduleDownloadAndPopBack(downloadUrl: String, filename: String) {
        mainViewModel.scheduleDownload(downloadUrl, filename)
        findNavController().popBackStack()
    }
}
