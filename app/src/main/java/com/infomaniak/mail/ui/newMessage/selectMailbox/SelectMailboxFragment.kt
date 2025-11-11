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
package com.infomaniak.mail.ui.newMessage.selectMailbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.mail.ui.theme.MailTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectMailboxFragment : Fragment() {
    private val selectMailboxViewModel: SelectMailboxViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MailTheme {
                    SelectMailboxScreen(
                        viewModel = selectMailboxViewModel,
                        onNavigationTopbarClick = { activity?.onBackPressedDispatcher?.onBackPressed() },
                        onContinue = { selectedMailbox ->
                            val direction = SelectMailboxFragmentDirections.actionSelectMailboxFragmentToNewMessageFragment(
                                userId = selectedMailbox.userId,
                                mailboxId = selectedMailbox.mailboxUi.mailboxId
                            )
                            safelyNavigate(direction)
                        }
                    )
                }
            }
        }
    }
}
