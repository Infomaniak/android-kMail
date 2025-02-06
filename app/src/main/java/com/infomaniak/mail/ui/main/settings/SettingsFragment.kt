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
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.screens.components.KSuiteProductsWithQuotas
import com.infomaniak.core.myksuite.ui.views.MyKSuiteDashboardFragmentArgs
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.silentlyReverseSwitch
import com.infomaniak.lib.core.utils.openAppNotificationSettings
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.MatomoMail.trackSyncAutoConfigEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.databinding.FragmentSettingsBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.UiUtils.saveFocusWhenNavigatingBack
import com.infomaniak.mail.utils.extensions.animatedNavigation
import com.infomaniak.mail.utils.extensions.launchSyncAutoConfigActivityForResult
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveFocusWhenNavigatingBack(getLayout = { binding.linearLayoutContainer }, lifecycle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        setupMailboxesAdapter()
        setupListeners()
        setSubtitlesInitialState()
        setupMyKSuite()

        observeFeatureFlag()
    }

    private fun setupMyKSuite() {
        binding.myKSuiteLayout.isGone = MyKSuiteDataUtils.myKSuite == null
        MyKSuiteDataUtils.myKSuite?.let { setupMyKSuiteLayout(it) } ?: fetchMyKSuite()
    }

    // TODO Manage when My KSuite is null but user has at least one mailbox free (V2 ?)
    private fun fetchMyKSuite() {}

    private fun setupMyKSuiteLayout(myKSuiteData: MyKSuiteData) = with(binding) {
        observeMyKSuiteMailbox()

        myKSuiteData.kSuitePack.type?.displayNameRes?.let(myKSuiteSettingsTitle::setText)

        settingsViewModel.getMyKSuiteMailbox(myKSuiteData.mail.mailboxId)

        myKSuiteSubscription.setOnClickListener {
            val args = MyKSuiteDashboardFragmentArgs(
                email = myKSuiteData.mail.email,
                avatarUri = AccountUtils.currentUser?.avatar ?: "",
                dailySendLimit = myKSuiteData.mail.dailyLimitSent.toString(),
                kSuiteAppsWithQuotas = getKSuiteQuotasApp(myKSuiteData),
            )
            animatedNavigation(resId = R.id.myKSuiteDashboardFragment, args = args.toBundle())
        }
    }

    private fun observeMyKSuiteMailbox() {
        settingsViewModel.myKSuiteMailboxResult.observe(viewLifecycleOwner) { mailbox ->
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

    private fun getKSuiteQuotasApp(myKSuite: MyKSuiteData): Array<KSuiteProductsWithQuotas> {
        val mailProduct = if (myKSuite.isMyKSuitePlus) {
            // TODO: Management for My kSuite Plus (and pack check name like in kDrive)
            null
        } else {
            with(myKSuite.mail) {
                KSuiteProductsWithQuotas.Mail(
                    usedSize = { requireContext().formatShortFileSize(usedSize) },
                    maxSize = { requireContext().formatShortFileSize(storageSizeLimit) },
                    progress = { (usedSize.toDouble() / storageSizeLimit.toDouble()).toFloat() }
                )
            }
        }

        val driveProduct = with(myKSuite.drive) {
            KSuiteProductsWithQuotas.Drive(
                usedSize = { requireContext().formatShortFileSize(usedSize) },
                maxSize = { requireContext().formatShortFileSize(size) },
                progress = { (usedSize.toDouble() / size.toDouble()).toFloat() }
            )
        }

        return if (mailProduct == null) arrayOf(driveProduct) else arrayOf(mailProduct, driveProduct)
    }

    override fun onResume() {
        super.onResume()
        setSwitchesInitialState()
    }

    private fun setupMailboxesAdapter() {
        val mailboxesAdapter = SettingsMailboxesAdapter { selectedMailbox ->
            with(selectedMailbox) {
                if (isAvailable) {
                    animatedNavigation(SettingsFragmentDirections.actionSettingsToMailboxSettings(objectId, email))
                } else {
                    context?.showToast(R.string.errorMailboxLocked)
                }
            }
        }

        binding.mailboxesList.adapter = mailboxesAdapter
        mainViewModel.mailboxesLive.observe(viewLifecycleOwner) { mailboxes ->
            mailboxesAdapter.setMailboxes(mailboxes.filterNot { it.mailboxId == MyKSuiteDataUtils.myKSuite?.mail?.mailboxId })
        }
    }

    private fun setSubtitlesInitialState() = with(binding) {
        with(localSettings) {
            settingsAiEngine.setSubtitle(aiEngine.localisedNameRes)
            settingsThreadListDensity.setSubtitle(threadDensity.localisedNameRes)
            settingsTheme.setSubtitle(theme.localisedNameRes)
            settingsAccentColor.setSubtitle(accentColor.localisedNameRes)
            settingsThreadMode.setSubtitle(threadMode.localisedNameRes)
            settingsExternalContent.setSubtitle(externalContent.localisedNameRes)
            settingsAutomaticAdvance.setSubtitle(autoAdvanceMode.localisedNameRes)
        }
    }

    private fun setSwitchesInitialState() = with(binding) {
        settingsAppLock.isChecked = localSettings.isAppLocked
    }

    private fun setupListeners() = with(binding) {

        addMailbox.setOnClickListener {
            animatedNavigation(resId = R.id.attachMailboxFragment)
        }

        settingsAppLock.apply {
            isVisible = LockActivity.hasBiometrics()
            isChecked = localSettings.isAppLocked
            setOnClickListener {
                trackEvent("settingsGeneral", "lock", value = isChecked.toFloat())
                // Reverse switch (before official parameter changed) by silent click
                requireActivity().silentlyReverseSwitch(toggle!!) { isChecked ->
                    localSettings.isAppLocked = isChecked
                    if (isChecked) LockActivity.unlock()
                }
            }
        }

        settingsNotifications.setOnClickListener {
            trackEvent("settingsNotifications", "openNotificationSettings")
            requireContext().openAppNotificationSettings()
        }

        settingsSyncAutoConfig.setOnClickListener {
            trackSyncAutoConfigEvent("openFromSettings")
            launchSyncAutoConfigActivityForResult()
        }

        settingsSend.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToSendSettings())
        }

        settingsAiEngine.setOnClickListener {
            animatedNavigation(SettingsFragmentDirections.actionSettingsToAiEngineSetting())
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

    private fun observeFeatureFlag() {
        mainViewModel.currentMailbox.observeNotNull(viewLifecycleOwner) {
            binding.settingsAiEngine.isVisible = it.featureFlags.contains(FeatureFlag.AI)
        }
    }
}
