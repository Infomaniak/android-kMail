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
package com.infomaniak.mail.data.models.extensions

import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.utils.ExternalUtils.ExternalData
import com.infomaniak.mail.utils.extensions.isEmail

// Computes if the Recipient is external, according to the required conditions.
// Does not tell anything about how to display the Recipient chip when composing a new Message.
fun Recipient.isExternal(externalData: ExternalData): Boolean = with(externalData) {
    val isUnknownContact = email !in emailDictionary
    val isAlias = email in aliases
    val isUntrustedDomain = email.isEmail() && trustedDomains.none(email::endsWith)
    val isMailerDaemon = """mailer-daemon@(?:.+\.)?infomaniak\.ch""".toRegex(RegexOption.IGNORE_CASE).matches(email)

    return@with isUnknownContact && !isAlias && isUntrustedDomain && !isMailerDaemon
}

fun Recipient.Companion.createValidRecipientOrNull(
    email: String,
    name: String? = null,
    hasExternalProvider: Boolean? = null
): Recipient? {
    if (!email.isEmail()) return null
    return createValidRecipient(
        syntacticallyValidEmail = email,
        name = name,
        hasExternalProvider = hasExternalProvider
    )
}
