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
package com.infomaniak.mail.ui.main.settings.swipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.userInfos.UserPreferencesController
import com.infomaniak.mail.data.models.user.UserPreferences
import com.infomaniak.mail.databinding.FragmentSwipeActionsSettingsBinding

class SwipeActionsSettingsFragment : Fragment() {

    private lateinit var binding: FragmentSwipeActionsSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwipeActionsSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        UserPreferencesController.getUserPreferences().setupUi()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun UserPreferences.setupUi() = with(binding) {
        settingsSwipeShortRightText.setText(shortRightSwipe.nameRes)
        settingsSwipeLongRightText.setText(longRightSwipe.nameRes)
        settingsSwipeShortLeftText.setText(shortLeftSwipe.nameRes)
        settingsSwipeLongLeftText.setText(longLeftSwipe.nameRes)
    }

    private fun setupListeners() = with(binding) {
        settingsSwipeShortRight.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeShortRight) }
        settingsSwipeLongRight.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeLongRight) }
        settingsSwipeShortLeft.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeShortLeft) }
        settingsSwipeLongLeft.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeLongLeft) }
    }

    private fun navigateToSwipeActionSelection(@StringRes resId: Int) {
        safeNavigate(SwipeActionsSettingsFragmentDirections.actionSwipeActionsSettingsToSwipeActionSelectionSetting(resId))
    }
}
