/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.utils

import okhttp3.internal.toHexString
import java.nio.charset.StandardCharsets

object Utils {

    val UTF_8: String = StandardCharsets.UTF_8.name()
    const val TEXT_HTML: String = "text/html"
    const val TEXT_PLAIN: String = "text/plain"
    /** The MIME type for data whose type is otherwise unknown. */
    const val MIMETYPE_UNKNOWN = "application/octet-stream"

    const val NUMBER_OF_OLD_MESSAGES_TO_FETCH = 500
    /** Beware: the API refuses a PAGE_SIZE bigger than 200. */
    const val PAGE_SIZE: Int = 50
    const val MAX_DELAY_BETWEEN_API_CALLS = 500L
    const val DELAY_BEFORE_FETCHING_ACTIVITIES_AGAIN = 500L

    fun colorToHexRepresentation(color: Int) = "#" + color.toHexString().substring(2 until 8)

    enum class MailboxErrorCode {
        NO_MAILBOX,
        NO_VALID_MAILBOX,
    }
}
