/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.core.bugtracker.BugTrackerActivity
import com.infomaniak.core.bugtracker.BugTrackerActivityArgs
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.CHATBOT_URL
import com.infomaniak.mail.IMPORT_EMAILS_URL
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackManageFolderEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.alertDialogs.ConfirmDeleteFolderDialog
import com.infomaniak.mail.ui.alertDialogs.CreateFolderDialog
import com.infomaniak.mail.ui.alertDialogs.ModifyNameFolderDialog
import com.infomaniak.mail.ui.bottomSheetDialogs.LockedMailboxBottomSheetDialogArgs
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.ui.main.menuDrawer.items.ActionViewHolder.MenuDrawerAction.ActionType
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ConfettiUtils
import com.infomaniak.mail.utils.ConfettiUtils.ConfettiType.INFOMANIAK
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.getStringWithBoldArg
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
    lateinit var modifyNameFolderDialog: ModifyNameFolderDialog

    @Inject
    lateinit var confirmDeleteFolderDialog: ConfirmDeleteFolderDialog

    @Inject
    lateinit var menuDrawerAdapter: MenuDrawerAdapter

    private val currentClassName: String = MenuDrawerFragment::class.java.name

    var exitDrawer: (() -> Unit)? = null

    val drawerHeader get() = _binding?.drawerHeader
    val drawerContent get() = _binding?.menuDrawerRecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindAlertToViewLifecycle(createFolderDialog)
        bindAlertToViewLifecycle(modifyNameFolderDialog)

        setupListeners()
        setupManageFolderDialog()
        setupRecyclerView()

        observeMenuDrawerData()
        observeCurrentFolder()
        observeCurrentMailbox()
        observeManageFolder()
    }

    private fun setupListeners() {
        binding.settingsButton.setOnClickListener {
            closeDrawer()
            safelyNavigate(ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment())
        }
    }

    private fun setupManageFolderDialog() {
        createFolderDialog.setCallbacks(onPositiveButtonClicked = mainViewModel::createNewFolder)
        modifyNameFolderDialog.setCallbacks(onPositiveButtonClicked = mainViewModel::modifyNameFolder)
    }

    private fun setupRecyclerView() {
        binding.menuDrawerRecyclerView.adapter = menuDrawerAdapter(
            callbacks = object : MenuDrawerAdapterCallbacks {
                override var onMailboxesHeaderClicked: () -> Unit = ::onMailboxesHeaderClicked
                override var onValidMailboxClicked: (Int) -> Unit = ::onValidMailboxClicked
                override var onInvalidMailboxClicked: (String) -> Unit = ::onInvalidMailboxClicked
                override var onFoldersHeaderClicked: (Boolean) -> Unit = ::onFoldersHeaderClicked
                override var onCreateFolderClicked: () -> Unit = ::onCreateFolderClicked
                override var onFolderClicked: (folderId: String) -> Unit = ::onFolderSelected
                override var onFolderLongClicked: (folderId: String, folderName: String, view: View) -> Unit = ::onFolderManage
                override var onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit = ::onFolderCollapsed
                override var onActionsHeaderClicked: () -> Unit = ::onActionsHeaderClicked
                override var onActionClicked: (ActionType) -> Unit = ::onActionClicked
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
        trackMenuDrawerEvent(MatomoName.Mailboxes, isExpanded)
        areMailboxesExpanded.value = isExpanded
    }

    private fun onValidMailboxClicked(mailboxId: Int) {
        lifecycleScope.launch { AccountUtils.switchToMailbox(mailboxId) }
    }

    private fun onInvalidMailboxClicked(mailboxEmail: String) {
        safeNavigate(
            resId = R.id.lockedMailboxBottomSheetDialog,
            args = LockedMailboxBottomSheetDialogArgs(mailboxEmail).toBundle(),
            currentClassName = currentClassName,
        )
    }

    private fun onFoldersHeaderClicked(isCollapsed: Boolean) {
        trackMenuDrawerEvent(MatomoName.CustomFolders, !isCollapsed)
        menuDrawerViewModel.areCustomFoldersExpanded.value = !isCollapsed
    }

    private fun onCreateFolderClicked() {
        trackCreateFolderEvent(MatomoName.FromMenuDrawer)
        createFolderDialog.show()
    }

    private fun onFolderSelected(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }

    private fun onFolderManage(folderId: String, folderName: String, view: View) {
        val popup = PopupMenu(context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.item_menu_settings_folder, popup.menu)
        popup.show()

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.modifySettingsFolder -> {
                    trackManageFolderEvent(MatomoName.Rename)
                    modifyNameFolderDialog.setFolderIdAndShow(folderName, folderId)
                    true
                }
                R.id.deleteSettingsFolder -> {
                    trackManageFolderEvent(MatomoName.Delete)
                    confirmDeleteFolderDialog.show(
                        title = requireContext().getString(R.string.deleteFolderDialogTitle),
                        description = getStringWithBoldArg(R.string.deleteFolderDialogDescription, folderName),
                        positiveButtonText = R.string.buttonYes,
                        negativeButtonText = R.string.buttonNo,
                        onPositiveButtonClicked = {
                            trackManageFolderEvent(MatomoName.DeleteConfirm)
                            mainViewModel.deleteFolder(folderId)
                        }
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun onFolderCollapsed(folderId: String, shouldCollapse: Boolean) {
        menuDrawerViewModel.toggleFolderCollapsingState(folderId, shouldCollapse)
    }

    private fun onActionsHeaderClicked() {
        trackMenuDrawerEvent(MatomoName.AdvancedActions, value = !menuDrawerViewModel.areActionsExpanded.value!!)
        menuDrawerViewModel.toggleActionsCollapsingState()
    }

    private fun onActionClicked(type: ActionType) {
        when (type) {
            ActionType.SYNC_AUTO_CONFIG -> onSyncAutoConfigClicked()
            ActionType.IMPORT_MAILS -> onImportMailsClicked()
            ActionType.RESTORE_MAILS -> onRestoreMailsClicked()
        }
    }

    private fun onSyncAutoConfigClicked() {
        trackSyncAutoConfigEvent(MatomoName.OpenFromMenuDrawer)
        launchSyncAutoConfigActivityForResult()
        closeDrawer()
    }

    private fun onImportMailsClicked() {
        trackMenuDrawerEvent(MatomoName.ImportEmails)
        context?.openUrl(IMPORT_EMAILS_URL)
        closeDrawer()
    }

    private fun onRestoreMailsClicked() {
        trackMenuDrawerEvent(MatomoName.RestoreEmails)
        safeNavigate(R.id.restoreEmailsBottomSheetDialog, currentClassName = currentClassName)
    }

    private fun onFeedbackClicked() {
        val user = AccountUtils.currentUser
        if (user?.isStaff == true) {
            Intent(requireContext(), BugTrackerActivity::class.java).apply {
                putExtras(
                    BugTrackerActivityArgs(
                        userId = user.id,
                        userCurrentOrganizationId = user.preferences.organizationPreference.currentOrganizationId,
                        userEmail = user.email,
                        userDisplayName = user.displayName,
                        appId = BuildConfig.APPLICATION_ID,
                        appBuildNumber = BuildConfig.VERSION_NAME,
                        bucketIdentifier = BuildConfig.BUGTRACKER_MAIL_BUCKET_ID,
                        projectName = BuildConfig.BUGTRACKER_MAIL_PROJECT_NAME,
                    ).toBundle(),
                )
            }.also(::startActivity)
        } else {
            trackMenuDrawerEvent(MatomoName.Feedback)
            context?.openUrl(requireContext().getString(R.string.urlUserReportAndroid))
        }

        closeDrawer()
    }

    private fun onHelpClicked() {
        ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SUPPORT.id)
        trackMenuDrawerEvent(MatomoName.Help)
        context?.openUrl(CHATBOT_URL)
        closeDrawer()
    }

    private fun onAppVersionClicked() {
        ConfettiUtils.onEasterEggConfettiClicked(
            container = (activity as? MainActivity)?.getConfettiContainer(),
            type = INFOMANIAK,
            matomoValue = "menuDrawer",
        )
    }

    private fun observeMenuDrawerData() = with(mainViewModel) {

        Utils.waitInitMediator(
            mailboxesLive,
            menuDrawerViewModel.areMailboxesExpanded,
            displayedFoldersLive,
            menuDrawerViewModel.areCustomFoldersExpanded,
            menuDrawerViewModel.areActionsExpanded,
            currentPermissionsLive,
            currentQuotasLive,
            constructor = {
                @Suppress("UNCHECKED_CAST")
                MediatorContainer(
                    it[0] as List<Mailbox>,
                    it[1] as Boolean,
                    it[2] as MainViewModel.DisplayedFolders,
                    it[3] as Boolean,
                    it[4] as Boolean,
                    it[5] as MailboxPermissions?,
                    it[6] as Quotas?,
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

    private fun observeManageFolder() {
        mainViewModel.newFolderResultTrigger.observe(viewLifecycleOwner) { createFolderDialog.resetLoadingAndDismiss() }
        mainViewModel.renameFolderResultTrigger.observe(viewLifecycleOwner) { modifyNameFolderDialog.resetLoadingAndDismiss() }
        mainViewModel.deleteFolderResultTrigger.observe(viewLifecycleOwner) { confirmDeleteFolderDialog.resetLoadingAndDismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class MediatorContainer(
        val mailboxes: List<Mailbox>,
        val areMailboxesExpanded: Boolean,
        val displayedFolders: MainViewModel.DisplayedFolders,
        val areCustomFoldersExpanded: Boolean,
        val areActionsExpanded: Boolean,
        val permissions: MailboxPermissions?,
        val quotas: Quotas?,
    )
}
