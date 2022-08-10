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
package com.infomaniak.mail.ui.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.data.cache.userInfos.UserPreferencesController
import com.infomaniak.mail.data.models.user.UserPreferences.ThreadMode
import com.infomaniak.mail.databinding.FragmentDisplayModeSettingBinding

class DisplayModeSettingFragment : Fragment() {

    private lateinit var binding: FragmentDisplayModeSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDisplayModeSettingBinding.inflate(inflater, container, false).also { binding = it }.root
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
        when (UserPreferencesController.getUserPreferences().getThreadMode()) {
            ThreadMode.THREADS -> settingsOptionDiscussionsCheck.selectOption()
            ThreadMode.MESSAGES -> settingsOptionMessagesCheck.selectOption()
        }
    }

    private fun setupListeners() = with(binding) {
        settingsOptionDiscussions.setOnClickListener { updateDisplayMode(ThreadMode.THREADS, settingsOptionDiscussionsCheck) }
        settingsOptionMessages.setOnClickListener { updateDisplayMode(ThreadMode.MESSAGES, settingsOptionMessagesCheck) }
    }

    private fun updateDisplayMode(displayMode: ThreadMode, chosenOption: ImageView) {
        UserPreferencesController.updateUserPreferences { it.threadMode = displayMode.apiValue }
        chosenOption.selectOption()
    }

    private fun ImageView.selectOption() = with(binding) {

        settingsOptionDiscussionsCheck.let { if (it != this@selectOption) it.isInvisible = true }
        settingsOptionMessagesCheck.let { if (it != this@selectOption) it.isInvisible = true }

        this@selectOption.isVisible = true
    }
}
