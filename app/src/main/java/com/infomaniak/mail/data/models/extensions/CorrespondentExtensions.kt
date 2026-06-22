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
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models.extensions

import android.content.Context
import com.infomaniak.core.auth.firstOrEmpty
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.getStartAndEndOfPlusEmail
import io.sentry.Sentry

fun Correspondent.isMe(): Boolean {
    val userEmail = AccountUtils.currentMailboxEmail?.lowercase()
    val correspondentEmail = email.lowercase()

    val isRealMe = userEmail == correspondentEmail
    if (isRealMe) return true

    val (start, end) = userEmail.getStartAndEndOfPlusEmail()
    val isPlusMe = correspondentEmail.startsWith(start) && correspondentEmail.endsWith(end)
    return isPlusMe
}

fun Correspondent.shouldDisplayUserAvatar(): Boolean = isMe() && email.lowercase() == AccountUtils.currentUser?.email?.lowercase()

val Correspondent.initials: String
    get() = cachedInitials ?: synchronized(this) {
        cachedInitials ?: computeInitials().also { cachedInitials = it }
    }

fun Correspondent.computeInitials(): String {
    return runCatching {
        val (firstName, lastName) = computeFirstAndLastName()
        val first = firstName.removeControlAndPunctuation().ifBlank { firstName }.first()
        val last = lastName.removeControlAndPunctuation().firstOrEmpty()

        return@runCatching "$first$last".uppercase()
    }.getOrElse { exception ->
        Sentry.captureException(exception) { scope ->
            scope.setExtra("email", email)
            scope.setExtra("name size", name.count().toString())
            scope.setExtra("name is blank", name.isBlank().toString())
            scope.setExtra("characters not letters", name.filterNot(Char::isLetter))
        }

        return@getOrElse ""
    }
}

fun Correspondent.computeFirstAndLastName(): Pair<String, String> {
    val words = getNameOrEmail().trim().replace(Regex("\\s+"), " ").split(" ", limit = 2)

    return when (words.count()) {
        0 -> "" to ""
        1 -> words.single() to ""
        else -> words.first() to words.last()
    }
}

fun Correspondent.displayedName(context: Context): String {
    return if (isMe()) context.getString(R.string.contactMe) else getNameOrEmail()
}

private fun String.removeControlAndPunctuation() = replace(Regex("\\p{Punct}|\\p{C}"), "")
