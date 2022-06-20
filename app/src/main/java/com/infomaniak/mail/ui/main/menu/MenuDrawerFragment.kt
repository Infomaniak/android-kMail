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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.main.thread.ThreadListViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.toggleChevron
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MenuDrawerFragment(private val closeDrawer: () -> Unit) : Fragment() {

    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()
    private val threadListViewModel: ThreadListViewModel by activityViewModels()

    private lateinit var binding: FragmentMenuDrawerBinding

    private var mailboxesJob: Job? = null

    private val addressAdapter = SettingAddressAdapter(displayIcon = false) {
        threadListViewModel.loadMailData()
        closeDrawer.invoke()
        onMailboxChange()
    }

    private val customFoldersAdapter = CustomFoldersAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentMenuDrawerBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        menuDrawerViewModel.setup()
        binding.recyclerViewAddress.adapter = addressAdapter
        binding.customFoldersList.adapter = customFoldersAdapter
        setupListener()
        setupUi()
    }

    private fun setupUi() = with(binding) {
        accountSwitcherText.text = AccountUtils.currentUser?.email
        setCommercialFolderVisibility()
        listenToMailboxes()
    }

    private fun setCommercialFolderVisibility() = with(binding) {
        val mustDisplayFolders = MailData.currentMailboxFlow.value?.hasSocialAndCommercialFiltering ?: false
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

    private fun listenToMailboxes() {
        with(menuDrawerViewModel) {

            if (mailboxesJob != null) mailboxesJob?.cancel()

            mailboxesJob = viewModelScope.launch(Dispatchers.Main) {
                uiMailboxesFlow.filterNotNull().collect { mailboxes ->
                    addressAdapter.setMailboxes(mailboxes)
                    addressAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupListener() = with(binding) {
        accountSwitcher.setOnClickListener {
            recyclerViewAddress.apply {
                isVisible = !isVisible
                expandAccountButton.toggleChevron(!isVisible)
            }
        }
        customFolders.setOnClickListener {
            customFoldersList.apply {
                isVisible = !isVisible
                expandCustomFolderButton.toggleChevron(!isVisible)
            }
        }
    }

    private fun onMailboxChange() {
        setCommercialFolderVisibility()

        // TODO: Change custom folders dataset
    }
}
