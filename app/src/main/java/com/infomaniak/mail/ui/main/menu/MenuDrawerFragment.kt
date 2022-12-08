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

import android.annotation.SuppressLint
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
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.toggleChevron

class MenuDrawerFragment : Fragment() {

    private lateinit var binding: FragmentMenuDrawerBinding
    private val mainViewModel: MainViewModel by activityViewModels()

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private var inboxFolderId: String? = null
    private var canNavigate = true

    private val addressAdapter = MenuDrawerSwitchUserMailboxesAdapter { selectedMailbox ->
        // TODO: This works, but... The splashscreen blinks.
        AccountUtils.currentMailboxId = selectedMailbox.mailboxId
        RealmDatabase.close()
        AccountUtils.reloadApp?.invoke()
    }

    private val defaultFolderAdapter = FolderAdapter(openFolder = { folderId -> openFolder(folderId) })
    private val customFolderAdapter = FolderAdapter(openFolder = { folderId -> openFolder(folderId) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayVersion()

        setupAdapters()
        setupListeners()

        observeCurrentMailbox()
        observeMailboxesLive()
        observeCurrentFolder()
        observeFoldersLive()
        observeQuotas()
    }

    @SuppressLint("SetTextI18n")
    private fun displayVersion() {
        binding.appVersionName.text = "kMail Android version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
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
        customFolders.setOnClickListener { customFoldersLayout.isGone = customFolders.isCollapsed }
        customFolders.setOnActionClickListener { // Create new folder
            // TODO
            notYetImplemented()
        }
        feedback.setOnClickListener {
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

    private fun observeCurrentMailbox() {
        mainViewModel.currentMailbox.observe(viewLifecycleOwner) { mailbox ->
            binding.mailboxSwitcherText.text = mailbox.email
        }
    }

    private fun observeMailboxesLive() = with(binding) {
        mainViewModel.observeMailboxesLive().observe(viewLifecycleOwner) { mailboxes ->
            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }
            addressAdapter.setMailboxes(sortedMailboxes)
            val isEmpty = sortedMailboxes.isEmpty()
            addressesList.isGone = isEmpty
            addressesListDivider.isGone = isEmpty
        }
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->
            binding.inboxFolder.setSelectedState(folder.role == FolderRole.INBOX)

            defaultFolderAdapter.updateSelectedState(folder.id)
            customFolderAdapter.updateSelectedState(folder.id)
        }
    }

    private fun observeFoldersLive() {
        mainViewModel.currentFoldersLive.observe(viewLifecycleOwner) { (inbox, defaultFolders, customFolders) ->

            inboxFolderId = inbox?.id
            inbox?.unreadCount?.let { binding.inboxFolder.badge = it }

            binding.noFolderText.isVisible = customFolders.isEmpty()

            val currentFolder = mainViewModel.currentFolder.value ?: return@observe
            defaultFolderAdapter.setFolders(defaultFolders, currentFolder.id)
            customFolderAdapter.setFolders(customFolders, currentFolder.id)
        }
    }

    private fun observeQuotas() = with(binding) {
        mainViewModel.currentQuotasLive.observe(viewLifecycleOwner) { quotas ->
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
        customFoldersLayout.isVisible = true
        customFolders.setIsCollapsed(false)
        advancedActionsLayout.isVisible = true
    }

    private fun openFolder(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }
}
