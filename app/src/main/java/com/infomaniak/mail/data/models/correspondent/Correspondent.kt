/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.correspondent

import android.content.Context
import com.infomaniak.lib.core.utils.firstOrEmpty
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.AccountUtils
import io.sentry.Sentry

interface Correspondent {
    var email: String
    var name: String

    val initials: String

    fun isMe(): Boolean = AccountUtils.currentMailboxEmail == email

    fun shouldDisplayUserAvatar(): Boolean = isMe() && email == AccountUtils.currentUser?.email

    fun getNameOrEmail(): String = name.ifBlank { email }

    fun computeInitials(): String {
        return runCatching {
            val (firstName, lastName) = computeFirstAndLastName()
            val first = firstName.removeControlAndPunctuation().ifBlank { firstName }.first()
            val last = lastName.removeControlAndPunctuation().firstOrEmpty()

            return@runCatching "$first$last".uppercase()
        }.getOrElse { exception ->
            Sentry.withScope { scope ->
                scope.setExtra("email", email)
                scope.setExtra("name", name)
                Sentry.captureException(exception)
            }

            return@getOrElse ""
        }
    }

    fun computeFirstAndLastName(): Pair<String, String> {
        val words = getNameOrEmail().trim().replace(Regex("\\s+"), " ").split(" ", limit = 2)

        return when (words.count()) {
            0 -> "" to ""
            1 -> words.single() to ""
            else -> words.first() to words.last()
        }
    }

    private fun String.removeControlAndPunctuation() = replace(Regex("\\p{Punct}|\\p{C}"), "")

    fun displayedName(context: Context): String = if (isMe()) context.getString(R.string.contactMe) else getNameOrEmail()
}
