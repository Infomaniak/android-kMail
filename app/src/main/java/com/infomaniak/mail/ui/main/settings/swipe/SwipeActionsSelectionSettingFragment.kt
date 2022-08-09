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
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.UserInfosController.getUserPreferences
import com.infomaniak.mail.data.models.user.UserPreferences.SwipeAction
import com.infomaniak.mail.databinding.FragmentSwipeActionsSelectionSettingBinding

class SwipeActionsSelectionSettingFragment : Fragment() {

    private val navigationArgs: SwipeActionsSelectionSettingFragmentArgs by navArgs()
    private lateinit var binding: FragmentSwipeActionsSelectionSettingBinding

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
        when (getUserPreferences().getSwipeAction(actionResId)) {
            SwipeAction.NONE -> actionNoneCheck
            SwipeAction.ARCHIVE -> actionArchiveCheck
            SwipeAction.DELETE -> actionDeleteCheck
            SwipeAction.FAVORITE -> actionFavoriteCheck
            SwipeAction.MOVE -> actionMoveCheck
            SwipeAction.POSTPONE -> actionPostponeCheck
            SwipeAction.QUICKACTIONS_MENU -> actionQuickActionsMenuCheck
            SwipeAction.READ_AND_ARCHIVE -> actionReadAndArchiveCheck
            SwipeAction.READ_UNREAD -> actionReadUnreadCheck
            SwipeAction.SPAM -> actionSpamCheck
            else -> null
        }?.selectOption()
    }

    private fun setupListeners() = with(binding) {
        actionDelete.setOnClickListener {
            actionDeleteCheck.selectOption()
            saveAction(SwipeAction.DELETE)
        }
        actionArchive.setOnClickListener {
            actionArchiveCheck.selectOption()
            saveAction(SwipeAction.ARCHIVE)
        }
        actionReadUnread.setOnClickListener {
            actionReadUnreadCheck.selectOption()
            saveAction(SwipeAction.READ_UNREAD)
        }
        actionMove.setOnClickListener {
            actionMoveCheck.selectOption()
            saveAction(SwipeAction.MOVE)
        }
        actionFavorite.setOnClickListener {
            actionFavoriteCheck.selectOption()
            saveAction(SwipeAction.FAVORITE)
        }
        actionPostpone.setOnClickListener {
            actionPostponeCheck.selectOption()
            saveAction(SwipeAction.POSTPONE)
        }
        actionSpam.setOnClickListener {
            actionSpamCheck.selectOption()
            saveAction(SwipeAction.SPAM)
        }
        actionReadAndArchive.setOnClickListener {
            actionReadAndArchiveCheck.selectOption()
            saveAction(SwipeAction.READ_AND_ARCHIVE)
        }
        actionQuickActionsMenu.setOnClickListener {
            actionQuickActionsMenuCheck.selectOption()
            saveAction(SwipeAction.QUICKACTIONS_MENU)
        }
        actionNone.setOnClickListener {
            actionNoneCheck.selectOption()
            saveAction(SwipeAction.NONE)
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

    private fun saveAction(swipeAction: SwipeAction) {
        MailData.updateSwipeAction(navigationArgs.titleResId, swipeAction)
    }
}
