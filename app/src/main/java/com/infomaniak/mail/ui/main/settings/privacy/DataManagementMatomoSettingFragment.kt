/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentDataManagementMatomoSettingBinding
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DataManagementMatomoSettingFragment : Fragment() {

    private var binding: FragmentDataManagementMatomoSettingBinding by safeBinding()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDataManagementMatomoSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.settingsTrackingSwitchMatomo) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        isChecked = localSettings.isMatomoTrackingEnabled

        setOnClickListener {
            val hasUserOptIn = isChecked
            localSettings.isMatomoTrackingEnabled = hasUserOptIn
            MatomoMail.tracker.isOptOut = !hasUserOptIn
        }
    }
}
