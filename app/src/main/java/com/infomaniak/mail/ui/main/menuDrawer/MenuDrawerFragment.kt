/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menuDrawer

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.Fade.IN
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.InputAlertDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.LockedMailboxBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.InvalidPasswordFragmentArgs
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.checkForFolderCreationErrors
import com.infomaniak.mail.utils.extensions.observeNotNull
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MenuDrawerFragment : Fragment() {

    private var _binding: FragmentMenuDrawerBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var folderController: FolderController

    @Inject
    lateinit var inputDialog: InputAlertDialog

    @Inject
    lateinit var menuDrawerAdapter: MenuDrawerAdapter

    private val currentClassName: String = MenuDrawerFragment::class.java.name

    var exitDrawer: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindAlertToViewLifecycle(inputDialog)

        setupListeners()
        setupCreateFolderDialog()
        setupRecyclerView()

        observeAllTheThingsSheSaid()
        observeCurrentMailbox()
        observeNewFolderCreation()
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener {
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment(),
                currentClassName = currentClassName,
            )
        }
    }

    private fun setupCreateFolderDialog() {
        inputDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                trackCreateFolderEvent("confirm")
                mainViewModel.createNewFolder(folderName)
            },
            onErrorCheck = { folderName ->
                requireContext().checkForFolderCreationErrors(folderName, folderController)
            },
        )
    }

    private fun setupRecyclerView() {
        binding.menuDrawerRecyclerView.adapter = menuDrawerAdapter(
            currentClassName = currentClassName,
            confettiContainer = (activity as? MainActivity)?.getConfettiContainer(),
            onAskingTransition = ::executeMenuDrawerTransition,
            onAskingToCloseDrawer = ::closeDrawer,
            onMailboxesHeaderClicked = ::onMailboxesHeaderClicked,
            onValidMailboxClicked = ::onValidMailboxClicked,
            onInvalidPasswordMailboxClicked = ::onInvalidPasswordMailboxClicked,
            onLockedMailboxClicked = ::onLockedMailboxClicked,
            onCustomFoldersHeaderClicked = ::onCustomFoldersHeaderClicked,
            onCreateFolderClicked = ::onCreateFolderClicked,
            onFolderClicked = ::onFolderSelected,
            onCollapseChildrenClicked = ::onFolderCollapsed,
        )
    }

    private fun executeMenuDrawerTransition() {
        val transition = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(Fade(IN))
            .setDuration(MENU_DRAWER_TRANSITION_DURATION)

        TransitionManager.beginDelayedTransition(binding.menuDrawerRecyclerView, transition)
    }

    fun onDrawerOpened() {
        trackScreen()
    }

    fun closeDrawer() {
        exitDrawer?.invoke()
        closeDropdowns()
    }

    fun closeDropdowns() {
        menuDrawerViewModel.areMailboxesExpanded.value = false
    }

    private fun onMailboxesHeaderClicked() = with(menuDrawerViewModel) {
        val isExpanded = !(areMailboxesExpanded.value ?: false)
        trackMenuDrawerEvent("mailboxes", isExpanded)
        executeMenuDrawerTransition()
        areMailboxesExpanded.value = isExpanded
    }

    private fun onValidMailboxClicked(mailboxId: Int) = lifecycleScope.launch {
        AccountUtils.switchToMailbox(mailboxId)
    }

    private fun onInvalidPasswordMailboxClicked(mailbox: Mailbox) {
        safeNavigate(
            resId = R.id.invalidPasswordFragment,
            args = InvalidPasswordFragmentArgs(mailbox.mailboxId, mailbox.objectId, mailbox.email).toBundle(),
            currentClassName = currentClassName,
        )
    }

    private fun onLockedMailboxClicked(mailboxEmail: String) {
        safeNavigate(
            resId = R.id.lockedMailboxBottomSheetDialog,
            args = LockedMailboxBottomSheetDialogArgs(mailboxEmail).toBundle(),
            currentClassName = currentClassName,
        )
    }

    private fun onCustomFoldersHeaderClicked(isCollapsed: Boolean) {
        trackMenuDrawerEvent("customFolders", !isCollapsed)
        executeMenuDrawerTransition()
        menuDrawerViewModel.areCustomFoldersExpanded.value = !isCollapsed
    }

    private fun onCreateFolderClicked() {
        trackCreateFolderEvent("fromMenuDrawer")
        inputDialog.show(R.string.newFolderDialogTitle, R.string.newFolderDialogHint, R.string.buttonCreate)
    }

    private fun onFolderSelected(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }

    private fun onFolderCollapsed(folderId: String, shouldCollapse: Boolean) {
        menuDrawerViewModel.toggleFolderCollapsingState(folderId, shouldCollapse)
    }

    private fun observeAllTheThingsSheSaid() = with(mainViewModel) {

        Utils.waitInitMediator(
            currentMailbox,
            menuDrawerViewModel.areMailboxesExpanded,
            menuDrawerViewModel.otherMailboxesLive,
            currentFolder,
            currentDefaultFoldersLive,
            menuDrawerViewModel.areCustomFoldersExpanded,
            currentCustomFoldersLive,
            currentPermissionsLive,
            currentQuotasLive,
            constructor = {
                @Suppress("UNCHECKED_CAST")
                MediatorContainer(
                    it[0] as Mailbox?,
                    it[1] as Boolean,
                    it[2] as List<Mailbox>,
                    it[3] as Folder?,
                    it[4] as List<Folder>,
                    it[5] as Boolean,
                    it[6] as List<Folder>,
                    it[7] as MailboxPermissions?,
                    it[8] as Quotas?,
                )
            }
        ).observe(viewLifecycleOwner, menuDrawerAdapter::setItems)
    }

    private fun observeCurrentMailbox() {
        mainViewModel.currentMailbox.observeNotNull(viewLifecycleOwner) {
            // Make sure you always cancel all Mailbox current notifications, whenever it is visible by the user.
            // Also cancel notifications from the current mailbox if it no longer exists.
            lifecycleScope.launch {
                repeatOnLifecycle(State.STARTED) {
                    mainViewModel.dismissCurrentMailboxNotifications()
                }
            }
        }
    }

    private fun observeNewFolderCreation() {
        mainViewModel.newFolderResultTrigger.observe(viewLifecycleOwner) { inputDialog.resetLoadingAndDismiss() }
    }

    override fun onDestroyView() {
        TransitionManager.endTransitions(binding.menuDrawerRecyclerView)
        super.onDestroyView()
        _binding = null
    }

    data class MediatorContainer(
        val currentMailbox: Mailbox?,
        val areMailboxesExpanded: Boolean,
        val otherMailboxes: List<Mailbox>,
        val currentFolder: Folder?,
        val defaultFolders: List<Folder>,
        val areCustomFoldersExpanded: Boolean,
        val customFolders: List<Folder>,
        val permissions: MailboxPermissions?,
        val quotas: Quotas?,
    )

    companion object {
        private const val MENU_DRAWER_TRANSITION_DURATION = 250L
    }
}
