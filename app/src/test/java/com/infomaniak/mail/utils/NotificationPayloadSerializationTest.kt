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
package com.infomaniak.mail.utils

import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior
import com.infomaniak.mail.utils.NotificationPayload.NotificationBehavior.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.Base64

class NotificationPayloadSerializationTest {

    @Test
    fun notificationBehaviorFromUnminifiedReleaseCanBeRead() {
        // Serialized by the unminified app on Android before R8 was enabled.
        val encoded = """
            rO0ABXNyAEJjb20uaW5mb21hbmlhay5tYWlsLnV0aWxzLk5vdGlmaWNhdGlvblBheWxvYWQkTm90aWZpY2F0aW9uQmVoYXZpb3LPqHh2
            FzkQBwIABEwAD2JlaGF2aW9yQ29udGVudHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wAE2JlaGF2aW9yRGVzY3JpcHRpb25xAH4AAUwADWJl
            aGF2aW9yVGl0bGVxAH4AAUwABHR5cGV0AFVMY29tL2luZm9tYW5pYWsvbWFpbC91dGlscy9Ob3RpZmljYXRpb25QYXlsb2FkJE5vdGlm
            aWNhdGlvbkJlaGF2aW9yJE5vdGlmaWNhdGlvblR5cGU7eHB0AAdjb250ZW50dAALZGVzY3JpcHRpb250AAV0aXRsZX5yAFNjb20uaW5m
            b21hbmlhay5tYWlsLnV0aWxzLk5vdGlmaWNhdGlvblBheWxvYWQkTm90aWZpY2F0aW9uQmVoYXZpb3IkTm90aWZpY2F0aW9uVHlwZQAA
            AAAAAAAAEgAAeHIADmphdmEubGFuZy5FbnVtAAAAAAAAAAASAAB4cHQABFVORE8=
        """.filterNot(Char::isWhitespace)
        val behavior = ObjectInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use {
            it.readObject() as NotificationBehavior
        }

        assertEquals(NotificationType.UNDO, behavior.type)
        assertEquals("title", behavior.behaviorTitle)
        assertEquals("content", behavior.behaviorContent)
        assertEquals("description", behavior.behaviorDescription)
    }
}
