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
import com.infomaniak.mail.data.UiSettings.EmailForwarding
import com.infomaniak.mail.databinding.FragmentForwardMailsSettingBinding
import com.infomaniak.mail.utils.notYetImplemented

class ForwardMailsSettingFragment : Fragment() {

    private lateinit var binding: FragmentForwardMailsSettingBinding
    private val uiSettings by lazy { UiSettings.getInstance(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentForwardMailsSettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.radioGroup) {
        super.onViewCreated(view, savedInstanceState)

        initTranslationTable(
            mapOf(
                R.id.inBody to EmailForwarding.IN_BODY,
                R.id.asAttachment to EmailForwarding.AS_ATTACHMENT
            )
        )

        check(uiSettings.emailForwarding)

        onItemCheckedListener { _, _, enum ->
            uiSettings.emailForwarding = (enum as? EmailForwarding) ?: return@onItemCheckedListener
            notYetImplemented()
        }
    }
}
