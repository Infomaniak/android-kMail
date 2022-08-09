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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.menu.user.MenuDrawerSwitchUserMailboxesAdapter
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlin.math.ceil

class MenuDrawerFragment : Fragment() {

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private val viewModel: MenuDrawerViewModel by viewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private val inboxFolderId: String? by lazy { MailData.foldersFlow.value?.find { it.role == FolderRole.INBOX }?.id }
    private var canNavigate = true

    private val addressAdapter = MenuDrawerSwitchUserMailboxesAdapter() { selectedMailbox ->
        viewModel.switchToMailbox(selectedMailbox)
        // TODO: This is not enough. It won't refresh the MenuDrawer data (ex: unread counts)
        closeDrawer()
    }

    private val defaultFoldersAdapter = FoldersAdapter(openFolder = { folderId -> openFolder(folderId) })
    private val customFoldersAdapter = FoldersAdapter(openFolder = { folderId -> openFolder(folderId) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AccountUtils.currentUser?.let(binding.userAvatarImage::loadAvatar)
        setupAdapters()
        setupListener()
        listenToCurrentMailbox()
        listenToMailboxes()
        listenToFolders()
        listenToCurrentFolder()
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
        inboxFolder.setOnClickListener { inboxFolderId?.let { openFolder(it) } }
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
            menuDrawerSafeNavigate(R.id.helpFragment)
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
        getMoreStorageCardview.setOnClickListener { menuDrawerSafeNavigate(R.id.getMoreStorageBottomSheetDialog) }
    }

    private fun menuDrawerSafeNavigate(destinationResId: Int) {
        if (canNavigate) {
            canNavigate = false
            findNavController().navigate(destinationResId)
        }
    }


    fun onDrawerOpened() {
        canNavigate = true
    }

    private fun listenToCurrentMailbox() {
        viewModel.currentMailbox.observeNotNull(this) { currentMailbox ->
            binding.mailboxSwitcherText.text = currentMailbox.email
            displayMailboxQuotas(currentMailbox)
        }
        viewModel.listenToCurrentMailbox()
    }

    private fun displayMailboxQuotas(mailbox: Mailbox) = with(binding) {
        getMoreStorageCardview.isVisible = mailbox.isLimited
        storageDivider.isVisible = mailbox.isLimited

        if (mailbox.isLimited) {
            val usedSize = (mailbox.quotas?.size ?: 0).toLong()
            val maxSize = mailbox.quotas?.maxSize ?: 0L
            val formattedSize = FormatterFileSize.formatShortFileSize(context, usedSize)
            val formattedTotalSize = FormatterFileSize.formatShortFileSize(context, maxSize)

            storageText.text = context.resources.getString(R.string.menuDrawerMailboxStorage, formattedSize, formattedTotalSize)
            storageIndicator.progress = ceil(100.0f * usedSize.toFloat() / maxSize.toFloat()).toInt()
        }
    }

    private fun listenToMailboxes() = with(binding) {
        viewModel.mailboxes.observeNotNull(this@MenuDrawerFragment) { mailboxes ->
            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }.sortMailboxes()
            addressAdapter.setMailboxes(sortedMailboxes)
            if (sortedMailboxes.isEmpty()) {
                addressesList.isGone = true
                addressesListDivider.isGone = true
            }
        }
        viewModel.listenToMailboxes()
    }

    private fun listenToFolders() {
        viewModel.folders.observeNotNull(this, ::onFoldersChange)
        viewModel.listenToFolders()
    }

    private fun listenToCurrentFolder() = with(binding) {
        viewModel.currentFolder.observeNotNull(this@MenuDrawerFragment, ::onCurrentFolderChange)
        viewModel.listenToCurrentFolder()
    }

    private fun onCurrentFolderChange(currentFolder: Folder) = updateSelectedItemUi(currentFolder)

    private fun updateSelectedItemUi(currentFolder: Folder) = with(binding) {
        inboxFolder.setSelectedState(currentFolder.id == inboxFolderId)
        defaultFoldersAdapter.notifyItemRangeChanged(0, defaultFoldersAdapter.itemCount, Unit)
        customFoldersAdapter.notifyItemRangeChanged(0, customFoldersAdapter.itemCount, Unit)
    }

    private fun setCustomFolderCollapsedState() = with(binding) {
        val currentFolder = MailData.currentFolderFlow.value
        val isExpanded = currentFolder != null && (currentFolder.role == null || customFoldersAdapter.itemCount == 0)
        val angleResource = if (isExpanded) R.dimen.angleViewRotated else R.dimen.angleViewNotRotated
        val angle = ResourcesCompat.getFloat(resources, angleResource)
        customFoldersList.isVisible = isExpanded
        createNewFolderButton.isVisible = isExpanded
        expandCustomFolderButton.rotation = angle
    }

    fun closeDrawer() = with(binding) {
        exitDrawer?.invoke()
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

    private fun openFolder(folderId: String) {
        viewModel.openFolder(folderId)
        closeDrawer()
    }

    private fun onFoldersChange(folders: List<Folder>) {

        val (inbox, defaultFolders, customFolders) = getMenuFolders(folders)

        binding.inboxFolder.badge = inbox?.getUnreadCountOrNull()

        defaultFoldersAdapter.setFolders(defaultFolders)
        customFoldersAdapter.setFolders(customFolders)

        setCustomFolderCollapsedState()
    }

    private fun getMenuFolders(folders: List<Folder>): Triple<Folder?, List<Folder>, List<Folder>> {
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

            Triple(inbox, defaultFolders, customFolders)
        }
    }
}
