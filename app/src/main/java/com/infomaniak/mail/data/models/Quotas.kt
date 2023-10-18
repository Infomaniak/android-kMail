/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.models

import android.content.Context
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.mail.R
import io.realm.kotlin.types.EmbeddedRealmObject
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
class Quotas : EmbeddedRealmObject {

    @SerialName("size")
    private var _size: Int = 0

    private val size: Long
        get() = _size.toLong() * FormatterFileSize.KIBI_BYTE.toLong()

    fun getText(context: Context, email: String?): String {

        val formattedUsedSize = FormatterFileSize.formatShortFileSize(context, size)
        val formattedMaxSize = FormatterFileSize.formatShortFileSize(context, QUOTAS_MAX_SIZE)

        // TODO: Remove this Sentry when we are sure the fix is the right one.
        if (_size < 0 || size < 0L || formattedUsedSize.firstOrNull() == '-') {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("1. mailbox", "$email")
                scope.setExtra("2. raw size", "$_size")
                scope.setExtra("3. display size", formattedUsedSize)
                scope.setExtra("4. raw maxSize", "$QUOTAS_MAX_SIZE")
                scope.setExtra("5. display maxSize", formattedMaxSize)
                Sentry.captureMessage("Quotas: Something is negative when trying to display")
            }
        }

        return context.getString(R.string.menuDrawerMailboxStorage, formattedUsedSize, formattedMaxSize)
    }

    fun getProgress(): Int = ceil(100.0f * size.toFloat() / QUOTAS_MAX_SIZE.toFloat()).toInt()

    companion object {
        private const val QUOTAS_MAX_SIZE = 21_474_836_480L // TODO: Get this value from API?
    }
}
