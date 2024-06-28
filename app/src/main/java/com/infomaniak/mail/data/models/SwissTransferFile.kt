/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import io.realm.kotlin.types.EmbeddedRealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.util.UUID

@Serializable
class SwissTransferFile : EmbeddedRealmObject, Attachable {

    //region Remote data
    var uuid: String = ""
    override var name: String = ""
    override var size: Long = 0L
    @SerialName("type")
    override var mimeType: String = ""
    //endregion

    //region Local data (Transient)
    @Transient
    override var resource: String? = null
    @Transient
    override var localUuid: String = UUID.randomUUID().toString()
    //endregion

    override fun hasUsableCache(context: Context, file: File?, userId: Int, mailboxId: Int) = false

    override fun isInlineCachedFile(context: Context) = false

    override fun getCacheFile(context: Context, userId: Int, mailboxId: Int) = File("")

    fun initLocalValues(containerUuid: String) {
        resource = "/api/swisstransfer/containers/$containerUuid/files/$uuid"
    }
}
