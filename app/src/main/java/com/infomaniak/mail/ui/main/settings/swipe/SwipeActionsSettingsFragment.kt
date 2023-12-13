/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.databinding.FragmentSwipeActionsSettingsBinding
import com.infomaniak.mail.utils.animatedNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SwipeActionsSettingsFragment : Fragment() {

    private var binding: FragmentSwipeActionsSettingsBinding by safeBinding()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwipeActionsSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        with(localSettings) {
            swipeRightView.setSubtitle(swipeRight.nameRes)
            swipeLeftView.setSubtitle(swipeLeft.nameRes)

            swipeRightIllustration.apply {
                swipeBackground.setCardBackgroundColor(swipeRight.getBackgroundColor(requireContext()))
                swipeRight.iconRes?.let(swipeIcon::setImageResource)
                swipeIcon.isGone = swipeRight.iconRes == null
                swipeIcon.contentDescription = getString(swipeRight.nameRes)
                swipeToDefine.isVisible = swipeRight.iconRes == null
            }

            swipeLeftIllustration.apply {
                swipeBackground.setCardBackgroundColor(swipeLeft.getBackgroundColor(requireContext()))
                swipeLeft.iconRes?.let(swipeIcon::setImageResource)
                swipeIcon.isGone = swipeLeft.iconRes == null
                swipeIcon.contentDescription = getString(swipeLeft.nameRes)
                swipeToDefine.isVisible = swipeLeft.iconRes == null
            }
        }

        swipeRightCardview.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeRight) }
        swipeLeftCardview.setOnClickListener { navigateToSwipeActionSelection(R.string.settingsSwipeLeft) }
    }

    private fun navigateToSwipeActionSelection(@StringRes resId: Int) {
        animatedNavigation(SwipeActionsSettingsFragmentDirections.actionSwipeActionsSettingsToSwipeActionSelectionSetting(resId))
    }

    companion object {
        val DEFAULT_SWIPE_ACTION_RIGHT = SwipeAction.READ_UNREAD
        val DEFAULT_SWIPE_ACTION_LEFT = SwipeAction.DELETE
    }
}
