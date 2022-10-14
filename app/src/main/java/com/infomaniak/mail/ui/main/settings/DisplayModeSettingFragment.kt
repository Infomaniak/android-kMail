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
import androidx.fragment.app.Fragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.data.UiSettings.ThreadMode
import com.infomaniak.mail.data.UiSettings.ThreadMode.MESSAGES
import com.infomaniak.mail.data.UiSettings.ThreadMode.THREADS
import com.infomaniak.mail.databinding.FragmentDisplayModeSettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class DisplayModeSettingFragment : Fragment() {

    private lateinit var binding: FragmentDisplayModeSettingBinding

    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDisplayModeSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.radioGroup) {
        super.onViewCreated(view, savedInstanceState)

        initTranslationTable(
            mapOf(
                R.id.threadMode to THREADS,
                R.id.messageMode to MESSAGES,
            )
        )

        check(uiSettings.threadMode)

        onItemCheckedListener { _, _, enum ->
            notYetImplemented()
            uiSettings.threadMode = enum as? ThreadMode ?: return@onItemCheckedListener
        }
    }
}
