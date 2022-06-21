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
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.main.thread.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.thread.ThreadListViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toggleChevron
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MenuDrawerFragment(private val closeDrawer: (() -> Unit)? = null) : Fragment() {

    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()
    private val threadListViewModel: ThreadListViewModel by activityViewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var foldersJob: Job? = null
    private var mailboxesJob: Job? = null

    private val addressAdapter = SettingAddressAdapter(displayIcon = false) {
        threadListViewModel.loadMailData()
        threadListViewModel.setup()
        closeDrawer()
    }

    private val customFoldersAdapter = CustomFoldersAdapter(openFolder = { folderName -> openFolder(folderName) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        menuDrawerViewModel.setup()
        setupAdapters()
        setupListener()
        setupUi()
    }

    private fun setupAdapters() = with(binding) {
        recyclerViewAddress.adapter = addressAdapter
        customFoldersList.adapter = customFoldersAdapter
    }

    private fun setupListener() = with(binding) {
        settingsButton.setOnClickListener {
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
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToManageMailAddressFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        addAccount.setOnClickListener {
            // TODO: go back to login activity directly ?
        }
        inboxFolder.setOnClickListener { openFolder(R.string.inboxFolder) }
        sentFolder.setOnClickListener { openFolder(R.string.sentFolder) }
        draftFolder.setOnClickListener { openFolder(R.string.draftFolder) }
        spamFolder.setOnClickListener { openFolder(R.string.spamFolder) }
        trashFolder.setOnClickListener { openFolder(R.string.trashFolder) }
        archiveFolder.setOnClickListener { openFolder(R.string.archiveFolder) }
        customFolders.setOnClickListener {
            customFoldersList.apply {
                isVisible = !isVisible
                expandCustomFolderButton.toggleChevron(!isVisible)
            }
        }
        feedbacks.setOnClickListener {
            // TODO: go to feedbacks
        }
        help.setOnClickListener {
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToHelpFragment(),
                currentClassName = MenuDrawerFragment::class.java.name,
            )
        }
        importMails.setOnClickListener {
            // TODO: import mails
        }
        restoreMails.setOnClickListener {
            // TODO: restore mails
        }
    }

    private fun setupUi() = with(binding) {
        accountSwitcherText.text = AccountUtils.currentUser?.email
        listenToMailboxes()
        listenToFolders()
    }

    private fun setCommercialFolderVisibility() = with(binding) {
        val mustDisplayFolders = MailData.currentMailboxFlow.value?.hasSocialAndCommercialFiltering ?: false
        if (mustDisplayFolders) {
            commercialFolder.setOnClickListener { openFolder(R.string.commercialFolder) }
            socialNetworksFolder.setOnClickListener { openFolder(R.string.socialNetworksFolder) }
        } else {
            commercialFolder.setOnClickListener(null)
            socialNetworksFolder.setOnClickListener(null)
        }
        commercialFolder.isVisible = mustDisplayFolders
        socialNetworksFolder.isVisible = mustDisplayFolders
    }

    override fun onResume() {
        super.onResume()
        listenToMailboxes()
    }

    override fun onPause() {
        mailboxesJob?.cancel()
        mailboxesJob = null

        super.onPause()
    }

    private fun listenToMailboxes() = with(menuDrawerViewModel) {
        if (mailboxesJob != null) mailboxesJob?.cancel()

        mailboxesJob = viewModelScope.launch(Dispatchers.Main) {
            uiMailboxesFlow.filterNotNull().collect { mailboxes ->
                addressAdapter.setMailboxes(mailboxes)
                addressAdapter.notifyDataSetChanged()
                setCommercialFolderVisibility()
            }
        }
    }

    private fun listenToFolders() = with(menuDrawerViewModel) {
        if (foldersJob != null) foldersJob?.cancel()

        foldersJob = viewModelScope.launch(Dispatchers.Main) {
            uiFoldersFlow.filterNotNull().collect { folders ->
                // TODO : Manage embedded folders ?
                val customFolders = folders.filter { it.role == null }.sortedByDescending { it.isFavorite }
                customFoldersAdapter.setCustomFolders(customFolders)
                customFoldersAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun closeDrawer() = with(binding) {
        closeDrawer?.invoke()
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
}
