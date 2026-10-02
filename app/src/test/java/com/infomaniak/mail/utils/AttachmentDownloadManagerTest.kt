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
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager
import com.infomaniak.mail.utils.attachment.AttachmentOperations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentDownloadManagerTest {

    private val context = mockk<Context>()
    private val networkManager = mockk<NetworkManager>()
    private val operations = mockk<AttachmentOperations>()
    private val snackbarManager = mockk<SnackbarManager>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private lateinit var manager: AttachmentDownloadManager

    @Before
    fun setUp() {
        mockkObject(SentryLog)
        every { SentryLog.e(any(), any(), any()) } just Runs
        every { context.getString(any()) } answers { "error-${firstArg<Int>()}" }
        every { networkManager.hasNetwork } returns true
        manager = AttachmentDownloadManager(context, networkManager, dispatcher, operations, snackbarManager)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dispatcher.scheduler.runCurrent()
        unmockkObject(SentryLog)
    }

    private fun attachment(localUuid: String, cached: Boolean = false, duration: Long = 100): Attachment {
        val attachment = mockk<Attachment> { every { this@mockk.localUuid } returns localUuid }
        coEvery { operations.getAttachment(localUuid) } returns attachment
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } returns cached
        coEvery { operations.deleteIncompleteCache(attachment) } just Runs
        coEvery { operations.download(attachment) } coAnswers {
            delay(duration)
            true
        }
        return attachment
    }

    @Test
    fun downloadAttachment_downloadsWithoutPreparingAnOpeningIntent() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        assertEquals(setOf("uuid-1"), manager.downloadingUuids.value)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.download(attachment) }
        coVerify(exactly = 0) { operations.getOpenIntent(any()) }
    }

    @Test
    fun duplicateRequests_shareOneDownload() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")

        manager.downloadAttachment("uuid-1", scope)
        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        coVerify(exactly = 1) { operations.download(attachment) }
        assertTrue(manager.downloadingUuids.value.isEmpty())
    }

    @Test
    fun independentAttachments_downloadInParallel() = runTest(dispatcher) {
        val first = attachment("uuid-1", duration = 100)
        val second = attachment("uuid-2", duration = 300)

        manager.downloadAttachment("uuid-1", scope)
        manager.downloadAttachment("uuid-2", scope)
        runCurrent()
        assertEquals(setOf("uuid-1", "uuid-2"), manager.downloadingUuids.value)

        advanceTimeBy(150)
        runCurrent()
        assertEquals(setOf("uuid-2"), manager.downloadingUuids.value)
        advanceUntilIdle()

        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.download(second) }
        assertTrue(manager.downloadingUuids.value.isEmpty())
    }

    @Test
    fun cachedAttachment_doesNotShowDownloadProgress() = runTest(dispatcher) {
        val attachment = attachment("uuid-1", cached = true)

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 0) { operations.download(attachment) }
        coVerify(exactly = 0) { operations.getOpenIntent(any()) }
    }

    @Test
    fun cancelDownload_deletesIncompleteCacheWithoutReportingAnError() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun leavingTheScreen_cancelsItsDownloads() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        scope.cancel()
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun downloadFailure_cleansCacheAndShowsError() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.download(attachment) } returns false

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun downloadException_cleansCacheAndShowsError() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.download(attachment) } throws IOException("Download failed")

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
        verify { SentryLog.e(any(), any(), any()) }
    }

    @Test
    fun failureWithoutNetwork_showsNoConnectionError() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        every { networkManager.hasNetwork } returns false
        coEvery { operations.download(attachment) } returns false

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        verify { snackbarManager.postValue("error-${R.string.noConnection}") }
    }

    @Test
    fun preparationException_isReportedInsteadOfLeavingObserversWaitingForever() = runTest(dispatcher) {
        coEvery { operations.getAttachment("uuid-1") } throws IllegalStateException("Attachment unavailable")

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        verify { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun missingAttachment_showsError() = runTest(dispatcher) {
        coEvery { operations.getAttachment("uuid-1") } returns null

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        verify { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun failedDownload_canBeRetried() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.download(attachment) } returns false andThen true

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()
        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        coVerify(exactly = 2) { operations.download(attachment) }
        assertTrue(manager.downloadingUuids.value.isEmpty())
    }

    @Test
    fun downloadWithoutSupportingApp_isStillAvailableForSaveToDrive() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.hasSupportedApp(attachment) } returns false

        manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        coVerify(exactly = 1) { operations.download(attachment) }
        coVerify(exactly = 0) { operations.hasSupportedApp(any()) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }
}
