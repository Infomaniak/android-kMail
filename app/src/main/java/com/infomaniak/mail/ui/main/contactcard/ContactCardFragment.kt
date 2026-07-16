/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.contactcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.auth.models.user.Card
import com.infomaniak.core.ui.compose.contactcard.ContactCardScreen
import com.infomaniak.core.ui.compose.contactcard.R
import com.infomaniak.core.ui.compose.contactcard.shareContactCard
import com.infomaniak.core.ui.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.infomaniak.mail.R as RMail

@AndroidEntryPoint
class ContactCardFragment : Fragment() {

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialThemeFromXml {
                    ContactCardScreen(
                        onBack = { findNavController().popBackStack() },
                        onShare = ::shareCard,
                        confirmDelete = ::confirmDelete,
                    )
                }
            }
        }
    }

    private fun shareCard(card: Card) {
        lifecycleScope.launch {
            requireContext().shareContactCard(card)
        }
    }

    private fun confirmDelete(onConfirmed: () -> Unit) {
        descriptionDialog.show(
            title = getString(R.string.deleteAlertTitle),
            description = getString(R.string.deleteAlertDescription),
            displayLoader = false,
            positiveButtonText = RMail.string.actionDelete,
            onPositiveButtonClicked = { onConfirmed() },
        )
    }
}
