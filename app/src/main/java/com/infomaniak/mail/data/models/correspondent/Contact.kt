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
@file:UseSerializers(RealmListKSerializer::class)

package com.infomaniak.mail.data.models.correspondent

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Contact(
    val name: String = "",
    val emails: RealmList<String> = realmListOf(),
    val avatar: String? = null,
)
