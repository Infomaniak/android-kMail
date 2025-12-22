/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail


import com.infomaniak.core.appversionchecker.data.models.AppPublishedVersion
import com.infomaniak.core.appversionchecker.data.models.AppVersion
import com.infomaniak.core.appversionchecker.data.models.AppVersion.Companion.compareVersionTo
import com.infomaniak.core.appversionchecker.data.models.AppVersion.Companion.toVersionNumbers
import com.infomaniak.core.sentry.SentryLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Tests for the [com.infomaniak.core.appversionchecker.data.models.AppVersion] methods to compare two App Versions
 */
class AppVersionCheckingTest {

    private val negativeVersion = "-1.0.2"
    private val smallVersion = "0.0.1"
    private val basicVersion = "1.0.1"
    private val basicBigDigitVersion = "1.0.9"
    private val basicNumberVersion = "1.0.10"
    private val mediumVersion = "1.2.0"
    private val mediumVersionShortFormat = "1.2"
    private val greatVersion = "1.2.2"
    private val greaterVersion = "1.3.0"
    private val invalidCommaVersion = "1.2,9"
    private val invalidParseVersion = "invalid_parse_version"
    private val invalidEmptyVersion = ""

    private val defaultVersionChannel = AppVersion.VersionChannel.Production

    private val defaultAppVersion = AppVersion(
        minimalAcceptedVersion = mediumVersion,
        publishedVersions = listOf(AppPublishedVersion(tag = greatVersion))
    )
    private val invalidMinimalAppVersion = AppVersion(
        minimalAcceptedVersion = mediumVersion,
        publishedVersions = listOf(AppPublishedVersion(tag = basicVersion))
    )
    private val invalidFormatAppVersion = AppVersion(
        minimalAcceptedVersion = invalidCommaVersion,
        publishedVersions = listOf(AppPublishedVersion(tag = basicVersion))
    )

