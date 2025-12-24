/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.auth.room.UserDatabase
import com.infomaniak.core.crossapplogin.back.CrossAppLogin
import com.infomaniak.core.ksuite.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.ksuite.myksuite.ui.views.MyKSuiteDashboardFragmentArgs
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.core.legacy.applock.LockActivity
import com.infomaniak.core.legacy.applock.Utils.silentlyReverseSwitch
import com.infomaniak.core.legacy.utils.openAppNotificationSettings
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.ui.showToast
import com.infomaniak.mail.MatomoMail.MatomoCategory
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackMyKSuiteEvent
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentSettingsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.UiUtils.saveFocusWhenNavigatingBack
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets
import com.infomaniak.mail.utils.extensions.launchSyncAutoConfigActivityForResult
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.getDashboardData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val myKSuiteViewModel: MykSuiteViewModel by viewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var myKSuiteDataUtils: MyKSuiteDataUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveFocusWhenNavigatingBack(getLayout = { binding.linearLayoutContainer }, lifecycle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleEdgeToEdge()

        setupMailboxesAdapter()
        setupListeners()
        setSubtitlesInitialState()
        setupMyKSuite()

        observeMyKSuiteData()
    }

    private fun handleEdgeToEdge() = with(binding) {
        root.setCustomInsetsBehavior { insets, contentView ->
            contentView.applySideAndBottomSystemInsets(insets, withBottom = false)
            linearLayoutContainer.applySideAndBottomSystemInsets(insets, withSides = false)
        }
    }

    private fun setupMyKSuite() {
        binding.myKSuiteLayout.isGone = myKSuiteDataUtils.myKSuite == null
        myKSuiteDataUtils.myKSuite?.let { setupMyKSuiteLayout(it) } ?: myKSuiteViewModel.refreshMyKSuite()
    }

    private fun setupMyKSuiteLayout(myKSuiteData: MyKSuiteData) = with(binding) {
        observeMyKSuiteMailbox()

        myKSuiteSettingsTitle.setText(myKSuiteData.name)

        myKSuiteViewModel.getMyKSuiteMailbox(myKSuiteData.mail.mailboxId)

        myKSuiteSubscription.setOnClickListener { openMyKSuiteDashboard(myKSuiteData) }
    }

    private fun observeMyKSuiteMailbox() {
        myKSuiteViewModel.myKSuiteMailboxResult.observe(viewLifecycleOwner) { mailbox ->
            binding.myKSuiteMailAddress.apply {
                isVisible = mailbox != null

                if (mailbox == null) return@observe

                setTitle(mailbox.email)
                setOnClickListener {
                    animatedNavigation(
                        SettingsFragmentDirections.actionSettingsToMailboxSettings(mailbox.objectId, mailbox.email)
                    )
                }
            }
        }
    }

    private fun observeMyKSuiteData() {
        myKSuiteViewModel.myKSuiteDataResult.observeNotNull(viewLifecycleOwner) { data -> setupMyKSuiteLayout(data) }
    }

    private fun openMyKSuiteDashboard(myKSuiteData: MyKSuiteData) {

        val user = AccountUtils.currentUser ?: return

        trackMyKSuiteEvent(MatomoKSuite.OPEN_DASHBOARD_NAME)

        val args = MyKSuiteDashboardFragmentArgs(dashboardData = getDashboardData(myKSuiteData, user))
        animatedNavigation(resId = R.id.myKSuiteDashboardFragment, args = args.toBundle())
    }

    override fun onResume() {
        super.onResume()
        setSwitchesInitialState()
    }

    private fun setupMailboxesAdapter() {
        val mailboxesAdapter = SettingsMailboxesAdapter { selectedMailbox ->
            with(selectedMailbox) {
                if (isLocked) {
                    context?.showToast(R.string.errorMailboxLocked)
                } else {
                    animatedNavigation(SettingsFragmentDirections.actionSettingsToMailboxSettings(objectId, email))
                }
            }
        }

        binding.mailboxesList.adapter = mailboxesAdapter
        mainViewModel.mailboxesLive.observe(viewLifecycleOwner) { mailboxes ->
            mailboxesAdapter.setMailboxes(mailboxes.filterNot { it.mailboxId == myKSuiteDataUtils.myKSuite?.mail?.mailboxId })
        }
    }

    private fun setSubtitlesInitialState() = with(binding) {
        with(localSettings) {
            settingsThreadListDensity.setSubtitle(threadDensity.localisedNameRes)
            settingsTheme.setSubtitle(theme.localisedNameRes)
            settingsAccentColor.setSubtitle(accentColor.localisedNameRes)
            settingsThreadMode.setSubtitle(threadMode.localisedNameRes)
            settingsExternalContent.setSubtitle(externalContent.localisedNameRes)
            settingsAutomaticAdvance.setSubtitle(autoAdvanceMode.localisedNameRes)
            lifecycleScope.launch {
                UserDatabase().userDao().allUsers.map { list -> list.any { it.isStaff } }.collectLatest { hasStaffAccount ->
                    if (!hasStaffAccount) return@collectLatest
                    settingsCrossAppDeviceId.isVisible = true
                    val crossAppLogin = CrossAppLogin.forContext(requireContext(), this)
                    @OptIn(ExperimentalUuidApi::class)
                    crossAppLogin.sharedDeviceIdFlow.collect { crossAppDeviceId ->
                        settingsCrossAppDeviceId.setSubtitle(crossAppDeviceId.toHexDashString())
                    }
                }
            }
        }
    }

    private fun setSwitchesInitialState() = with(binding) {
        settingsAppLock.isChecked = localSettings.isAppLocked
    }

    private fun setupListeners() = with(binding) {
        settingsAppLock.apply {
            isVisible = LockActivity.hasBiometrics()
            isChecked = localSettings.isAppLocked
            setOnClickListener {
                trackEvent(MatomoCategory.SettingsGeneral, MatomoName.Lock, value = isChecked.toFloat())
                // Reverse switch (before official parameter changed) by silent click
                requireActivity().silentlyReverseSwitch(toggle!!) { isChecked ->
                    localSettings.isAppLocked = isChecked
                    if (isChecked) LockActivity.unlock()
                }
            }
        }

        settingsNotifications.setOnClickListener {
            trackEvent(MatomoCategory.SettingsNotifications, MatomoName.OpenNotificationSettings)
            requireContext().openAppNotificationSettings()
        }

        settingsSyncAutoConfig.setOnClickListener {
            trackSyncAutoConfigEvent(MatomoName.OpenFromSettings)
            launchSyncAutoConfigActivityForResult()
        }

        settingsSend.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToSendSettings())
        }

        settingsExternalContent.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToExternalContentSetting())
        }

        settingsAutomaticAdvance.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToAutoAdvanceSettings())
        }

        settingsThreadListDensity.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToThreadListDensitySetting())
        }

        settingsTheme.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToThemeSetting())
        }

        settingsAccentColor.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToAccentColorSetting())
        }

        settingsSwipeActions.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToSwipeActionsSetting())
        }

        settingsThreadMode.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToThreadModeSetting())
        }

        settingsDataManagement.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToDataManagementSettings())
        }

        settingsAccountManagement.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToAccountManagementSettings())
        }
    }
}
