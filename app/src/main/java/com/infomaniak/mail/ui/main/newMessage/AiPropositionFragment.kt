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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.ShowAiReplacementDialog
import com.infomaniak.mail.databinding.DialogAiReplaceContentBinding
import com.infomaniak.mail.databinding.FragmentAiPropositionBinding
import com.infomaniak.mail.utils.changeToolbarColorOnScroll
import com.infomaniak.mail.utils.postfixWithTag
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AiPropositionFragment : Fragment() {

    private lateinit var binding: FragmentAiPropositionBinding
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val aiViewModel: AiViewModel by activityViewModels()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAiPropositionBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setToolbar()

        aiViewModel.aiProposition.observe(viewLifecycleOwner) { proposition ->
            propositionTextView.text = proposition?.second
        }

        insertPropositionButton.setOnClickListener {
            val doNotAskAgain = localSettings.showAiReplacementDialog == ShowAiReplacementDialog.HIDE
            if (doNotAskAgain || newMessageViewModel.draft.uiBody.isBlank()) {
                choosePropositionAndBack()
            } else {
                createReplaceContentDialog { choosePropositionAndBack() }.show()
            }
        }
    }

    private fun setToolbar() = with(binding) {
        changeToolbarColorOnScroll(toolbar, nestedScrollView)
        toolbar.apply {
            setNavigationOnClickListener { findNavController().popBackStack() }
            title = requireContext().postfixWithTag(
                getString(R.string.aiPromptTitle),
                R.string.aiPromptTag,
                R.color.aiBetaTagBackground,
                R.color.aiBetaTagTextColor
            )
        }
    }

    private fun choosePropositionAndBack() {
        aiViewModel.aiOutputToInsert.value = aiViewModel.aiProposition.value!!.second
        findNavController().popBackStack()
    }

    private fun Fragment.createReplaceContentDialog(
        onPositiveButtonClicked: () -> Unit,
    ) = with(DialogAiReplaceContentBinding.inflate(layoutInflater)) {
        dialogDescriptionLayout.apply {
            dialogTitle.text = getString(R.string.aiReplacementDialogTitle)
            dialogDescription.text = getString(R.string.aiReplacementDialogDescription)
        }

        checkbox.apply {
            isChecked = localSettings.showAiReplacementDialog == ShowAiReplacementDialog.HIDE
            setOnCheckedChangeListener { _, isChecked ->
                localSettings.showAiReplacementDialog = if (isChecked) {
                    ShowAiReplacementDialog.HIDE
                } else {
                    ShowAiReplacementDialog.SHOW
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.AiCursorAndPrimaryColorTheme)
            .setView(root)
            .setPositiveButton(R.string.aiReplacementDialogPositiveButton) { _, _ -> onPositiveButtonClicked() }
            .setNegativeButton(com.infomaniak.lib.core.R.string.buttonCancel, null)
            .create()
    }
}