    val sentryLog = mockk<SentryLog>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(SentryLog)
        every { SentryLog.e(any(), any(), any(), any()) } returns sentryLog.e("", "")
    }

    //region toVersionNumbers()
    @Test
    fun parseVersionNumber_defaultFormat() {
        Assert.assertEquals(listOf(1, 0, 1), basicVersion.toVersionNumbers())
    }

    @Test
    fun parseVersionNumber_negativeFormat() {
        Assert.assertEquals(listOf(-1, 0, 2), negativeVersion.toVersionNumbers())
    }

    @Test
    fun parseVersionNumber_numberFormat() {
        Assert.assertEquals(listOf(1, 0, 10), basicNumberVersion.toVersionNumbers())
    }

    @Test
    fun parseVersionNumber_invalidCommaFormat() {
        Assert.assertThrows(NumberFormatException::class.java) { invalidCommaVersion.toVersionNumbers() }
    }

    @Test
    fun parseVersionNumber_invalidStringFormat() {
        Assert.assertThrows(NumberFormatException::class.java) { invalidParseVersion.toVersionNumbers() }
    }

    @Test
    fun parseVersionNumber_invalidEmptyFormat() {
        Assert.assertThrows(NumberFormatException::class.java) { invalidEmptyVersion.toVersionNumbers() }
    }
    //endregion

    //region compareVersionTo
    @Test
    fun compareVersions_equalVersions() {
        compareVersion(0, basicVersion, basicVersion)
    }

    @Test
    fun compareVersions_olderVersions() {
        compareVersion(-1, basicVersion, greatVersion)
    }

    @Test
    fun compareVersions_newerVersions() {
        compareVersion(1, basicVersion, smallVersion)
    }

    @Test
    fun compareVersions_numberVersions() {
        compareVersion(-1, basicBigDigitVersion, basicNumberVersion)
    }

    @Test
    fun compareVersions_shortVersions() {
        compareVersion(-1, basicNumberVersion, mediumVersionShortFormat)
        compareVersion(0, mediumVersion, mediumVersionShortFormat)
        compareVersion(1, greatVersion, mediumVersionShortFormat)
        compareVersion(1, greaterVersion, mediumVersionShortFormat)

        compareVersion(1, mediumVersionShortFormat, basicNumberVersion)
        compareVersion(0, mediumVersionShortFormat, mediumVersion)
        compareVersion(-1, mediumVersionShortFormat, greatVersion)
    }

    @Test
    fun compareVersions_negativeVersions() {
        compareVersion(1, smallVersion, negativeVersion)
    }

    private fun compareVersion(expectedResult: Int, caller: String, other: String) {
        Assert.assertEquals(expectedResult, caller.toVersionNumbers().compareVersionTo(other.toVersionNumbers()))
    }
    //endregion

    //region isMinimalVersionValid
    @Test
    fun isMinimalVersionValid_correct() {
        val minimalAcceptedVersion = defaultAppVersion.minimalAcceptedVersion
        Assert.assertNotNull(minimalAcceptedVersion)
        if (minimalAcceptedVersion != null) {
            val minimalVersionNumbers = minimalAcceptedVersion.toVersionNumbers()
            Assert.assertTrue(defaultAppVersion.isMinimalVersionValid(minimalVersionNumbers))
            Assert.assertTrue(invalidMinimalAppVersion.isMinimalVersionValid(negativeVersion.toVersionNumbers()))
        }
    }

    @Test
    fun isMinimalVersionValid_wrongMinimalVersion() {
        val minimalAcceptedVersion = invalidMinimalAppVersion.minimalAcceptedVersion
        Assert.assertNotNull(minimalAcceptedVersion)
        if (minimalAcceptedVersion != null) {
            val minimalVersionNumbers = minimalAcceptedVersion.toVersionNumbers()
            Assert.assertFalse(invalidMinimalAppVersion.isMinimalVersionValid(minimalVersionNumbers))
        }
    }

    @Test
    fun isMinimalVersionValid_invalidMinimalVersions() {
        val invalidVersions = listOf(invalidCommaVersion, invalidParseVersion, invalidEmptyVersion)

        invalidVersions.forEach { version ->
            Assert.assertThrows(NumberFormatException::class.java) {
                defaultAppVersion.isMinimalVersionValid(version.toVersionNumbers())
            }
        }
    }
    //endregion

    //region mustRequireUpdate
    @Test
    fun mustRequireUpdate_newerVersion() {
        Assert.assertFalse(defaultAppVersion.mustRequireUpdate(greatVersion, defaultVersionChannel))
    }

    @Test
    fun mustRequireUpdate_sameVersion() {
        Assert.assertFalse(defaultAppVersion.mustRequireUpdate(mediumVersion, defaultVersionChannel))
        Assert.assertFalse(defaultAppVersion.mustRequireUpdate(mediumVersionShortFormat, defaultVersionChannel))
    }

    @Test
    fun mustRequireUpdate_olderVersion() {
        Assert.assertTrue(defaultAppVersion.mustRequireUpdate(basicVersion, defaultVersionChannel))
    }

    @Test
    fun mustRequireUpdate_invalidMinimal() {
        Assert.assertFalse(invalidMinimalAppVersion.mustRequireUpdate(greaterVersion, defaultVersionChannel))
        Assert.assertFalse(invalidMinimalAppVersion.mustRequireUpdate(mediumVersion, defaultVersionChannel))
        Assert.assertFalse(invalidMinimalAppVersion.mustRequireUpdate(smallVersion, defaultVersionChannel))
    }

    @Test
    fun mustRequireUpdate_invalidFormat() {
        Assert.assertFalse(invalidFormatAppVersion.mustRequireUpdate(greaterVersion, defaultVersionChannel))
        Assert.assertFalse(invalidFormatAppVersion.mustRequireUpdate(mediumVersion, defaultVersionChannel))
        Assert.assertFalse(invalidFormatAppVersion.mustRequireUpdate(smallVersion, defaultVersionChannel))
    }
    //endregion
}
