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
import androidx.navigation.fragment.findNavController
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.userInfos.UserPreferencesController
import com.infomaniak.mail.data.models.user.UserPreferences.ListDensityMode
import com.infomaniak.mail.databinding.FragmentListDensitySettingBinding

class ListDensitySettingFragment : Fragment() {

    private lateinit var binding: FragmentListDensitySettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentListDensitySettingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupBack()
    }

    private fun setupUi() = with(binding) {
        val (checkedButtonId, resId) = getCheckedButtonFromDensity()
        listDensityButtonsGroup.check(checkedButtonId)
        listDensityImage.setImageResource(resId)
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

            val (listDensity, resId) = getDensityFromCheckedButton(buttonId)
            UserPreferencesController.updateUserPreferences { it.threadListDensity = listDensity.apiName }
            listDensityImage.setImageResource(resId)
        }
    }

    private fun getCheckedButtonFromDensity() = when (UserPreferencesController.getUserPreferences().getListDensityMode()) {
        ListDensityMode.COMPACT -> R.id.listDensityButtonCompact to R.drawable.bg_list_density_compact
        ListDensityMode.LARGE -> R.id.listDensityButtonLarge to R.drawable.bg_list_density_large
        else -> R.id.listDensityButtonNormal to R.drawable.bg_list_density_default
    }

    private fun getDensityFromCheckedButton(buttonId: Int) = when (buttonId) {
        R.id.listDensityButtonCompact -> ListDensityMode.COMPACT to R.drawable.bg_list_density_compact
        R.id.listDensityButtonLarge -> ListDensityMode.LARGE to R.drawable.bg_list_density_large
        else -> ListDensityMode.DEFAULT to R.drawable.bg_list_density_default
    }

    override fun onPause() {
        removeListeners()
        super.onPause()
    }

    private fun removeListeners() {
        binding.listDensityButtonsGroup.clearOnButtonCheckedListeners()
    }
}
