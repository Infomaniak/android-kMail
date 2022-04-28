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
package com.infomaniak.mail.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.realm.RealmInstant
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter to convert API ISO [String] to/from [RealmInstant] when [com.google.gson.Gson] deserializes/serializes a model.
 */
class RealmInstantConverter : TypeAdapter<RealmInstant>() {

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    /**
     * Write [RealmInstant] field as a ISO [String] or null
     */
    override fun write(out: JsonWriter?, date: RealmInstant?) {
        date?.let { realmInstant -> out?.value(sdf.format(realmInstant.toDate())) } ?: out?.nullValue()
    }

    /**
     * Read ISO [String] field as a [RealmInstant] or null
     */
    override fun read(timestamp: JsonReader?): RealmInstant? {
        return timestamp?.let {
            if (it.peek() === JsonToken.STRING) {
                sdf.parse(it.nextString())?.toRealmInstant()
            } else {
                it.nextNull()
                null
            }
        }
    }
}
