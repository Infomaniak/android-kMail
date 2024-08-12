/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings.appearance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ThreadDensity.*
import com.infomaniak.mail.databinding.FragmentThreadListDensitySettingBinding
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ThreadListDensitySettingFragment : Fragment() {

    private var binding: FragmentThreadListDensitySettingBinding by safeBinding()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadListDensitySettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        initUi()
    }

    private fun initUi() = with(binding) {
        val (checkedButtonId, resId) = getCheckedButtonFromDensity()
        listDensityButtonsGroup.check(checkedButtonId)
        listDensityImage.setImageResource(resId)
    }

    private fun getCheckedButtonFromDensity() = when (localSettings.threadDensity) {
        COMPACT -> R.id.listDensityButtonCompact to R.drawable.bg_list_density_compact
        LARGE -> R.id.listDensityButtonLarge to R.drawable.bg_list_density_large
        else -> R.id.listDensityButtonNormal to R.drawable.bg_list_density_default
    }

    override fun onResume() {
        super.onResume()
        addListeners()
    }

    private fun addListeners() = with(binding) {
        listDensityButtonsGroup.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val (listDensity, resId) = getDensityFromCheckedButton(buttonId)
            localSettings.threadDensity = listDensity
            listDensityImage.setImageResource(resId)

            trackEvent("settingsDensity", listDensity.toString())
        }
    }

    private fun getDensityFromCheckedButton(buttonId: Int) = when (buttonId) {
        R.id.listDensityButtonCompact -> COMPACT to R.drawable.bg_list_density_compact
        R.id.listDensityButtonLarge -> LARGE to R.drawable.bg_list_density_large
        else -> NORMAL to R.drawable.bg_list_density_default
    }

    override fun onPause() {
        removeListeners()
        super.onPause()
    }

    private fun removeListeners() {
        binding.listDensityButtonsGroup.clearOnButtonCheckedListeners()
    }
}
