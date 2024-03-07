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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.extensions.MergedContactDictionary

object ExternalUtils {

    /**
     * Only returns a quantity of at most 2, used to differentiate between the singular or plural form of the dialog messages
     */
    fun Thread.findExternalRecipients(externalData: ExternalData): Pair<String?, Int> {
        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        messages.forEach { message ->
            val (singleEmail, quantityForThisMessage) = findExternalRecipientInIterables(externalData, message.from)

            externalRecipientQuantity += quantityForThisMessage
            if (externalRecipientQuantity > 1) return null to 2

            if (quantityForThisMessage == 1) externalRecipientEmail = singleEmail
        }

        return externalRecipientEmail to externalRecipientQuantity
    }

    fun Draft.findExternalRecipientForNewMessage(externalData: ExternalData): Pair<String?, Int> {
        val to = to.onlyAutomaticallyAddedOnes()
        val cc = cc.onlyAutomaticallyAddedOnes()
        val bcc = bcc.onlyAutomaticallyAddedOnes()

        return findExternalRecipientInIterables(externalData, to, cc, bcc)
    }

    private fun List<Recipient>.onlyAutomaticallyAddedOnes(): List<Recipient> = filter { !it.isManuallyEntered }

    /**
     * Only returns a quantity of at most 2, used to differentiate between the singular or plural form of the dialog messages
     */
    private fun findExternalRecipientInIterables(
        externalData: ExternalData,
        vararg recipientLists: Iterable<Recipient>,
    ): Pair<String?, Int> {
        var externalRecipientEmail: String? = null
        var externalRecipientQuantity = 0

        recipientLists.forEach { recipientList ->
            recipientList.forEach { recipient ->
                if (recipient.isExternal(externalData)) {
                    if (externalRecipientQuantity++ == 0) {
                        externalRecipientEmail = recipient.email
                    } else {
                        return null to 2
                    }
                }
            }
        }

        return externalRecipientEmail to externalRecipientQuantity
    }

    data class ExternalData(
        val emailDictionary: MergedContactDictionary,
        val aliases: List<String>,
        val trustedDomains: List<String>,
    )
}
