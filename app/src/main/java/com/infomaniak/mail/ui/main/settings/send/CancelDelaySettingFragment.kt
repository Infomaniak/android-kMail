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
package com.infomaniak.mail.ui.main.settings.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentCancelDelaySettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class CancelDelaySettingFragment : Fragment() {

    private lateinit var binding: FragmentCancelDelaySettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentCancelDelaySettingBinding.inflate(inflater, container, false).also { binding = it }.root
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
        val resId = R.string.settingsDelaySeconds
        settingCancel10Text.text = getString(resId, 10)
        settingCancel15Text.text = getString(resId, 15)
        settingCancel20Text.text = getString(resId, 20)
        settingCancel25Text.text = getString(resId, 25)
        settingCancel30Text.text = getString(resId, 30)
    }

    private fun setupListeners() = with(binding) {
        settingsDisabled.setOnClickListener {
            notYetImplemented()
            settingsDisabledCheck.selectOption()
        }
        settingCancel10.setOnClickListener {
            notYetImplemented()
            settingCancel10Check.selectOption()
        }
        settingCancel15.setOnClickListener {
            notYetImplemented()
            settingCancel15Check.selectOption()
        }
        settingCancel20.setOnClickListener {
            notYetImplemented()
            settingCancel20Check.selectOption()
        }
        settingCancel25.setOnClickListener {
            notYetImplemented()
            settingCancel25Check.selectOption()
        }
        settingCancel30.setOnClickListener {
            notYetImplemented()
            settingCancel30Check.selectOption()
        }
    }

    private fun ImageView.selectOption() = with(binding) {

        settingsDisabledCheck.let { if (it != this@selectOption) it.isInvisible = true }
        settingCancel10Check.let { if (it != this@selectOption) it.isInvisible = true }
        settingCancel15Check.let { if (it != this@selectOption) it.isInvisible = true }
        settingCancel20Check.let { if (it != this@selectOption) it.isInvisible = true }
        settingCancel25Check.let { if (it != this@selectOption) it.isInvisible = true }
        settingCancel30Check.let { if (it != this@selectOption) it.isInvisible = true }

        this@selectOption.isVisible = true
    }
}
