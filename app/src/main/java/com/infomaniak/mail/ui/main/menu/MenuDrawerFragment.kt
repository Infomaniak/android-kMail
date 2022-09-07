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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren

class MenuDrawerFragment : Fragment() {

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var currentFolderRole: FolderRole? = null
    private var inboxFolderId: String? = null
    private var canNavigate = true

    private val addressAdapter = MenuDrawerSwitchUserMailboxesAdapter { selectedMailbox ->
        mainViewModel.openMailbox(selectedMailbox)
        closeDrawer()
    }

    private val defaultFolderAdapter = FolderAdapter(openFolder = { folderId -> openFolder(folderId) })
    private val customFolderAdapter = FolderAdapter(openFolder = { folderId -> openFolder(folderId) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AccountUtils.currentUser?.let(binding.userAvatar::loadAvatar)

        setupAdapters()
        setupListener()

        observeMailboxes()
        observeCurrentMailbox()
        observeFolders()
        observeCurrentFolder()
        observeQuotas()
    }

    private fun setupAdapters() = with(binding) {
        addressesList.adapter = addressAdapter
        defaultFoldersList.adapter = defaultFolderAdapter
        customFoldersList.adapter = customFolderAdapter
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

    private fun observeMailboxes() = with(binding) {
        mainViewModel.listenToMailboxes().observe(viewLifecycleOwner) { mailboxes ->
            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }.sortMailboxes()
            addressAdapter.setMailboxes(sortedMailboxes)
            if (sortedMailboxes.isEmpty()) {
                addressesList.isGone = true
                addressesListDivider.isGone = true
            }
        }
    }

    private fun observeCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(this) { objectId ->
            menuDrawerViewModel.currentMailboxObjectId.value = objectId
            mainViewModel.getMailbox(objectId).observeNotNull(viewLifecycleOwner) { mailbox ->
                binding.mailboxSwitcherText.text = mailbox.email
            }
        }
    }

    private fun observeFolders() {
        menuDrawerViewModel.folders.observe(viewLifecycleOwner) { folders ->
            val (inbox, defaultFolders, customFolders) = getMenuFolders(folders)

            inboxFolderId = inbox?.id
            binding.inboxFolder.badge = inbox?.getUnreadCountOrNull()

            val currentFolderId = MainViewModel.currentFolderId.value
            defaultFolderAdapter.setFolders(defaultFolders, currentFolderId)
            customFolderAdapter.setFolders(customFolders, currentFolderId)

            setCustomFoldersCollapsedState()
        }
    }

    private fun observeCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(this) { folderId ->
            mainViewModel.getFolder(folderId).observeNotNull(viewLifecycleOwner) { folder ->
                currentFolderRole = folder.role
                binding.inboxFolder.setSelectedState(currentFolderRole == FolderRole.INBOX)
                defaultFolderAdapter.notifyItemRangeChanged(0, defaultFolderAdapter.itemCount, Unit)
                customFolderAdapter.notifyItemRangeChanged(0, customFolderAdapter.itemCount, Unit)
            }
        }
    }

    private fun observeQuotas() = with(binding) {
        menuDrawerViewModel.quotas.observe(viewLifecycleOwner) { quotas ->
            val isLimited = quotas != null

            getMoreStorageCardview.isVisible = isLimited
            storageDivider.isVisible = isLimited

            if (isLimited) {
                storageText.text = quotas?.getText(context, MenuDrawerViewModel.QUOTAS_MAX_SIZE) ?: return@observe
                storageIndicator.progress = quotas.getProgress(MenuDrawerViewModel.QUOTAS_MAX_SIZE)
            }
        }
    }

    fun onDrawerOpened() {
        canNavigate = true
        mainViewModel.forceRefreshMailboxes()
    }

    private fun setCustomFoldersCollapsedState() = with(binding) {
        val folderId = MainViewModel.currentFolderId.value
        val isExpanded = folderId != null && (currentFolderRole == null || customFolderAdapter.itemCount == 0)
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
