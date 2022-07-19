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
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter.Companion.sortMailboxes
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.toggleChevron
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.ceil

class MenuDrawerFragment(
    private val closeDrawer: (() -> Unit)? = null,
    private val isDrawerOpen: (() -> Boolean)? = null,
) : Fragment() {

    private val viewModel: MenuDrawerViewModel by viewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var currentMailboxJob: Job? = null
    private var mailboxesJob: Job? = null
    private var foldersJob: Job? = null

    private val addressAdapter = SwitchUserMailboxesAdapter(displayIcon = false) { selectedMailbox ->
        viewModel.switchToMailbox(selectedMailbox)
        // TODO: This is not enough. It won't refresh the MenuDrawer data (ex: unread counts)
        closeDrawer()
    }

    private val customFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })
    private val defaultFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setup()
        setupAdapters()
        setupListener()
        handleOnBackPressed()
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
        mailboxSwitcher.setOnClickListener {
            mailboxExpandedSwitcher.apply {
                isVisible = !isVisible
                mailboxExpandButton.toggleChevron(!isVisible)
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
            context.openUrl(BuildConfig.FEEDBACK_USER_REPORT)
        }
        help.setOnClickListener {
            notYetImplemented()
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToHelpFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        importMails.setOnClickListener {
            closeDrawer()
            // TODO: Import mails
            notYetImplemented()
        }
        restoreMails.setOnClickListener {
            closeDrawer()
            // TODO: Restore mails
            notYetImplemented()
        }
    }

    private fun handleOnBackPressed() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            if (isDrawerOpen?.invoke() == true) {
                closeDrawer()
            } else {
                isEnabled = false
                activity?.onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        listenToCurrentMailbox()
        listenToMailboxes()
        listenToFolders()
    }

    override fun onPause() {
        currentMailboxJob?.cancel()
        mailboxesJob?.cancel()
        foldersJob?.cancel()
        super.onPause()
    }

    private fun listenToCurrentMailbox() {
        currentMailboxJob?.cancel()
        currentMailboxJob = lifecycleScope.launch {
            MailData.currentMailboxFlow.filterNotNull().collect { currentMailbox ->
                binding.mailboxSwitcherText.text = currentMailbox.email
                displayMailboxQuotas(currentMailbox)
            }
        }
    }

    private fun displayMailboxQuotas(mailbox: Mailbox) = with(binding) {
        storageLayout.isVisible = mailbox.isLimited

        if (mailbox.isLimited) {
            val usedSize = (mailbox.quotas?.size ?: 0).toLong()
            val maxSize = mailbox.quotas?.maxSize ?: 0L
            val formattedSize = FormatterFileSize.formatShortFileSize(context, usedSize)
            val formattedTotalSize = FormatterFileSize.formatShortFileSize(context, maxSize)

            storageText.text = context.resources.getString(R.string.menuDrawerMailboxStorage, formattedSize, formattedTotalSize)
            storageIndicator.progress = ceil(100.0f * usedSize.toFloat() / maxSize.toFloat()).toInt()
        }
    }

    private fun listenToMailboxes() {
        mailboxesJob?.cancel()
        mailboxesJob = lifecycleScope.launch {
            viewModel.uiMailboxesFlow.filterNotNull().collect { mailboxes ->
                val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }.sortMailboxes()
                addressAdapter.setMailboxes(sortedMailboxes)
            }
        }
    }

    private fun listenToFolders() {
        foldersJob?.cancel()
        foldersJob = lifecycleScope.launch {
            viewModel.uiFoldersFlow.filterNotNull().collect(::onFoldersChange)
        }
    }

    private fun closeDrawer() = with(binding) {
        closeDrawer?.invoke()
        closeDropdowns()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun closeDropdowns() = with(binding) {
        mailboxExpandedSwitcher.isGone = true
        mailboxExpandButton.rotation = 0.0f
        customFoldersList.isGone = true
        expandCustomFolderButton.rotation = 0.0f
    }

    private fun openFolder(@StringRes folderNameId: Int) {
        openFolder(getString(folderNameId))
    }

    private fun openFolder(folderName: String) = with(binding) {
        viewModel.openFolder(folderName, context)
        closeDrawer()
    }

    private fun onFoldersChange(folders: List<Folder>) {

        val (inbox, defaultFolders, customFolders) = getMenuFolders(folders)

        binding.inboxFolderBadge.text = inbox?.getUnreadCountOrNull()

        defaultFoldersAdapter.setFolders(defaultFolders)
        customFoldersAdapter.setFolders(customFolders)
    }

    private fun getMenuFolders(folders: List<Folder>): Folders {
        return folders.toMutableList().let { list ->

            val inbox = list.find { it.role == FolderRole.INBOX }?.also(list::remove)
            val defaultFolders = list.filter { it.role != null }.sortedBy { it.role?.order }.also(list::removeAll)
            val customFolders = list.sortedByDescending { it.isFavorite }

            Folders(inbox, defaultFolders, customFolders)
        }
    }

    private data class Folders(
        val inbox: Folder?,
        val defaultFolders: List<Folder>,
        val customFolders: List<Folder>,
    )
}
