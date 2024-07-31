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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.CreateFolderDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.LockedMailboxBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.InvalidPasswordFragmentArgs
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ConfettiUtils
import com.infomaniak.mail.utils.ConfettiUtils.ConfettiType.INFOMANIAK
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.launchSyncAutoConfigActivityForResult
import com.infomaniak.mail.utils.extensions.observeNotNull
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MenuDrawerFragment : Fragment() {

    private var _binding: FragmentMenuDrawerBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var createFolderDialog: CreateFolderDialog

    @Inject
    lateinit var menuDrawerAdapter: MenuDrawerAdapter

    private val currentClassName: String = MenuDrawerFragment::class.java.name

    var exitDrawer: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindAlertToViewLifecycle(createFolderDialog)

        setupListeners()
        setupCreateFolderDialog()
        setupRecyclerView()

        observeMenuDrawerData()
        observeCurrentFolder()
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
        createFolderDialog.setCallbacks(onPositiveButtonClicked = mainViewModel::createNewFolder)
    }

    private fun setupRecyclerView() {
        binding.menuDrawerRecyclerView.adapter = menuDrawerAdapter(
            callbacks = object : MenuDrawerAdapterCallbacks {
                override var onMailboxesHeaderClicked: () -> Unit = ::onMailboxesHeaderClicked
                override var onValidMailboxClicked: (Int) -> Unit = ::onValidMailboxClicked
                override var onLockedMailboxClicked: (String) -> Unit = ::onLockedMailboxClicked
                override var onInvalidPasswordMailboxClicked: (Mailbox) -> Unit = ::onInvalidPasswordMailboxClicked
                override var onFolderClicked: (folderId: String) -> Unit = ::onFolderSelected
                override var onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit = ::onFolderCollapsed
                override var onCustomFoldersHeaderClicked: (Boolean) -> Unit = ::onCustomFoldersHeaderClicked
                override var onCreateFolderClicked: () -> Unit = ::onCreateFolderClicked
                override var onSyncAutoConfigClicked: () -> Unit = ::onSyncAutoConfigClicked
                override var onImportMailsClicked: () -> Unit = ::onImportMailsClicked
                override var onRestoreMailsClicked: () -> Unit = ::onRestoreMailsClicked
                override var onFeedbackClicked: () -> Unit = ::onFeedbackClicked
                override var onHelpClicked: () -> Unit = ::onHelpClicked
                override var onAppVersionClicked: () -> Unit = ::onAppVersionClicked
            },
        )
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
        val isExpanded = !areMailboxesExpanded.value!!
        trackMenuDrawerEvent("mailboxes", isExpanded)
        areMailboxesExpanded.value = isExpanded
    }

    private fun onValidMailboxClicked(mailboxId: Int) {
        lifecycleScope.launch { AccountUtils.switchToMailbox(mailboxId) }
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
        menuDrawerViewModel.areCustomFoldersExpanded.value = !isCollapsed
    }

    private fun onCreateFolderClicked() {
        trackCreateFolderEvent("fromMenuDrawer")
        createFolderDialog.show()
    }

    private fun onFolderSelected(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }

    private fun onFolderCollapsed(folderId: String, shouldCollapse: Boolean) {
        menuDrawerViewModel.toggleFolderCollapsingState(folderId, shouldCollapse)
    }

    private fun onSyncAutoConfigClicked() {
        trackSyncAutoConfigEvent("openFromMenuDrawer")
        launchSyncAutoConfigActivityForResult()
        closeDrawer()
    }

    private fun onImportMailsClicked() {
        trackMenuDrawerEvent("importEmails")
        context?.openUrl(BuildConfig.IMPORT_EMAILS_URL)
        closeDrawer()
    }

    private fun onRestoreMailsClicked() {
        trackMenuDrawerEvent("restoreEmails")
        safeNavigate(R.id.restoreEmailsBottomSheetDialog, currentClassName = currentClassName)
    }

    private fun onFeedbackClicked() {

        if (AccountUtils.currentUser?.isStaff == true) {
            Intent(requireContext(), BugTrackerActivity::class.java).apply {
                putExtras(
                    BugTrackerActivityArgs(
                        user = AccountUtils.currentUser!!,
                        appBuildNumber = BuildConfig.VERSION_NAME,
                        bucketIdentifier = BuildConfig.BUGTRACKER_MAIL_BUCKET_ID,
                        projectName = BuildConfig.BUGTRACKER_MAIL_PROJECT_NAME,
                        repoGitHub = BuildConfig.GITHUB_REPO,
                    ).toBundle(),
                )
            }.also(::startActivity)
        } else {
            trackMenuDrawerEvent("feedback")
            context?.openUrl(requireContext().getString(R.string.urlUserReportAndroid))
        }

        closeDrawer()
    }

    private fun onHelpClicked() {
        ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SUPPORT.id)
        trackMenuDrawerEvent("help")
        context?.openUrl(BuildConfig.CHATBOT_URL)
        closeDrawer()
    }

    private fun onAppVersionClicked() {
        ConfettiUtils.onEasterEggConfettiClicked(
            container = (activity as? MainActivity)?.getConfettiContainer(),
            type = INFOMANIAK,
            matomoValue = "MenuDrawer",
        )
    }

    private fun observeMenuDrawerData() = with(mainViewModel) {

        Utils.waitInitMediator(
            mailboxesLive,
            menuDrawerViewModel.areMailboxesExpanded,
            currentFoldersLive,
            menuDrawerViewModel.areCustomFoldersExpanded,
            currentPermissionsLive,
            currentQuotasLive,
            constructor = {
                @Suppress("UNCHECKED_CAST")
                MediatorContainer(
                    it[0] as List<Mailbox>,
                    it[1] as Boolean,
                    it[2] as List<Folder>,
                    it[3] as Boolean,
                    it[4] as MailboxPermissions?,
                    it[5] as Quotas?,
                )
            }
        )
            .asFlow()
            .map(menuDrawerAdapter::formatList)
            .flowOn(Dispatchers.IO)
            .asLiveData()
            .observe(viewLifecycleOwner, menuDrawerAdapter::submitList)
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observeNotNull(viewLifecycleOwner, menuDrawerAdapter::notifySelectedFolder)
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
        mainViewModel.newFolderResultTrigger.observe(viewLifecycleOwner) { createFolderDialog.resetLoadingAndDismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class MediatorContainer(
        val mailboxes: List<Mailbox>,
        val areMailboxesExpanded: Boolean,
        val allFolders: List<Folder>,
        val areCustomFoldersExpanded: Boolean,
        val permissions: MailboxPermissions?,
        val quotas: Quotas?,
    )
}
