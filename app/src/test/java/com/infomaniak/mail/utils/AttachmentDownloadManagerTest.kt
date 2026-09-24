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
import android.content.Intent
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager
import com.infomaniak.mail.utils.attachment.AttachmentOperations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentDownloadManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val networkManager = mockk<NetworkManager>(relaxed = true)
    private val operations = mockk<AttachmentOperations>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val snackbarManager = mockk<SnackbarManager>(relaxed = true)

    private lateinit var attachmentDownloadManager: AttachmentDownloadManager

    @Before
    fun setUp() {
        every { context.getString(any()) } returns "Error message"

        attachmentDownloadManager = AttachmentDownloadManager(
            context = context,
            networkManager = networkManager,
            ioDispatcher = testDispatcher,
            operations = operations,
            snackbarManager = snackbarManager,
        )
    }

    @Test
    fun downloadAndOpenAttachment_noSupportedApp_showsErrorSnackbar() = runTest(testDispatcher) {
        val attachment = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        coEvery { operations.hasSupportedApp(attachment) } returns false

        var downloadStateChanged = false
        var snackbarShown = false

        coEvery { snackbarManager.postValue(any()) } answers {
            snackbarShown = true
        }

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, _ -> downloadStateChanged = true },
            openIntent = {},
        )
        advanceUntilIdle()

        assertFalse(downloadStateChanged)
        assertTrue(snackbarShown)
        assertFalse(attachmentDownloadManager.isDownloading("uuid-1"))
    }

    @Test
    fun downloadAndOpenAttachment_alreadyCached_opensDirectly() = runTest(testDispatcher) {
        val dummyIntent = mockk<Intent>()
        val attachment = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } returns true
        coEvery { operations.getOpenIntent(attachment) } returns dummyIntent

        var openedIntent: Intent? = null
        var downloadStateCalled = false

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, _ -> downloadStateCalled = true },
            openIntent = { openedIntent = it },
        )
        advanceUntilIdle()

        assertFalse(downloadStateCalled)
        assertEquals(dummyIntent, openedIntent)
        assertFalse(attachmentDownloadManager.isDownloading("uuid-1"))
    }

    @Test
    fun downloadAndOpenAttachment_notCached_downloadsAndOpens() = runTest(testDispatcher) {
        val dummyIntent = mockk<Intent>()
        val attachment = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } returns false
        coEvery { operations.download(attachment) } returns true
        coEvery { operations.getOpenIntent(attachment) } returns dummyIntent

        val downloadStates = mutableListOf<Boolean>()
        var openedIntent: Intent? = null

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, isDownloading -> downloadStates.add(isDownloading) },
            openIntent = { openedIntent = it },
        )
        advanceUntilIdle()

        assertEquals(listOf(true, false), downloadStates)
        assertEquals(dummyIntent, openedIntent)
        assertFalse(attachmentDownloadManager.isDownloading("uuid-1"))
    }

    @Test
    fun downloadAndOpenAttachment_multipleClicked_opensFirstOneToFinish() = runTest(testDispatcher) {
        val intent1 = mockk<Intent>()
        val intent2 = mockk<Intent>()

        val attachment1 = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        val attachment2 = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-2"
        }

        coEvery { operations.hasSupportedApp(attachment1) } returns true
        coEvery { operations.isCached(attachment1) } returns false
        coEvery { operations.getOpenIntent(attachment1) } returns intent1

        coEvery { operations.hasSupportedApp(attachment2) } returns true
        coEvery { operations.isCached(attachment2) } returns false
        coEvery { operations.getOpenIntent(attachment2) } returns intent2

        coEvery { operations.download(attachment1) } coAnswers {
            delay(100.milliseconds)
            true
        }
        coEvery { operations.download(attachment2) } coAnswers {
            delay(50.milliseconds)
            true
        }

        val openedIntents = mutableListOf<Intent>()

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment1,
            scope = this,
            onDownloadStateChanged = { _, _ -> },
            openIntent = { openedIntents.add(it) },
        )

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment2,
            scope = this,
            onDownloadStateChanged = { _, _ -> },
            openIntent = { openedIntents.add(it) },
        )

        advanceUntilIdle()

        // attachment2 finished first (50ms vs 100ms), so only intent2 should be opened!
        assertEquals(1, openedIntents.size)
        assertEquals(intent2, openedIntents.first())
        assertFalse(attachmentDownloadManager.isDownloading("uuid-1"))
        assertFalse(attachmentDownloadManager.isDownloading("uuid-2"))
    }

    @Test
    fun downloadAndOpenAttachment_alreadyDownloading_ignoresSecondClick() = runTest(testDispatcher) {
        val dummyIntent = mockk<Intent>()
        val attachment = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } returns false
        coEvery { operations.download(attachment) } coAnswers {
            delay(100.milliseconds)
            true
        }
        coEvery { operations.getOpenIntent(attachment) } returns dummyIntent

        val downloadStates = mutableListOf<Boolean>()

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, isDownloading -> downloadStates.add(isDownloading) },
            openIntent = {},
        )

        // Second click while still downloading
        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, isDownloading -> downloadStates.add(isDownloading) },
            openIntent = {},
        )

        advanceUntilIdle()

        assertEquals(listOf(true, false), downloadStates)
    }

    @Test
    fun downloadAndOpenAttachment_afterAllDownloadsComplete_newDownloadCanOpen() = runTest(testDispatcher) {
        val intent1 = mockk<Intent>()
        val intent2 = mockk<Intent>()

        val attachment1 = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        val attachment2 = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-2"
        }

        coEvery { operations.hasSupportedApp(attachment1) } returns true
        coEvery { operations.isCached(attachment1) } returns false
        coEvery { operations.download(attachment1) } returns true
        coEvery { operations.getOpenIntent(attachment1) } returns intent1

        coEvery { operations.hasSupportedApp(attachment2) } returns true
        coEvery { operations.isCached(attachment2) } returns false
        coEvery { operations.download(attachment2) } returns true
        coEvery { operations.getOpenIntent(attachment2) } returns intent2

        val openedIntents = mutableListOf<Intent>()

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment1,
            scope = this,
            onDownloadStateChanged = { _, _ -> },
            openIntent = { openedIntents.add(it) },
        )
        advanceUntilIdle()

        assertEquals(listOf(intent1), openedIntents)

        // Subsequent download now that first has completed
        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment2,
            scope = this,
            onDownloadStateChanged = { _, _ -> },
            openIntent = { openedIntents.add(it) },
        )
        advanceUntilIdle()

        assertEquals(listOf(intent1, intent2), openedIntents)
    }

    @Test
    fun downloadAndOpenAttachment_downloadFails_showsSnackbarAndCleansUp() = runTest(testDispatcher) {
        val attachment = mockk<Attachment>(relaxed = true) {
            every { localUuid } returns "uuid-1"
        }
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } returns false
        coEvery { operations.download(attachment) } returns false

        var errorSnackbarShown = false
        coEvery { snackbarManager.postValue(any()) } answers { errorSnackbarShown = true }

        val downloadStates = mutableListOf<Boolean>()

        attachmentDownloadManager.downloadAndOpenAttachment(
            attachment = attachment,
            scope = this,
            onDownloadStateChanged = { _, isDownloading -> downloadStates.add(isDownloading) },
            openIntent = {},
        )
        advanceUntilIdle()

        assertEquals(listOf(true, false), downloadStates)
        assertTrue(errorSnackbarShown)
        assertFalse(attachmentDownloadManager.isDownloading("uuid-1"))
    }
}
