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
package com.infomaniak.mail.ui.main.settings.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentMessageListDensitySettingBinding

class MessageListDensitySettingFragment : Fragment() {

    private lateinit var binding: FragmentMessageListDensitySettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMessageListDensitySettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBack()
    }

    private fun setupBack() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun onResume() {
        super.onResume()
        addListeners()
    }

    private fun addListeners() = with(binding) {
        listDensityButtonsGroup.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val resId = when (buttonId) {
                R.id.listDensityButtonCompact -> R.drawable.bg_list_density_compact
                R.id.listDensityButtonNormal -> R.drawable.bg_list_density_default
                else -> R.drawable.bg_list_density_large
            }

            listDensityImage.setImageResource(resId)
        }
    }

    override fun onPause() {
        removeListeners()
        super.onPause()
    }

    private fun removeListeners() {
        binding.listDensityButtonsGroup.clearOnButtonCheckedListeners()
    }
}
