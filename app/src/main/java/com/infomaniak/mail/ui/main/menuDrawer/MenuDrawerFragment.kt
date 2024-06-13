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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.Fade.IN
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackCreateFolderEvent
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentMenuDrawerBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.main.MailboxListFragment
import com.infomaniak.mail.ui.main.folder.ThreadListFragmentDirections
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ConfettiUtils
import com.infomaniak.mail.utils.ConfettiUtils.ConfettiType
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.launchSyncAutoConfigActivityForResult
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.extensions.toggleChevron
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MenuDrawerFragment : MenuFoldersFragment(), MailboxListFragment {

    private var _binding: FragmentMenuDrawerBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView
    private val menuDrawerViewModel: MenuDrawerViewModel by viewModels()

    var exitDrawer: (() -> Unit)? = null

    override val isInMenuDrawer = true
    override val defaultFoldersList: RecyclerView by lazy { binding.defaultFoldersList }
    override val customFoldersList: RecyclerView by lazy { binding.customFoldersList }

    override val hasValidMailboxes = true
    override val currentClassName: String = MenuDrawerFragment::class.java.name
    override val mailboxesAdapter get() = binding.mailboxList.adapter as MailboxesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMenuDrawerBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayVersion()
        setupListeners()
        setupCreateFolderDialog()

        observeCurrentMailbox()
        observeMailboxesLive()
        observeCurrentFolder()
        observeFoldersLive()
        observeQuotasLive()
        observePermissionsLive()
        observeNewFolderCreation()
    }

    private fun setupListeners() = with(binding) {

        settingsButton.setOnClickListener {
            closeDrawer()
            safeNavigate(
                directions = ThreadListFragmentDirections.actionThreadListFragmentToSettingsFragment(),
                currentClassName = currentClassName,
            )
        }

        mailboxSwitcher.setOnClickListener {
            addMenuDrawerTransition()
            mailboxList.apply {
                isVisible = !isVisible
                mailboxExpandButton.toggleChevron(!isVisible)
                setMailboxSwitcherTextAppearance(isVisible)
                trackMenuDrawerEvent("mailboxes", isVisible)
            }
        }

        customFolders.setOnClickListener {
            addMenuDrawerTransition()
            trackMenuDrawerEvent("customFolders", !customFolders.isCollapsed)
            customFoldersLayout.isGone = customFolders.isCollapsed
        }

        customFolders.setOnActionClickListener {
            trackCreateFolderEvent("fromMenuDrawer")
            inputDialog.show(R.string.newFolderDialogTitle, R.string.newFolderDialogHint, R.string.buttonCreate)
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
                            repoGitHub = BuildConfig.GITHUB_REPO,
                        ).toBundle(),
                    )
                }.also(::startActivity)
            } else {
                trackMenuDrawerEvent("feedback")
                context.openUrl(getString(R.string.urlUserReportAndroid))
            }
        }

        help.setOnClickListener {
            ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SUPPORT.id)
            trackMenuDrawerEvent("help")
            context.openUrl(BuildConfig.CHATBOT_URL)
            closeDrawer()
        }

        advancedActions.setOnClickListener {
            addMenuDrawerTransition()
            trackMenuDrawerEvent("advancedActions", !advancedActions.isCollapsed)
            advancedActionsLayout.isGone = advancedActions.isCollapsed
        }

        syncAutoConfig.setOnClickListener {
            trackSyncAutoConfigEvent("openFromMenuDrawer")
            launchSyncAutoConfigActivityForResult()
            closeDrawer()
        }

        importMails.setOnClickListener {
            trackMenuDrawerEvent("importEmails")
            requireContext().openUrl(BuildConfig.IMPORT_EMAILS_URL)
            closeDrawer()
        }

        restoreMails.setOnClickListener {
            trackMenuDrawerEvent("restoreEmails")
            safeNavigate(R.id.restoreEmailsBottomSheetDialog, currentClassName = currentClassName)
        }

        appVersionName.setOnClickListener {
            ConfettiUtils.onEasterEggConfettiClicked(
                container = (activity as? MainActivity)?.getConfettiContainer(),
                type = ConfettiType.INFOMANIAK,
                matomoValue = "MenuDrawer",
            )
        }
    }

    override fun onDestroyView() {
        TransitionManager.endTransitions(binding.drawerContentScrollView)
        super.onDestroyView()
        _binding = null
    }

    private fun setupCreateFolderDialog() {
        inputDialog.setCallbacks(
            onPositiveButtonClicked = { folderName ->
                trackCreateFolderEvent("confirm")
                mainViewModel.createNewFolder(folderName)
            },
            onErrorCheck = ::checkForFolderCreationErrors,
        )
    }

    override fun setupAdapters() {
        super.setupAdapters()
        binding.mailboxList.adapter = MailboxesAdapter(
            isInMenuDrawer = isInMenuDrawer,
            hasValidMailboxes = hasValidMailboxes,
            onValidMailboxClicked = { mailboxId -> onValidMailboxClicked(mailboxId) },
            onLockedMailboxClicked = { mailboxEmail -> onLockedMailboxClicked(mailboxEmail) },
            onInvalidPasswordMailboxClicked = { mailbox -> onInvalidPasswordMailboxClicked(mailbox) },
        )
    }

    override fun onFolderSelected(folderId: String) {
        mainViewModel.openFolder(folderId)
        closeDrawer()
    }

    override fun onFolderCollapse(folderId: String, shouldCollapse: Boolean) {
        menuDrawerViewModel.toggleFolderCollapsingState(folderId, shouldCollapse)
    }

    private fun addMenuDrawerTransition(shouldExclude: Boolean = false, shouldFade: Boolean = true) {
        val transition = TransitionSet()
            .addTransition(ChangeBounds())
            .also { if (shouldFade) it.addTransition(Fade(IN)) }
            .excludeTarget(RecyclerView::class.java, shouldExclude)
            .setDuration(MENU_DRAWER_TRANSITION_DURATION)

        TransitionManager.beginDelayedTransition(binding.drawerContentScrollView, transition)
    }

    @SuppressLint("SetTextI18n")
    private fun displayVersion() {
        binding.appVersionName.text = "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun observeCurrentMailbox() {
        mainViewModel.currentMailbox.observeNotNull(viewLifecycleOwner) { mailbox ->
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
        menuDrawerViewModel.otherMailboxesLive.observe(viewLifecycleOwner) { mailboxes ->

            mailboxesAdapter.setMailboxes(mailboxes)

            val hasMoreThanOneMailbox = mailboxes.isNotEmpty()

            mailboxSwitcher.apply {
                isClickable = hasMoreThanOneMailbox
                isFocusable = hasMoreThanOneMailbox
            }

            mailboxExpandButton.isVisible = hasMoreThanOneMailbox
        }
    }

    private fun observeCurrentFolder() {
        mainViewModel.currentFolder.observeNotNull(viewLifecycleOwner) { folder ->
            defaultFoldersAdapter.updateSelectedState(folder.id)
            customFoldersAdapter.updateSelectedState(folder.id)
        }
    }

    private fun observeFoldersLive() = with(mainViewModel) {
        Utils.waitInitMediator(
            currentFolder,
            currentDefaultFoldersLive,
        ).observe(viewLifecycleOwner) { (currentFolder, defaultFolders) ->
            runCatchingRealm {
                val newCurrentFolderId = currentFolder?.id ?: return@observe
                binding.defaultFoldersList.post {
                    defaultFoldersAdapter.setFolders(defaultFolders, newCurrentFolderId)
                }
            }
        }

        Utils.waitInitMediator(
            currentFolder,
            currentCustomFoldersLive,
        ).observe(viewLifecycleOwner) { (currentFolder, customFolders) ->
            runCatchingRealm {
                binding.noFolderText.isVisible = customFolders.isEmpty()
                val newCurrentFolderId = currentFolder?.id ?: return@observe
                binding.customFoldersList.post {
                    customFoldersAdapter.setFolders(customFolders, newCurrentFolderId)
                }
            }
        }
    }

    private fun observeQuotasLive() = with(binding) {
        mainViewModel.currentQuotasLive.observe(viewLifecycleOwner) { quotas ->
            val isLimited = quotas != null

            storageLayout.isVisible = isLimited
            storageDivider.isVisible = isLimited

            if (isLimited) {
                storageText.text = quotas!!.getText(context)
                storageIndicator.progress = quotas.getProgress()
            }
        }
    }

    private fun observePermissionsLive() {
        mainViewModel.currentPermissionsLive.observe(viewLifecycleOwner) { permissions ->
            binding.restoreMails.isVisible = permissions?.canRestoreEmails == true
        }
    }

    private fun observeNewFolderCreation() {
        mainViewModel.newFolderResultTrigger.observe(viewLifecycleOwner) { inputDialog.resetLoadingAndDismiss() }
    }

    fun onDrawerOpened() {
        trackScreen()
    }

    fun closeDrawer() {
        exitDrawer?.invoke()
        closeDropdowns()
    }

    fun closeDropdowns() = with(binding) {
        mailboxList.isGone = true
        mailboxExpandButton.rotation = ResourcesCompat.getFloat(resources, R.dimen.angleViewNotRotated)
        setMailboxSwitcherTextAppearance(isOpen = false)
    }

    private fun setMailboxSwitcherTextAppearance(isOpen: Boolean) {
        binding.mailboxSwitcherText.setTextAppearance(if (isOpen) R.style.BodyMedium_Accent else R.style.BodyMedium)
    }

    companion object {
        private const val MENU_DRAWER_TRANSITION_DURATION = 250L
    }
}
