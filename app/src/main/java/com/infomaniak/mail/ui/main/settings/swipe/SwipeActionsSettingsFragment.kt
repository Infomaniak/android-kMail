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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.databinding.FragmentSwipeActionsSettingsBinding

class SwipeActionsSettingsFragment : Fragment() {

    private lateinit var binding: FragmentSwipeActionsSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwipeActionsSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        with(UiSettings.getInstance(requireContext())) {
            swipeRight.setSubtitle(swipeLongRight.nameRes)
            swipeLeft.setSubtitle(swipeLongLeft.nameRes)

            swipeRightIllustration.apply {
                swipeBackground.setCardBackgroundColor(swipeLongRight.getBackgroundColor(requireContext()))
                swipeLongRight.iconRes?.let { swipeIcon.setImageResource(it) }
                swipeIcon.isGone = swipeLongRight.iconRes == null
                swipeToDefine.isVisible = swipeLongRight.iconRes == null
            }

            swipeLeftIllustration.apply {
                swipeBackground.setCardBackgroundColor(swipeLongLeft.getBackgroundColor(requireContext()))
                swipeLongLeft.iconRes?.let { swipeIcon.setImageResource(it) }
                swipeIcon.isGone = swipeLongLeft.iconRes == null
                swipeToDefine.isVisible = swipeLongLeft.iconRes == null
            }
        }

        swipeRight.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeLongRight) }
        swipeLeft.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeLongLeft) }
    }

    private fun navigateToSwipeActionSelection(@StringRes resId: Int) {
        safeNavigate(SwipeActionsSettingsFragmentDirections.actionSwipeActionsSettingsToSwipeActionSelectionSetting(resId))
    }
}
