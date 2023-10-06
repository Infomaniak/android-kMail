/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import com.infomaniak.mail.data.models.message.SubBody
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmList
import kotlinx.serialization.json.*

// Documentation: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-transforming-serializer/
object FlatteningSubBodiesSerializer : JsonTransformingSerializer<RealmList<SubBody>>(
    RealmListKSerializer(SubBody.serializer()),
) {

    override fun transformDeserialize(element: JsonElement): JsonElement = element.jsonArray.flattenSubBodies()

    // TODO: Implement this if we need to send `subBodies` back to the API.
    override fun transformSerialize(element: JsonElement): JsonElement = super.transformSerialize(element)

    private fun List<JsonElement>.flattenSubBodies(): JsonElement {

        if (isEmpty()) return JsonArray(emptyList())

        tailrec fun formatSubBodyWithAllChildren(
            inputList: MutableList<JsonElement>,
            outputList: MutableList<JsonElement> = mutableListOf(),
        ): List<JsonElement> {

            val remoteSubBody = inputList.removeFirst() as? JsonObject
            val remoteBody = remoteSubBody?.get("body") as? JsonObject
            val remoteSubBodies = remoteBody?.get("subBody") as? JsonArray

            val subBody = JsonObject(
                content = mutableMapOf<String, JsonElement>().apply {
                    remoteSubBody?.get("name")?.let { put("name", it) }
                    remoteSubBody?.get("type")?.let { put("type", it) }
                    remoteSubBody?.get("date")?.let { put("date", it) }
                    remoteSubBody?.get("subject")?.let { put("subject", it) }
                    remoteSubBody?.get("from")?.let { put("from", it) }
                    remoteSubBody?.get("to")?.let { put("to", it) }
                    remoteSubBody?.get("part_id")?.let { put("part_id", it) }
                    remoteBody?.get("value")?.let { put("bodyValue", it) }
                    remoteBody?.get("type")?.let { put("bodyType", it) }
                },
            )

            outputList.add(subBody)
            remoteSubBodies?.let { children ->
                if (children.isNotEmpty()) inputList.addAll(0, children)
            }

            return if (inputList.isEmpty()) outputList else formatSubBodyWithAllChildren(inputList, outputList)
        }

        return JsonArray(formatSubBodyWithAllChildren(toMutableList()))
    }
}
