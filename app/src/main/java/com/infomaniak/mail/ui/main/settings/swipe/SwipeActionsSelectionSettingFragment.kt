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
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.SwipeAction
import com.infomaniak.mail.data.UiSettings.SwipeAction.*
import com.infomaniak.mail.databinding.FragmentSwipeActionsSelectionSettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class SwipeActionsSelectionSettingFragment : Fragment() {

    private lateinit var binding: FragmentSwipeActionsSelectionSettingBinding
    private val navigationArgs: SwipeActionsSelectionSettingFragmentArgs by navArgs()

    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSwipeActionsSelectionSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
        setupUi()
        setupListeners()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupUi() = with(binding) {
        val actionResId = navigationArgs.titleResId
        toolbarText.setText(actionResId)
        when (uiSettings.getSwipeAction(actionResId)) {
            NONE -> actionNoneCheck
            ARCHIVE -> actionArchiveCheck
            DELETE -> actionDeleteCheck
            FAVORITE -> actionFavoriteCheck
            MOVE -> actionMoveCheck
            POSTPONE -> actionPostponeCheck
            QUICKACTIONS_MENU -> actionQuickActionsMenuCheck
            READ_AND_ARCHIVE -> actionReadAndArchiveCheck
            READ_UNREAD -> actionReadUnreadCheck
            SPAM -> actionSpamCheck
            else -> null
        }?.selectOption()
    }

    private fun setupListeners() = with(binding) {
        actionDelete.setOnClickListener {
            notYetImplemented()
            actionDeleteCheck.selectOption()
            saveAction(DELETE)
        }
        actionArchive.setOnClickListener {
            notYetImplemented()
            actionArchiveCheck.selectOption()
            saveAction(ARCHIVE)
        }
        actionReadUnread.setOnClickListener {
            notYetImplemented()
            actionReadUnreadCheck.selectOption()
            saveAction(READ_UNREAD)
        }
        actionMove.setOnClickListener {
            notYetImplemented()
            actionMoveCheck.selectOption()
            saveAction(MOVE)
        }
        actionFavorite.setOnClickListener {
            notYetImplemented()
            actionFavoriteCheck.selectOption()
            saveAction(FAVORITE)
        }
        actionPostpone.setOnClickListener {
            notYetImplemented()
            actionPostponeCheck.selectOption()
            saveAction(POSTPONE)
        }
        actionSpam.setOnClickListener {
            notYetImplemented()
            actionSpamCheck.selectOption()
            saveAction(SPAM)
        }
        actionReadAndArchive.setOnClickListener {
            notYetImplemented()
            actionReadAndArchiveCheck.selectOption()
            saveAction(READ_AND_ARCHIVE)
        }
        actionQuickActionsMenu.setOnClickListener {
            notYetImplemented()
            actionQuickActionsMenuCheck.selectOption()
            saveAction(QUICKACTIONS_MENU)
        }
        actionNone.setOnClickListener {
            notYetImplemented()
            actionNoneCheck.selectOption()
            saveAction(NONE)
        }
    }

    private fun ImageView.selectOption() = with(binding) {

        actionDeleteCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionArchiveCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionReadUnreadCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionMoveCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionFavoriteCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionPostponeCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionSpamCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionReadAndArchiveCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionQuickActionsMenuCheck.let { if (it != this@selectOption) it.isInvisible = true }
        actionNoneCheck.let { if (it != this@selectOption) it.isInvisible = true }

        this@selectOption.isVisible = true
    }

    private fun saveAction(swipeAction: SwipeAction) = with(uiSettings) {
        when (navigationArgs.titleResId) {
            R.string.settingsSwipeShortRight -> swipeShortRight = swipeAction
            R.string.settingsSwipeLongRight -> swipeLongRight = swipeAction
            R.string.settingsSwipeShortLeft -> swipeShortLeft = swipeAction
            R.string.settingsSwipeLongLeft -> swipeLongLeft = swipeAction
        }
    }
}
