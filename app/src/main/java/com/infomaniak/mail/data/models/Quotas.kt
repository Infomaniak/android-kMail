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

    private val size: Int
        get() = _size * FormatterFileSize.KIBI_BYTE

    fun getSizeForSentry() = _size

    fun getText(context: Context, email: String?): String {
        val formattedUsedSize = FormatterFileSize.formatShortFileSize(context, size.toLong())
        val formattedMaxSize = FormatterFileSize.formatShortFileSize(context, QUOTAS_MAX_SIZE)

        if (_size < 0 || formattedUsedSize.toLong() < 0) {
            Sentry.withScope {
                it.level = SentryLevel.WARNING
                it.setExtra("mailbox", "$email")
                it.setExtra("quotas raw size", "$_size")
                it.setExtra("quotas display size", formattedUsedSize)
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
