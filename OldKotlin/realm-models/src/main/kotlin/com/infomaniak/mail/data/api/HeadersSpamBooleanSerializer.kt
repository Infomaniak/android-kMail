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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object HeadersSpamBooleanSerializer : KSerializer<Boolean> {

    private const val IS_SPAM_HEADER_VALUE = "spam"
    private const val IS_NOT_SPAM_HEADER_VALUE = "ham"

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("String", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) = decoder.decodeString() == IS_SPAM_HEADER_VALUE

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeString(if (value) IS_SPAM_HEADER_VALUE else IS_NOT_SPAM_HEADER_VALUE)
    }
}
