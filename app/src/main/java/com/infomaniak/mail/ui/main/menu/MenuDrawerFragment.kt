/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.thread.ThreadListViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toggleChevron
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.ceil

class MenuDrawerFragment(private val closeDrawer: (() -> Unit)? = null) : Fragment() {

    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()
    private val threadListViewModel: ThreadListViewModel by activityViewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var currentMailboxJob: Job? = null
    private var mailboxesJob: Job? = null
    private var foldersJob: Job? = null

    private val addressAdapter = SettingAddressAdapter(displayIcon = false) {
        threadListViewModel.loadMailData()
        threadListViewModel.setup()
        closeDrawer()
    }

    private val customFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })
    private val defaultFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        menuDrawerViewModel.setup()
        setupAdapters()
        setupListener()
    }

    private fun setupAdapters() = with(binding) {
        addressesList.adapter = addressAdapter
        customFoldersList.adapter = customFoldersAdapter
        defaultFoldersList.adapter = defaultFoldersAdapter
    }

    private fun setupListener() = with(binding) {
        settingsButton.setOnClickListener {
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        accountSwitcher.setOnClickListener {
            expandedAccountSwitcher.apply {
                isVisible = !isVisible
                expandAccountButton.toggleChevron(!isVisible)
            }
        }
        manageAccount.setOnClickListener {
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToManageMailAddressFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        addAccount.setOnClickListener { startActivity(Intent(context, LoginActivity::class.java)) }
        inboxFolder.setOnClickListener { openFolder(R.string.inboxFolder) }
        customFolders.setOnClickListener {
            customFoldersList.apply {
                isVisible = !isVisible
                expandCustomFolderButton.toggleChevron(!isVisible)
            }
        }
        feedbacks.setOnClickListener {
            closeDrawer()
            context?.openUrl(BuildConfig.FEEDBACK_USER_REPORT)
        }
        help.setOnClickListener {
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToHelpFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        importMails.setOnClickListener {
            closeDrawer()
            // TODO: import mails
        }
        restoreMails.setOnClickListener {
            closeDrawer()
            // TODO: restore mails
        }
    }

    override fun onResume() {
        super.onResume()

        listenToCurrentMailbox()
        listenToMailboxes()
        listenToFolders()
        listenToCurrentMailboxSize()
    }

    override fun onPause() {
        currentMailboxJob?.cancel()
        currentMailboxJob = null

        mailboxesJob?.cancel()
        mailboxesJob = null

        foldersJob?.cancel()
        foldersJob = null

        super.onPause()
    }

    private fun listenToCurrentMailbox() {
        if (currentMailboxJob != null) currentMailboxJob?.cancel()

        currentMailboxJob = menuDrawerViewModel.viewModelScope.launch(Dispatchers.Main) {
            MailData.currentMailboxFlow.filterNotNull().collect { currentMailbox ->
                binding.accountSwitcherText.text = currentMailbox.email
            }
        }
    }

    private fun listenToMailboxes() = with(menuDrawerViewModel) {
        if (mailboxesJob != null) mailboxesJob?.cancel()

        mailboxesJob = viewModelScope.launch(Dispatchers.Main) {
            uiMailboxesFlow.filterNotNull().collect { mailboxes ->
                addressAdapter.setMailboxes(mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId })
                addressAdapter.notifyDataSetChanged()
                manageStorageFooterVisibility()
            }
        }
    }

    private fun listenToFolders() = with(menuDrawerViewModel) {
        if (foldersJob != null) foldersJob?.cancel()

        foldersJob = viewModelScope.launch(Dispatchers.Main) {
            uiFoldersFlow.filterNotNull().collect { folders ->
                onFoldersChange(folders)
                // TODO : Manage embedded folders ?
            }
        }
    }

    private fun listenToCurrentMailboxSize() = with(binding) {
        menuDrawerViewModel.mailboxSize.observe(viewLifecycleOwner) { sizeUsed ->
            if (sizeUsed == null) return@observe

            val formattedSize = FormatterFileSize.formatShortFileSize(root.context, sizeUsed)
            val formattedTotalSize = FormatterFileSize.formatShortFileSize(root.context, LIMITED_MAILBOX_SIZE)

            storageText.text = context?.resources?.getString(R.string.menuDrawerMailboxStorage, formattedSize, formattedTotalSize)
            storageIndicator.progress = ceil(100.0 * sizeUsed / LIMITED_MAILBOX_SIZE).toInt()
        }
    }

    private fun manageStorageFooterVisibility() {
        MailData.currentMailboxFlow.value?.let { mailbox ->
            binding.storageLayout.isVisible = mailbox.isLimited
            if (mailbox.isLimited) activity?.let { menuDrawerViewModel.getMailBoxStorage(mailbox, it) }
        }
    }

    private fun closeDrawer() = with(binding) {
        closeDrawer?.invoke()
        closeDropdowns()
    }

    fun closeDropdowns() = with(binding) {
        expandedAccountSwitcher.isGone = true
        expandAccountButton.rotation = 0.0f
        customFoldersList.isGone = true
        expandCustomFolderButton.rotation = 0.0f
    }

    private fun openFolder(@StringRes folderNameId: Int) {
        openFolder(getString(folderNameId))
    }

    private fun openFolder(folderName: String) = context?.let {
        menuDrawerViewModel.openFolder(folderName, it)
        closeDrawer()
    }

    private fun onFoldersChange(folders: List<Folder>) {
        binding.inboxFolderBadge.text = getInboxFolder(folders)?.getUnreadCountOrNull()

        defaultFoldersAdapter.setFolders(getDefaultFolders(folders))
        defaultFoldersAdapter.notifyDataSetChanged()

        customFoldersAdapter.setFolders(getCustomFolders(folders))
        customFoldersAdapter.notifyDataSetChanged()
    }

    private fun getInboxFolder(folders: List<Folder>) = folders.find { it.role == Folder.FolderRole.INBOX }

    private fun getDefaultFolders(folders: List<Folder>) = folders.filter {
        it.role != null && it.role != Folder.FolderRole.INBOX
    }.sortedBy { it.role?.order }

    private fun getCustomFolders(folders: List<Folder>) = folders.filter { it.role == null }.sortedByDescending { it.isFavorite }

    companion object {
        const val LIMITED_MAILBOX_SIZE: Long = 20L * 1 shl 30
    }
}
