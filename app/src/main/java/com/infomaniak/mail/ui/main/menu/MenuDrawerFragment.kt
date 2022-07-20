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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter
import com.infomaniak.mail.ui.main.menu.user.SwitchUserMailboxesAdapter.Companion.sortMailboxes
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlin.math.ceil

class MenuDrawerFragment : Fragment() {

    var closeDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private val viewModel: MenuDrawerViewModel by viewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private val addressAdapter = SwitchUserMailboxesAdapter(displayIcon = false) { selectedMailbox ->
        viewModel.switchToMailbox(selectedMailbox)
        // TODO: This is not enough. It won't refresh the MenuDrawer data (ex: unread counts)
        closeDrawer()
    }

    private val defaultFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })
    private val customFoldersAdapter = FoldersAdapter(openFolder = { folderName -> openFolder(folderName) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupListener()
        handleOnBackPressed()
        listenToCurrentMailbox()
        listenToMailboxes()
        listenToFolders()
    }

    private fun setupAdapters() = with(binding) {
        addressesList.adapter = addressAdapter
        defaultFoldersList.adapter = defaultFoldersAdapter
        customFoldersList.adapter = customFoldersAdapter
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
            createNewFolderButton.apply {
                isVisible = !isVisible
            }
        }
        createNewFolderButton.setOnClickListener {
            // TODO
            notYetImplemented()
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

    private fun listenToCurrentMailbox() {
        viewModel.currentMailbox.observeNotNull(this) { currentMailbox ->
            binding.mailboxSwitcherText.text = currentMailbox.email
            displayMailboxQuotas(currentMailbox)
        }
        viewModel.listenToCurrentMailbox()
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
        viewModel.mailboxes.observeNotNull(this) { mailboxes ->
            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }.sortMailboxes()
            addressAdapter.setMailboxes(sortedMailboxes)
        }
        viewModel.listenToMailboxes()
    }

    private fun listenToFolders() {
        viewModel.folders.observeNotNull(this, ::onFoldersChange)
        viewModel.listenToFolders()
    }

    private fun setCustomFolderCollapsedState() = with(binding) {
        val isCollapsed = customFoldersAdapter.itemCount > 0
        val angleResource = if (isCollapsed) R.dimen.angleViewNotRotated else R.dimen.angleViewRotated
        val angle = ResourcesCompat.getFloat(resources, angleResource)
        customFoldersList.isGone = isCollapsed
        createNewFolderButton.isGone = isCollapsed
        expandCustomFolderButton.rotation = angle
    }

    private fun closeDrawer() = with(binding) {
        closeDrawer?.invoke()
        closeDropdowns()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun closeDropdowns(): Unit = with(binding) {
        mailboxExpandedSwitcher.isGone = true
        mailboxExpandButton.rotation = 0.0f
        customFoldersList.isGone = true
        createNewFolderButton.isGone = true
        expandCustomFolderButton.rotation = 0.0f
        setCustomFolderCollapsedState()
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

        setCustomFolderCollapsedState()
    }

    private fun getMenuFolders(folders: List<Folder>): Folders {
        return folders.toMutableList().let { list ->

            val inbox = list
                .find { it.role == FolderRole.INBOX }
                ?.also(list::remove)

            val defaultFolders = list
                .filter { it.role != null }
                .sortedBy { it.role?.order }
                .also(list::removeAll)

            val customFolders = list
                .filter { it.parentLink == null }
                .sortedByDescending { it.isFavorite }
                .formatFoldersListWithAllChildren()

            Folders(inbox, defaultFolders, customFolders)
        }
    }

    private data class Folders(
        val inbox: Folder?,
        val defaultFolders: List<Folder>,
        val customFolders: List<Folder>,
    )
}
