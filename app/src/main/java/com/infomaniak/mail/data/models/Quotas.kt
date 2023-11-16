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

    fun getText(context: Context): String {

        val usedSize = Formatter.formatShortFileSize(context, size)
        val maxSize = Formatter.formatShortFileSize(context, QUOTAS_MAX_SIZE)

        return context.getString(R.string.menuDrawerMailboxStorage, usedSize, maxSize)
    }

    fun getProgress(): Int = ceil(100.0f * size.toFloat() / QUOTAS_MAX_SIZE.toFloat()).toInt()

    companion object {
        private const val QUOTAS_MAX_SIZE = 20_000_000_000L // TODO: Get this value from API?
    }
}
