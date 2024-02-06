/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentPermissionsOnboardingPagerBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.PermissionUtils
import com.infomaniak.mail.utils.extensions.removeOverScrollForApiBelow31
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PermissionsOnboardingPagerFragment : Fragment() {

    private var binding: FragmentPermissionsOnboardingPagerBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var currentPosition = 0

    @Inject
    lateinit var localSettings: LocalSettings

    @Inject
    lateinit var permissionUtils: PermissionUtils

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPermissionsOnboardingPagerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.statusBarColor = localSettings.accentColor.getOnboardingSecondaryBackground(context)

        permissionUtils.apply {
            registerReadContactsPermission(fragment = this@PermissionsOnboardingPagerFragment)
            registerNotificationsPermissionIfNeeded(fragment = this@PermissionsOnboardingPagerFragment)
        }

        permissionsViewpager.apply {
            adapter = PermissionsPagerAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
            isUserInputEnabled = false
            savedInstanceState?.getInt(VIEW_PAGER_POSITION_KEY)?.let { setCurrentItem(it, false) }
            removeOverScrollForApiBelow31()
        }

        continueButton.setOnClickListener {
            when (permissionsViewpager.currentItem) {
                0 -> {
                    permissionUtils.requestReadContactsPermission { hasPermission ->
                        if (hasPermission) mainViewModel.updateUserInfo()
                        if (permissionsViewpager.isLastPage()) {
                            leaveOnboarding()
                        } else {
                            currentPosition += 1
                            permissionsViewpager.currentItem += 1
                        }
                    }
                }
                1 -> permissionUtils.requestNotificationsPermissionIfNeeded { leaveOnboarding() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(VIEW_PAGER_POSITION_KEY, currentPosition)
        super.onSaveInstanceState(outState)
    }

    private fun ViewPager2.isLastPage() = adapter?.let { currentItem == it.itemCount - 1 } ?: true

    fun leaveOnboarding() {
        localSettings.showPermissionsOnboarding = false
        safeNavigate(R.id.threadListFragment)
    }

    companion object {
        private const val VIEW_PAGER_POSITION_KEY = "viewPagerPositionKey"
    }
}
