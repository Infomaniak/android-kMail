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
import androidx.fragment.app.Fragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.UiSettings
import com.infomaniak.mail.databinding.FragmentCancelDelaySettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class CancelDelaySettingFragment : Fragment() {

    private lateinit var binding: FragmentCancelDelaySettingBinding

    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentCancelDelaySettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNames()
        checkInitialValue()

        binding.radioGroup.onItemCheckedListener { _, value, _ ->
            val seconds = value?.toInt() ?: throw NullPointerException("Radio button had no associated value")
            uiSettings.cancelDelay = seconds
            notYetImplemented()
        }
    }

    private fun setupNames() = with(binding) {
        val resId = R.string.settingsDelaySeconds
        seconds10.setText(getString(resId, 10))
        seconds15.setText(getString(resId, 15))
        seconds20.setText(getString(resId, 20))
        seconds25.setText(getString(resId, 25))
        seconds30.setText(getString(resId, 30))
    }

    private fun checkInitialValue() = with(binding.radioGroup) {
        check(
            when (uiSettings.cancelDelay) {
                10 -> R.id.seconds10
                15 -> R.id.seconds15
                20 -> R.id.seconds20
                25 -> R.id.seconds25
                30 -> R.id.seconds30
                else -> R.id.disabled
            }
        )
    }
}
