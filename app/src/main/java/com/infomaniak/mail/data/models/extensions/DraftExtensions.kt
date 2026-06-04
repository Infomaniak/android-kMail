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

package com.infomaniak.mail.data.models.extensions

import com.infomaniak.core.common.utils.enumValueOfOrNull
import com.infomaniak.core.network.api.ApiController
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.draft.DraftAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

var Draft.action
    get() = enumValueOfOrNull<DraftAction>(_action)
    set(value) {
        _action = value?.apiCallValue
    }

fun Draft.getJsonRequestBody(): MutableMap<String, JsonElement> {
    return draftJson.encodeToJsonElement(this).jsonObject.toMutableMap().apply {
        this[Draft::attachments.name] = JsonArray(attachments.map { JsonPrimitive(it.uuid) })
    }
}

private val draftJson = Json(ApiController.json) { encodeDefaults = true }
