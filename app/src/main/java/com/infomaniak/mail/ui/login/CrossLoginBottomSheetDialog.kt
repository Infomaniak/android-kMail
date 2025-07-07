/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetCrossLoginBinding
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import com.infomaniak.mail.utils.uiAccounts

class CrossLoginBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: BottomSheetCrossLoginBinding by safeBinding()
    private val introViewModel: IntroViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetCrossLoginBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(statusBarColor = R.color.backgroundColor)

        observeAccentColor()
        observeCrossLoginAccounts()
        observeCrossLoginSelectedIds()
        setCrossLoginClicksListeners()
    }

    private fun observeAccentColor() {
        introViewModel.updatedAccentColor.observe(viewLifecycleOwner) { (newAccentColor, _) ->
            binding.crossLoginBottomSheet.setPrimaryColor(newAccentColor.getPrimary(requireContext()))
            binding.crossLoginBottomSheet.setOnPrimaryColor(newAccentColor.getOnPrimary(requireContext()))
        }
    }

    private fun observeCrossLoginAccounts() {
        introViewModel.crossLoginAccounts.observe(viewLifecycleOwner) { accounts ->
            binding.crossLoginBottomSheet.setAccounts(accounts.uiAccounts())
        }
    }

    private fun observeCrossLoginSelectedIds() {
        introViewModel.crossLoginSelectedIds.observe(viewLifecycleOwner) { ids ->
            binding.crossLoginBottomSheet.setSelectedIds(ids)
        }
    }

    private fun setCrossLoginClicksListeners() {

        binding.crossLoginBottomSheet.setOnAnotherAccountClickedListener {
            parentFragmentManager.setFragmentResult(
                /* requestKey = */ ON_ANOTHER_ACCOUNT_CLICKED_KEY,
                /* result = */ Bundle().apply { putString(ON_ANOTHER_ACCOUNT_CLICKED_KEY, "") },
            )
            findNavController().popBackStack()
        }

        binding.crossLoginBottomSheet.setOnSaveClickedListener { selectedIds ->
            introViewModel.crossLoginSelectedIds.value = selectedIds
            findNavController().popBackStack()
        }
    }

    companion object {
        const val ON_ANOTHER_ACCOUNT_CLICKED_KEY = "onAnotherAccountClickedKey"
    }
}
