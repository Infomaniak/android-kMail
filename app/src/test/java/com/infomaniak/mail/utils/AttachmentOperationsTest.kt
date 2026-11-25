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
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.AttachmentController
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DownloadResult
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DraftAttachment
import com.infomaniak.mail.utils.attachment.DefaultAttachmentOperations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class AttachmentOperationsTest {

    private val context = mockk<Context>()
    private val controller = mockk<AttachmentController>()
    private val attachment = mockk<Attachment>()
    private val operations = DefaultAttachmentOperations(context, controller)

    @get:Rule
    val cache = TemporaryFolder()

    private val draftMailbox = mockk<Mailbox> {
        every { userId } returns DRAFT_USER_ID
        every { mailboxId } returns 28
    }

    @Before
    fun setUp() {
        mockkObject(ApiRepository, SentryLog)
        every { SentryLog.e(any(), any(), any()) } answers { thirdArg<Throwable?>()?.printStackTrace() }
        every { context.cacheDir } answers { cache.root }
    }

    @After
    fun tearDown() {
        unmockkObject(LocalStorageUtils, ApiRepository, SentryLog)
    }

    @Test
    fun download_usesTheResolvedAttachmentWithoutAnotherRealmQuery() = runTest {
        mockkObject(LocalStorageUtils)
        coEvery { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) } returns true

        assertTrue(operations.download(attachment))

        coVerify(exactly = 1) { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) }
        verify(exactly = 0) { controller.getAttachment(any()) }
    }

    @Test
    fun download_returnsTheStorageFailure() = runTest {
        mockkObject(LocalStorageUtils)
        coEvery { LocalStorageUtils.downloadThenSaveAttachmentToCacheDir(context, attachment) } returns false

        assertFalse(operations.download(attachment))
    }

    @Test
    fun emptyDraftAttachment_isOnlyCachedAfterASuccessfulResponseAndOnlyWhenItsExpectedSizeIsZero() = runTest {
        val destination = mockRemoteAttachment()
        coEvery { ApiRepository.downloadAttachment(RESOURCE, DRAFT_USER_ID) } returns
                response(ByteArray(0).toResponseBody(), code = 404)

        assertFalse(operations.download(attachment, draftMailbox))
        assertFalse(destination.exists())

        coEvery { ApiRepository.downloadAttachment(RESOURCE, DRAFT_USER_ID) } answers
                { response(ByteArray(0).toResponseBody()) }
        assertTrue(operations.download(attachment, draftMailbox))
        assertTrue(destination.isFile)
        assertEquals(0L, destination.length())
        assertTrue(operations.isCached(attachment, draftMailbox))

        every { attachment.size } returns 6L
        assertFalse(operations.isCached(attachment, draftMailbox))
        assertFalse(operations.download(attachment, draftMailbox))
    }

    @Test
    fun oldActivityDownloadCannotPublishOrRemoveTheReplacementActivityCache() = runBlocking {
        val destination = mockRemoteAttachment(size = 3)
        val oldReading = CompletableDeferred<Unit>()
        val allowOldDownloadToFinish = CountDownLatch(1)
        val oldBody = object : InputStream() {
            private var hasRead = false

            override fun read(): Int {
                oldReading.complete(Unit)
                check(allowOldDownloadToFinish.await(10, TimeUnit.SECONDS))
                return -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!hasRead) {
                    hasRead = true
                    buffer[offset] = 'A'.code.toByte()
                    return 1
                }
                return read()
            }
        }
        coEvery { ApiRepository.downloadAttachment(RESOURCE, DRAFT_USER_ID) } returnsMany listOf(
            response(oldBody.source().buffer().asResponseBody()),
            response("new".toResponseBody()),
        )
        val networkManager = mockk<NetworkManager> { every { hasNetwork } returns true }
        val oldManager = AttachmentDownloadManager(networkManager, Dispatchers.IO, operations)
        val newManager = AttachmentDownloadManager(networkManager, Dispatchers.IO, operations)
        val oldViewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newViewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val draft = DraftAttachment(attachment, draftMailbox)

        try {
            withTimeout(10.seconds) {
                val oldDownload = oldManager.downloadAttachment("draft-uuid", oldViewScope, draft)
                oldReading.await()
                assertFalse(destination.exists())
                oldViewScope.cancel()

                val newDownload = newManager.downloadAttachment("draft-uuid", newViewScope, draft)
                assertEquals(DownloadResult.Ready(attachment, draftMailbox), newDownload.await())
                assertEquals("new", destination.readText())

                allowOldDownloadToFinish.countDown()
                oldDownload.join()
                while (destination.parentFile?.list()?.any { it.endsWith(".part") } == true) {
                    delay(10)
                }
                assertEquals("new", destination.readText())
            }
        } finally {
            allowOldDownloadToFinish.countDown()
            oldViewScope.cancel()
            newViewScope.cancel()
        }
    }

    private fun mockRemoteAttachment(size: Long = 0): File {
        every { attachment.resource } returns RESOURCE
        every { attachment.size } returns size
        every { attachment.name } returns "example.txt"
        every { attachment.uploadLocalUri } returns null
        return cache.root.resolve("attachments_cache/17/28/f1/m1/a1/example.txt")
    }

    private fun response(body: ResponseBody, code: Int = 200) = Response.Builder()
        .request(Request.Builder().url("https://example.test/attachment").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Test response")
        .body(body)
        .build()

    private companion object {
        const val DRAFT_USER_ID = 17
        const val RESOURCE = "folder/f1/message/m1/attachment/a1"
    }
}
