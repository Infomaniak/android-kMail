/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import com.infomaniak.lib.core.utils.SentryLog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SnoozeUuidSerializer : KSerializer<String?> {

    private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val lastUuidRegex = Regex("""$UUID_PATTERN(?!.*$UUID_PATTERN)""", RegexOption.IGNORE_CASE)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SnoozeActionUuid", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val snoozeAction = decoder.decodeString()
        return snoozeAction.lastUuidOrNull().also {
            if (it == null) SentryLog.e(TAG, "Could not deserialize a snooze uuid")
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        // Should never happen, we cannot recreate the full resource and this is not needed anyway. We never send an object with
        // a snoozeUuid to the api.
        SentryLog.e(TAG, "Tried to serialize a snooze uuid")
        value?.let { encoder.encodeString(it) }
    }

    fun String.lastUuidOrNull() = lastUuidRegex.find(this)?.value

    private val TAG = SnoozeUuidSerializer::class.java.simpleName
}
