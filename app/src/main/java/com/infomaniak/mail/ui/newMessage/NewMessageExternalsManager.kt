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

import androidx.core.view.isGone
import com.infomaniak.mail.MatomoMail.trackExternalEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.alertDialogs.InformationAlertDialog
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipientForNewMessage
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject

@FragmentScoped
class NewMessageExternalsManager @Inject constructor() : NewMessageManager() {

    private var _informationDialog: InformationAlertDialog? = null
    private inline val informationDialog: InformationAlertDialog get() = _informationDialog!!

    fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        informationDialog: InformationAlertDialog,
    ) {
        super.initValues(newMessageViewModel, binding, fragment, freeReferences = {
            _informationDialog = null
        })

        _informationDialog = informationDialog
    }

    fun observeExternals(arrivedFromExistingDraft: Boolean) = with(newMessageViewModel) {
        Utils.waitInitMediator(initResult, mergedContacts).observe(viewLifecycleOwner) { (_, mergedContacts) ->
            val externalMailFlagEnabled = currentMailbox.externalMailFlagEnabled
            val shouldWarnForExternal = externalMailFlagEnabled && !arrivedFromExistingDraft
            val emailDictionary = mergedContacts.second
            val aliases = currentMailbox.aliases

            updateFields(shouldWarnForExternal, emailDictionary, aliases)
            updateBanner(shouldWarnForExternal, emailDictionary, aliases)
        }
    }

    private fun updateFields(shouldWarnForExternal: Boolean, emailDictionary: MergedContactDictionary, aliases: List<String>) {
        with(binding) {
            toField.updateExternals(shouldWarnForExternal, emailDictionary, aliases)
            ccField.updateExternals(shouldWarnForExternal, emailDictionary, aliases)
            bccField.updateExternals(shouldWarnForExternal, emailDictionary, aliases)
        }
    }

    private fun updateBanner(shouldWarnForExternal: Boolean, emailDictionary: MergedContactDictionary, aliases: List<String>) {
        with(newMessageViewModel) {
            if (shouldWarnForExternal && !isExternalBannerManuallyClosed) {
                val (externalEmail, externalQuantity) = draft.findExternalRecipientForNewMessage(aliases, emailDictionary)
                externalRecipientCount.value = externalEmail to externalQuantity
            }
        }
    }

    fun setupExternalBanner() = with(binding) {
        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        closeButton.setOnClickListener {
            context.trackExternalEvent("bannerManuallyClosed")
            newMessageViewModel.isExternalBannerManuallyClosed = true
            externalBanner.isGone = true
        }

        informationButton.setOnClickListener {
            context.trackExternalEvent("bannerInfo")

            val description = resources.getQuantityString(
                R.plurals.externalDialogDescriptionRecipient,
                externalRecipientQuantity,
                externalRecipientEmail,
            )

            informationDialog.show(
                title = R.string.externalDialogTitleRecipient,
                description = description,
                confirmButtonText = R.string.externalDialogConfirmButton,
            )
        }

        newMessageViewModel.externalRecipientCount.observe(viewLifecycleOwner) { (email, externalQuantity) ->
            externalBanner.isGone = newMessageViewModel.isExternalBannerManuallyClosed || externalQuantity == 0
            externalRecipientEmail = email
            externalRecipientQuantity = externalQuantity
        }
    }

    fun updateBannerVisibility() = with(binding) {
        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        listOf(toField, ccField, bccField).forEach { field ->
            val (singleEmail, quantityForThisField) = field.findAlreadyExistingExternalRecipientsInFields()
            externalRecipientQuantity += quantityForThisField

            if (externalRecipientQuantity > 1) {
                newMessageViewModel.externalRecipientCount.value = null to 2
                return
            }

            if (quantityForThisField == 1) externalRecipientEmail = singleEmail
        }

        newMessageViewModel.externalRecipientCount.value = externalRecipientEmail to externalRecipientQuantity
    }
}
