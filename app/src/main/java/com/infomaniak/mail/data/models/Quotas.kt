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
import android.text.format.Formatter
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
    private var _size: Long = 0L

    private val size: Long
        get() {
            val converted = _size * 1_000L / 1_024L // Convert from binary units to decimal units
            return converted * 1_000L // Convert from KiloOctets to Octets
        }

    fun getText(context: Context, email: String?): String {

        val usedSize = Formatter.formatShortFileSize(context, size)
        val maxSize = Formatter.formatShortFileSize(context, QUOTAS_MAX_SIZE)

        // TODO: Remove this Sentry when we are sure the fix is the right one.
        if (_size < 0L || size < 0L || usedSize.firstOrNull() == '-') {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("1. mailbox", "$email")
                scope.setExtra("2. raw size", "$_size")
                scope.setExtra("3. display size", usedSize)
                scope.setExtra("4. raw maxSize", "$QUOTAS_MAX_SIZE")
                scope.setExtra("5. display maxSize", maxSize)
                Sentry.captureMessage("Quotas: Something is negative when trying to display")
            }
        }

        return context.getString(R.string.menuDrawerMailboxStorage, usedSize, maxSize)
    }

    fun getProgress(): Int = ceil(100.0f * size.toFloat() / QUOTAS_MAX_SIZE.toFloat()).toInt()

    companion object {
        private const val QUOTAS_MAX_SIZE = 20_000_000_000L // TODO: Get this value from API?
    }
}
