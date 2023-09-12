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
package com.infomaniak.mail.ui.main.newMessage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.databinding.FragmentAiPromptBinding

class AiPromptFragment : Fragment() {

    private lateinit var binding: FragmentAiPromptBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPromptBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        prompt.apply {
            showKeyboard()
            doAfterTextChanged {
                generateButton.isEnabled = it?.isNotEmpty() ?: false
            }
        }
        closeButton.setOnClickListener {
            (parentFragment as NewMessageFragment).closeAiPrompt()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.prompt.setText("")
    }

    override fun onDestroyView() {
        binding.prompt.hideKeyboard()
        super.onDestroyView()
    }
}
