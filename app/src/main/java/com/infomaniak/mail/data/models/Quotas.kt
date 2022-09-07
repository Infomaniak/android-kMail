/*
 * Infomaniak kMail - Android
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
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.ceil

@Serializable
class Quotas : RealmObject {

    //region API data
    var size: Int = 0
    @SerialName("size_checked_at")
    var sizeCheckedAt: Long = 0L
    //endregion

    //region Local data (Transient)
    @Transient
    @PrimaryKey
    var mailboxObjectId: String = ""
    //endregion

    fun getText(context: Context, maxSize: Long): String {
        val usedSize = size.toLong()

        val formattedUsedSize = FormatterFileSize.formatShortFileSize(context, usedSize)
        val formattedMaxSize = FormatterFileSize.formatShortFileSize(context, maxSize)

        return context.getString(R.string.menuDrawerMailboxStorage, formattedUsedSize, formattedMaxSize)
    }

    fun getProgress(maxSize: Long): Int {
        val usedSize = size.toLong()

        return ceil(100.0f * usedSize.toFloat() / maxSize.toFloat()).toInt()
    }
}
