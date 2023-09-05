/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
// @file:UseSerializers(RealmListSerializer::class, RealmInstantSerializer::class)

package com.infomaniak.mail.data.models.message

import com.infomaniak.mail.utils.MessageBodyUtils.SplitBody
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
class Body : EmbeddedRealmObject {

    //region Remote data
    var value: String = ""
    var type: String = ""

    // TODO: Realm doesn't allow us to do that for now:
    //  | Caused by: io.realm.kotlin.internal.interop.RealmCoreLogicException: [18]: Schema validation failed due to the following errors:
    //  | - Cycles containing embedded objects are not currently supported: 'Body.subBody.body'
    //  | - Cycles containing embedded objects are not currently supported: 'SubBody.body.subBody'
    // var subBody: RealmList<SubBody> = realmListOf()
    // TODO: In the meantime, we store the `subBody` as a JSON String, and we'll have to manually deserialize it when we want to use it.
    @Serializable(JsonAsStringSerializer::class)
    var subBody: String? = null
    //endregion

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    var splitBody: SplitBody? = null
    //endregion
}

// @Serializable
// class SubBody : EmbeddedRealmObject {
//     var body: Body? = null
//     var name: String? = null
//     var type: String? = null
//     var date: RealmInstant = Date().toRealmInstant()
//     var subject: String? = null
//     var from: RealmList<Recipient> = realmListOf()
//     var to: RealmList<Recipient> = realmListOf()
//     @SerialName("part_id")
//     var partId: String? = null
// }

// Documentation: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-transforming-serializer/
object JsonAsStringSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = JsonPrimitive(element.toString())
}
