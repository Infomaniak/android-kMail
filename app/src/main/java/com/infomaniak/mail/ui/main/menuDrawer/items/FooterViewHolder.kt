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
package com.infomaniak.mail.ui.main.menuDrawer.items

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.databinding.ItemMenuDrawerFooterBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder

class FooterViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
) : MenuDrawerViewHolder(ItemMenuDrawerFooterBinding.inflate(inflater, parent, false)) {

    override val binding = super.binding as ItemMenuDrawerFooterBinding

    @SuppressLint("SetTextI18n")
    fun displayFooter(
        footer: MenuDrawerFooter,
        onFeedbackClicked: () -> Unit,
        onHelpClicked: () -> Unit,
        onAppVersionClicked: () -> Unit,
    ) = with(binding) {
        SentryLog.d("Bind", "Bind Footer")

        // Feedback
        feedback.setOnClickListener { onFeedbackClicked() }

        // Help
        help.setOnClickListener { onHelpClicked() }

        // Quotas
        val isLimited = footer.quotas != null
        storageLayout.isVisible = isLimited
        storageDivider.isVisible = isLimited
        if (isLimited) {
            storageText.text = footer.quotas.getText(context)
            storageIndicator.progress = footer.quotas.getProgress() ?: 0
        }

        // App version
        appVersionName.apply {
            text = "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setOnClickListener { onAppVersionClicked() }
        }
    }

    data class MenuDrawerFooter(val quotas: Quotas?)
}
