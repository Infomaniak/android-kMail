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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.context
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.databinding.ItemMenuDrawerFooterBinding

object FooterItem {

    @Suppress("MayBeConstant")
    val viewType = R.layout.item_menu_drawer_footer

    fun binding(inflater: LayoutInflater, parent: ViewGroup): ViewBinding {
        return ItemMenuDrawerFooterBinding.inflate(inflater, parent, false)
    }

    fun display(
        item: Any,
        binding: ViewBinding,
        onSyncAutoConfigClicked: () -> Unit,
        onImportMailsClicked: () -> Unit,
        onRestoreMailsClicked: () -> Unit,
        onFeedbackClicked: () -> Unit,
        onHelpClicked: () -> Unit,
        onAppVersionClicked: () -> Unit,
    ) {
        item as MenuDrawerFooter
        binding as ItemMenuDrawerFooterBinding

        SentryLog.d("Bind", "Bind Footer")
        binding.displayFooter(
            item,
            onSyncAutoConfigClicked,
            onImportMailsClicked,
            onRestoreMailsClicked,
            onFeedbackClicked,
            onHelpClicked,
            onAppVersionClicked,
        )
    }

    private fun ItemMenuDrawerFooterBinding.displayFooter(
        footer: MenuDrawerFooter,
        onSyncAutoConfigClicked: () -> Unit,
        onImportMailsClicked: () -> Unit,
        onRestoreMailsClicked: () -> Unit,
        onFeedbackClicked: () -> Unit,
        onHelpClicked: () -> Unit,
        onAppVersionClicked: () -> Unit,
    ) {

        // Actions header
        advancedActions.setOnClickListener {
            context.trackMenuDrawerEvent("advancedActions", value = (!advancedActions.isCollapsed).toFloat())
            advancedActionsLayout.isGone = advancedActions.isCollapsed
        }

        // Calendar & contacts sync
        syncAutoConfig.setOnClickListener { onSyncAutoConfigClicked() }

        // Import mails
        importMails.setOnClickListener { onImportMailsClicked() }

        // Restore mails
        restoreMails.apply {
            isVisible = footer.permissions?.canRestoreEmails == true
            setOnClickListener { onRestoreMailsClicked() }
        }

        // Feedback
        feedback.setOnClickListener { onFeedbackClicked() }

        // Help
        help.setOnClickListener { onHelpClicked() }

        // Quotas
        val isLimited = footer.quotas != null
        storageLayout.isVisible = isLimited
        storageDivider.isVisible = isLimited
        if (isLimited) {
            storageText.text = footer.quotas!!.getText(context)
            storageIndicator.progress = footer.quotas.getProgress()
        }

        // App version
        appVersionName.apply {
            text = "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setOnClickListener { onAppVersionClicked() }
        }
    }

    data class MenuDrawerFooter(val permissions: MailboxPermissions?, val quotas: Quotas?)
}
