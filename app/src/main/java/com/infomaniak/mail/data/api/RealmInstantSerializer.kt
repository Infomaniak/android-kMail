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
package com.infomaniak.mail.data.api

import com.infomaniak.mail.utils.toDate
import com.infomaniak.mail.utils.toRealmInstant
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.*

object RealmInstantSerializer : KSerializer<RealmInstant?> {

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RealmInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RealmInstant?) {
        value?.toDate()?.let(simpleDateFormat::format)?.let(encoder::encodeString)
    }

    override fun deserialize(decoder: Decoder): RealmInstant? {
        return simpleDateFormat.parse(decoder.decodeString())?.toRealmInstant()
    }
}
