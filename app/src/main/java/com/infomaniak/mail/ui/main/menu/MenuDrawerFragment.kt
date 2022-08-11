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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxInfos.MailboxController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.LoginActivity
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.menu.user.MenuDrawerSwitchUserMailboxesAdapter
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class MenuDrawerFragment : Fragment() {

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var foldersJob: Job? = null

    private var currentFolderRole: FolderRole? = null
    private var inboxFolderId: String? = null
    private var canNavigate = true

    private val addressAdapter = MenuDrawerSwitchUserMailboxesAdapter { selectedMailbox ->
        mainViewModel.openMailbox(selectedMailbox)
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

        listenToMailboxes()
        listenToCurrentMailbox()
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
        inboxFolder.setOnClickListener { inboxFolderId?.let(::openFolder) }
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
        mainViewModel.forceRefreshMailboxes()
    }

    private fun listenToMailboxes() = lifecycleScope.launch(Dispatchers.IO) {
        MailboxController.getMailboxesAsync(AccountUtils.currentUserId).collect {
            withContext(Dispatchers.Main) { onMailboxesChange(it.list) }
        }
    }

    private fun listenToCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(this) { mailboxObjectId ->
            listenToFolders()
            onCurrentMailboxChange(mailboxObjectId)
        }
    }

    private fun listenToFolders() {
        foldersJob?.cancel()
        foldersJob = lifecycleScope.launch(Dispatchers.IO) {
            FolderController.getFoldersAsync().collect {
                withContext(Dispatchers.Main) { onFoldersChange(it.list) }
            }
        }
    }

    private fun listenToCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(this) { folderId ->
            lifecycleScope.launch(Dispatchers.IO) {
                FolderController.getFolderAsync(folderId).firstOrNull()?.obj?.role?.let { folderRole ->
                    withContext(Dispatchers.Main) { onCurrentFolderChange(folderRole) }
                }
            }
        }
    }

    override fun onDestroyView() {
        MainViewModel.currentMailboxObjectId.removeObservers(this)
        MainViewModel.currentFolderId.removeObservers(this)
        super.onDestroyView()
    }

    private fun onMailboxesChange(mailboxes: List<Mailbox>) = with(binding) {
        val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }.sortMailboxes()
        addressAdapter.setMailboxes(sortedMailboxes)
        if (sortedMailboxes.isEmpty()) {
            addressesList.isGone = true
            addressesListDivider.isGone = true
        }
    }

    private fun onCurrentMailboxChange(mailboxObjectId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            MailboxController.getMailboxAsync(mailboxObjectId).firstOrNull()?.obj?.let { mailbox ->
                withContext(Dispatchers.Main) {
                    binding.mailboxSwitcherText.text = mailbox.email
                    displayMailboxQuotas(mailbox)
                }
            }
        }
    }

    private fun onFoldersChange(folders: List<Folder>) {
        val (inbox, defaultFolders, customFolders) = getMenuFolders(folders)

        inboxFolderId = inbox?.id
        binding.inboxFolder.badge = inbox?.getUnreadCountOrNull()

        val currentFolderId = MainViewModel.currentFolderId.value
        defaultFoldersAdapter.setFolders(defaultFolders, currentFolderId)
        customFoldersAdapter.setFolders(customFolders, currentFolderId)

        setCustomFoldersCollapsedState()
    }

    private fun onCurrentFolderChange(folderRole: FolderRole) = with(binding) {
        currentFolderRole = folderRole
        inboxFolder.setSelectedState(currentFolderRole == FolderRole.INBOX)
        defaultFoldersAdapter.notifyItemRangeChanged(0, defaultFoldersAdapter.itemCount, Unit)
        customFoldersAdapter.notifyItemRangeChanged(0, customFoldersAdapter.itemCount, Unit)
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

    private fun setCustomFoldersCollapsedState() = with(binding) {
        val folderId = MainViewModel.currentFolderId.value
        val isExpanded = folderId != null && (currentFolderRole == null || customFoldersAdapter.itemCount == 0)
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
        setCustomFoldersCollapsedState()
    }

    private fun openFolder(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
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
