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

package com.infomaniak.mail.data.models

import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.ceil

@Serializable
class Quotas : EmbeddedRealmObject {

    //region Remote data
    @SerialName("size")
    var size: Long = 0L
    //endregion

    //region Local data (Transient)
    @Transient
    @InternalModelProperties
    var maxStorage: Long? = null
    //endregion

    val isFull: Boolean
        get() {
            val progress = getProgress()
            return progress != null && progress >= 100
        }

    /**
     * Stores the max storage data at the quota level to avoid having to access
     * a mailbox instance everytime we want to compute the progress of a quota.
     */
    fun initMaxStorage(maxStorage: Long?) {
        this.maxStorage = maxStorage
    }

    fun getProgress(): Int? = maxStorage?.let { ceil(100.0f * size.toFloat() / it.toFloat()).toInt() }
    fun hasLimitedStorage(): Boolean = maxStorage?.takeIf { it > 0L } != null

    companion object
}
