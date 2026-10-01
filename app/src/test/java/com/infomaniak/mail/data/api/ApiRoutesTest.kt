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
package com.infomaniak.mail.data.api

import com.infomaniak.mail.MAIL_API
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class ApiRoutesTest {

    @Test
    fun resource_withValidRelativePath_returnsExpectedUrl() {
        val validResource = "/api/mail/123/draft/456"
        val expected = "$MAIL_API$validResource"

        val result = ApiRoutes.resource(validResource)

        assertEquals(expected, result)
    }

    @Test
    fun resource_withUserinfoAttempt_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.resource("@attacker.example/capture")
        }
    }

    @Test
    fun resource_withSchemeRelativeUrl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.resource("//attacker.example/capture")
        }
    }

    @Test
    fun resource_withBackslashSchemeRelativeUrl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.resource("/\\attacker.example/capture")
        }
    }

    @Test
    fun resource_withAbsoluteUrl_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.resource("https://attacker.example/capture")
        }
    }

    @Test
    fun resource_withUrlEncodedUserinfo_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.resource("%40attacker.example/capture")
        }
    }

    @Test
    fun rescheduleDraft_withValidPath_succeeds() {
        val validResource = "/api/mail/123/draft/456"
        val dummyDate = Date(0L)

        val result = ApiRoutes.rescheduleDraft(validResource, dummyDate)

        assert(result.startsWith("$MAIL_API$validResource/schedule?schedule_date="))
    }

    @Test
    fun rescheduleDraft_withMaliciousPath_throwsIllegalArgumentException() {
        val maliciousResource = "@attacker.example/capture"
        val dummyDate = Date(0L)

        assertThrows(IllegalArgumentException::class.java) {
            ApiRoutes.rescheduleDraft(maliciousResource, dummyDate)
        }
    }
}
