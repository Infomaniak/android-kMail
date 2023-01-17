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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.SwipeAction
import com.infomaniak.mail.data.LocalSettings.SwipeAction.*
import com.infomaniak.mail.databinding.FragmentSwipeActionsSelectionSettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class SwipeActionsSelectionSettingFragment : Fragment() {

    private lateinit var binding: FragmentSwipeActionsSelectionSettingBinding
    private val navigationArgs: SwipeActionsSelectionSettingFragmentArgs by navArgs()

    private val localSettings by lazy { LocalSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwipeActionsSelectionSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val actionResId = navigationArgs.titleResId
        root.setTitle(actionResId)

        radioGroup.apply {
            initBijectionTable(
                R.id.delete to DELETE,
                R.id.archive to ARCHIVE,
                R.id.readUnread to READ_UNREAD,
                R.id.move to MOVE,
                R.id.favorite to FAVORITE,
                R.id.postpone to POSTPONE,
                R.id.spam to SPAM,
                R.id.readAndArchive to READ_AND_ARCHIVE,
                R.id.quickActionMenu to QUICKACTIONS_MENU,
                R.id.none to NONE,
            )

            check(localSettings.getSwipeAction(actionResId))

            onItemCheckedListener { _, _, swipeAction ->
                swipeAction as SwipeAction

                when (swipeAction) {
                    ARCHIVE, DELETE, FAVORITE, NONE, QUICKACTIONS_MENU, READ_UNREAD, READ_AND_ARCHIVE, SPAM -> Unit
                    else -> notYetImplemented()
                }

                saveAction(swipeAction)

                findNavController().popBackStack()
            }
        }
    }

    private fun saveAction(swipeAction: SwipeAction) = with(localSettings) {
        when (navigationArgs.titleResId) {
            R.string.settingsSwipeRight -> swipeRight = swipeAction
            R.string.settingsSwipeLeft -> swipeLeft = swipeAction
        }
    }
}
