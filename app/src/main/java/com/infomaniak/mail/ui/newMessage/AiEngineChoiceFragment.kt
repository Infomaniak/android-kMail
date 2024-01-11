/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentAiEngineChoiceBinding
import com.infomaniak.mail.databinding.LayoutAiEngineChoiceBinding
import com.infomaniak.mail.utils.SharedUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AiEngineChoiceFragment : Fragment() {

    private var binding: FragmentAiEngineChoiceBinding by safeBinding()
    private var choiceBinding: LayoutAiEngineChoiceBinding by safeBinding()

    @Inject
    lateinit var sharedUtils: SharedUtils

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAiEngineChoiceBinding.inflate(inflater, container, false)
        choiceBinding = LayoutAiEngineChoiceBinding.bind(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = requireContext().getColor(R.color.backgroundColor)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        sharedUtils.manageAiEngineSettings(this, choiceBinding.radioGroup, "promptAiEngine") {
            findNavController().popBackStack()
        }
    }
}
