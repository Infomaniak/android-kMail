/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.mail.R
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.ceil

@Serializable
class Quotas : EmbeddedRealmObject {

    //region Remote data
    @SerialName("size")
    private var _size: Long = 0L
    //endregion

    //region Local data (Transient)
    @Transient
    var maxStorage: Long = 0L // 0 means unlimited
    //endregion

    val size: Long get() = _size * 1_024L // Convert from KiloOctets to Octets

    val isFull get() = getProgress() >= 100

    fun getText(context: Context): String {

        val usedSize = context.formatShortFileSize(size)
        val maxSize = context.formatShortFileSize(maxStorage)

        return context.getString(R.string.menuDrawerMailboxStorage, usedSize, maxSize)
    }

    fun getProgress(): Int = ceil(100.0f * size.toFloat() / maxStorage.toFloat()).toInt()

    companion object
}
