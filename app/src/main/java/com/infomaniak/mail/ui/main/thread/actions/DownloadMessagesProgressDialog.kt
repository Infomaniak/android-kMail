/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalSplittiesApi::class)

package com.infomaniak.mail.ui.main.thread.actions

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.common.extensions.goToAppStore
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.mail.utils.LocalStorageUtils.clearEmlCacheDir
import com.infomaniak.mail.utils.SaveOnKDriveUtils.DRIVE_PACKAGE
import com.infomaniak.mail.utils.SaveOnKDriveUtils.SAVE_EXTERNAL_ACTIVITY_CLASS
import com.infomaniak.mail.utils.SaveOnKDriveUtils.canSaveOnKDrive
import kotlinx.coroutines.launch
import splitties.coroutines.suspendLazy
import splitties.experimental.ExperimentalSplittiesApi

class DownloadMessagesProgressDialog : DownloadProgressDialog() {
    private val downloadThreadsViewModel: DownloadMessagesViewModel by viewModels()

    override val dialogTitle: String = " " // Non-empty placeholder value to ensure the dialog's title view gets created.

    private val dialogTitleLazy = lifecycleScope.suspendLazy { downloadThreadsViewModel.getDialogName() }

    override fun onCreate(savedInstanceState: Bundle?) {
        observeDownload()
        super.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = super.onCreateDialog(savedInstanceState).also { dialog ->
        lifecycleScope.launch {
            dialog.setTitle(dialogTitleLazy())
        }
    }

    override fun download() {
        downloadThreadsViewModel.downloadMessages(mainViewModel.currentMailbox.value)
    }

    private fun observeDownload() {
        downloadThreadsViewModel.downloadMessagesLiveData.observe(this) { messageUris ->
            messageUris?.openKDriveOrAppStore(requireContext())?.let { openKDriveIntent ->
                setBackNavigationResult(DOWNLOAD_MESSAGES_RESULT, openKDriveIntent)
            } ?: run {
                clearEmlCacheDir(requireContext())
                if (messageUris == null) popBackStackWithError() else findNavController().popBackStack()
            }
        }
    }

    private fun List<Uri>.openKDriveOrAppStore(context: Context): Intent? {
        return if (canSaveOnKDrive(context)) {
            saveToDriveIntent()
        } else {
            context.goToAppStore(DRIVE_PACKAGE)
            null
        }
    }

    private fun List<Uri>.saveToDriveIntent(): Intent {
        return Intent().apply {
            component = ComponentName(DRIVE_PACKAGE, SAVE_EXTERNAL_ACTIVITY_CLASS)
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(this@saveToDriveIntent))
        }
    }

    companion object {
        const val DOWNLOAD_MESSAGES_RESULT = "download_messages_result"
    }
}
