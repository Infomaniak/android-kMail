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

import android.content.Context
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.utils.attachment.DefaultAttachmentOperations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentOperationsTest {

    private val context = mockk<Context>()
    private val controller = mockk<AttachmentController>()
    private val attachment = mockk<Attachment>()
    private val operations = DefaultAttachmentOperations(context, controller)

    @Before
    fun setUp() {
        mockkObject(LocalStorageUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(LocalStorageUtils)
    }

    @Test
    fun download_usesTheResolvedAttachmentWithoutAnotherRealmQuery() = runTest {
        coEvery { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) } returns true

        assertTrue(operations.download(attachment))

        coVerify(exactly = 1) { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) }
        verify(exactly = 0) { controller.getAttachment(any()) }
    }

    @Test
    fun download_returnsTheStorageFailure() = runTest {
        coEvery { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) } returns false

        assertFalse(operations.download(attachment))
    }
}
