/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
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
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.views.MenuDrawerItemView
import kotlinx.coroutines.launch

class MenuDrawerFragment : MenuFoldersFragment() {

    private lateinit var binding: FragmentMenuDrawerBinding

    override val inboxFolder: MenuDrawerItemView by lazy { binding.inboxFolder }
    override val defaultFoldersList: RecyclerView by lazy { binding.defaultFoldersList }
    override val customFoldersList: RecyclerView by lazy { binding.customFoldersList }

    var exitDrawer: (() -> Unit)? = null
    var isDrawerOpen: (() -> Boolean)? = null

    private var canNavigate = true

    private val addressAdapter = MenuDrawerSwitchUserMailboxesAdapter { selectedMailbox ->
        // TODO: This works, but... The splashscreen blinks.
        AccountUtils.currentMailboxId = selectedMailbox.mailboxId
        RealmDatabase.close()
        AccountUtils.reloadApp?.invoke()
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayVersion()

        observeCurrentMailbox()
        observeMailboxesLive()
        observeCurrentFolder()
        observeFoldersLive()
        observeQuotas()
    }

    override fun setupListeners() = with(binding) {
        super.setupListeners()

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
            mailboxSwitcherText.setTextAppearance(if (mailboxExpandedSwitcher.isVisible) R.style.BodyMedium_Accent else R.style.BodyMedium)
        }
        customFolders.setOnClickListener { customFoldersLayout.isGone = customFolders.isCollapsed }
        customFolders.setOnActionClickListener { // Create new folder
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToNewFolderDialog(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        feedback.setOnClickListener {
            closeDrawer()
            if (AccountUtils.currentUser?.isStaff == true) {
                Intent(context, BugTrackerActivity::class.java).apply {
                    putExtras(
                        BugTrackerActivityArgs(
                            user = AccountUtils.currentUser!!,
                            appBuildNumber = BuildConfig.VERSION_NAME,
                            bucketIdentifier = BuildConfig.BUGTRACKER_MAIL_BUCKET_ID,
                            projectName = BuildConfig.BUGTRACKER_MAIL_PROJECT_NAME,
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

    override fun setupAdapters() {
        super.setupAdapters()
        binding.addressesList.adapter = addressAdapter
    }

    override fun onFolderSelected(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }

    @SuppressLint("SetTextI18n")
    private fun displayVersion() {
        binding.appVersionName.text = "kMail Android version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
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

            // Make sure you always cancel all mailbox current notifications, whenever it is visible by the user.
            // Also cancel notifications from the current mailbox if it no longer exists.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    mainViewModel.dismissCurrentMailboxNotifications()
                }
            }
        }
    }

    private fun observeMailboxesLive() = with(binding) {
        mainViewModel.observeMailboxesLive().refreshObserve(viewLifecycleOwner) { mailboxes ->

            val sortedMailboxes = mailboxes.filterNot { it.mailboxId == AccountUtils.currentMailboxId }

            addressAdapter.setMailboxes(sortedMailboxes)

            val hasMoreThanOneMailbox = sortedMailboxes.isNotEmpty()

            mailboxSwitcher.apply {
                isClickable = hasMoreThanOneMailbox
                isFocusable = hasMoreThanOneMailbox
            }

            mailboxExpandButton.isVisible = hasMoreThanOneMailbox
        }
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observe(viewLifecycleOwner) { folder ->
            binding.inboxFolder.setSelectedState(folder.role == FolderRole.INBOX)

            defaultFoldersAdapter.updateSelectedState(folder.id)
            customFoldersAdapter.updateSelectedState(folder.id)
        }
    }

    private fun observeFoldersLive() = with(mainViewModel) {
        currentFoldersLiveToObserve.refreshObserve(viewLifecycleOwner) { (inbox, defaultFolders, customFolders) ->

            inboxFolderId = inbox?.id
            inbox?.unreadCount?.let { inboxFolder.badge = it }

            binding.noFolderText.isVisible = customFolders.isEmpty()

            val currentFolderId = currentFolderId.value ?: return@refreshObserve
            defaultFoldersAdapter.setFolders(defaultFolders, currentFolderId)
            customFoldersAdapter.setFolders(customFolders, currentFolderId)
        }
    }

    private fun observeQuotas() = with(binding) {
        mainViewModel.currentQuotasLiveToObserve.refreshObserve(viewLifecycleOwner) { quotas ->
            val isLimited = quotas != null

            storageLayout.isVisible = isLimited
            storageDivider.isVisible = isLimited

            if (isLimited) {
                storageText.text = quotas?.getText(context) ?: return@refreshObserve
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
        advancedActionsLayout.isGone = true
        advancedActions.setIsCollapsed(true)
    }
}
