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
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.ModelsUtils.formatFoldersListWithAllChildren

class MenuDrawerFragment : Fragment() {

    private lateinit var binding: FragmentMenuDrawerBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

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

        setupAdapters()
        setupListeners()

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

    private fun setupListeners() = with(binding) {
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
        swapAccount.setOnClickListener {
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToSwitchUserFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        inboxFolder.setOnClickListener { inboxFolderId?.let(::openFolder) }
        customFolders.setOnClickListener { customFoldersList.isGone = customFolders.isCollapsed }
        customFolders.setOnActionClickListener { // Create new folder
            // TODO
            notYetImplemented()
        }
        feedbacks.setOnClickListener {
            closeDrawer()
            if (AccountUtils.currentUser?.isStaff == true) {
                Intent(context, BugTrackerActivity::class.java).apply {
                    putExtras(
                        BugTrackerActivityArgs(
                            user = AccountUtils.currentUser!!,
                            appBuildNumber = BuildConfig.VERSION_NAME,
                            bucketIdentifier = BuildConfig.MAIL_BUCKET_ID,
                            projectName = BuildConfig.MAIL_PROJECT_NAME,
                        ).toBundle()
                    )
                    startActivity(this)
                }
            } else {
                context.openUrl(BuildConfig.FEEDBACK_USER_REPORT)
            }
        }
        help.setOnClickListener {
            notYetImplemented()
            closeDrawer()
            menuDrawerSafeNavigate(R.id.helpFragment)
        }
        advancedActions.setOnClickListener { advancedActionsLayout.isGone = advancedActions.isCollapsed }
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

    private fun menuDrawerSafeNavigate(destinationResId: Int) {
        if (canNavigate) {
            canNavigate = false
            findNavController().navigate(destinationResId)
        }
    }

    private fun observeMailboxes() = with(binding) {
        mainViewModel.observeMailboxes().observe(viewLifecycleOwner) { mailboxes ->
            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }
            addressAdapter.setMailboxes(sortedMailboxes)
            val isEmpty = sortedMailboxes.isEmpty()
            addressesList.isGone = isEmpty
            addressesListDivider.isGone = isEmpty
        }
    }

    private fun observeCurrentMailbox() {
        MainViewModel.currentMailboxObjectId.observeNotNull(viewLifecycleOwner) { objectId ->
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
        }
    }

    private fun observeCurrentFolder() {
        MainViewModel.currentFolderId.observeNotNull(viewLifecycleOwner) { folderId ->
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

            storageLayout.isVisible = isLimited
            storageDivider.isVisible = isLimited

            if (isLimited) {
                storageText.text = quotas?.getText(context) ?: return@observe
                storageIndicator.progress = quotas.getProgress()
            }
        }
    }

    fun onDrawerOpened() {
        canNavigate = true
        mainViewModel.forceRefreshMailboxes()
        trackScreen()
    }

    fun closeDrawer() {
        exitDrawer?.invoke()
        closeDropdowns()
    }

    fun closeDropdowns(): Unit = with(binding) {
        mailboxExpandedSwitcher.isGone = true
        mailboxExpandButton.rotation = ResourcesCompat.getFloat(resources, R.dimen.angleViewNotRotated)
        customFoldersList.isVisible = true
        customFolders.setIsCollapsed(false)
        advancedActionsLayout.isVisible = true
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
