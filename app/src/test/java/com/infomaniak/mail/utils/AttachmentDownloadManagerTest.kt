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

import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager.DownloadResult
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentDownloadManagerTest {

    private val networkManager = mockk<NetworkManager>()
    private val operations = mockk<AttachmentOperations>()
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private lateinit var manager: AttachmentDownloadManager

    @Before
    fun setUp() {
        mockkObject(SentryLog)
        every { SentryLog.e(any(), any(), any()) } just Runs
        every { networkManager.hasNetwork } returns true
        manager = AttachmentDownloadManager(networkManager, dispatcher, operations)
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
    }

    @Test
    fun retryAfterCancellation_waitsForCleanupAndKeepsItsSuccessfulCache() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        var attempts = 0
        var cacheExists = false
        coEvery { operations.download(attachment) } coAnswers {
            attempts++
            if (attempts == 1) awaitCancellation()
            cacheExists = true
            true
        }
        coEvery { operations.deleteIncompleteCache(attachment) } coAnswers {
            cleanupStarted.complete(Unit)
            allowCleanup.await()
            cacheExists = false
        }

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        runCurrent()
        assertTrue(cleanupStarted.isCompleted)

        val retry = manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        val retryStartedBeforeCleanup = attempts > 1
        val retryFinishedBeforeCleanup = retry.isCompleted

        allowCleanup.complete(Unit)
        advanceUntilIdle()

        assertFalse(retryStartedBeforeCleanup)
        assertFalse(retryFinishedBeforeCleanup)
        assertEquals(DownloadResult.Ready(attachment), retry.await())
        assertTrue(cacheExists)
        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
    }

    @Test
    fun cleanupOfOneAttachment_doesNotBlockAnotherAttachment() = runTest(dispatcher) {
        val first = attachment("uuid-1")
        val second = attachment("uuid-2", duration = 0)
        val allowCleanup = CompletableDeferred<Unit>()
        coEvery { operations.download(first) } coAnswers { awaitCancellation() }
        coEvery { operations.deleteIncompleteCache(first) } coAnswers { allowCleanup.await() }

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        runCurrent()
        val download = manager.downloadAttachment("uuid-2", scope)
        runCurrent()
        val completedBeforeCleanup = download.isCompleted

        allowCleanup.complete(Unit)
        advanceUntilIdle()

        assertTrue(completedBeforeCleanup)
        assertEquals(DownloadResult.Ready(second), download.await())
    }

    @Test
    fun retryFromANewScreen_waitsForThePreviousScreenCleanup() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        val allowCleanup = CompletableDeferred<Unit>()
        var attempts = 0
        coEvery { operations.download(attachment) } coAnswers {
            attempts++
            if (attempts == 1) awaitCancellation()
            true
        }
        coEvery { operations.deleteIncompleteCache(attachment) } coAnswers { allowCleanup.await() }

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        scope.cancel()
        runCurrent()

        val newScreenScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val retry = manager.downloadAttachment("uuid-1", newScreenScope)
            runCurrent()
            val retryStartedBeforeCleanup = attempts > 1
            allowCleanup.complete(Unit)
            advanceUntilIdle()

            assertFalse(retryStartedBeforeCleanup)
            assertEquals(DownloadResult.Ready(attachment), retry.await())
        } finally {
            allowCleanup.complete(Unit)
            newScreenScope.cancel()
        }
    }

    @Test
    fun duplicateRetriesDuringCleanup_shareOneNewDownload() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        val allowCleanup = CompletableDeferred<Unit>()
        var attempts = 0
        coEvery { operations.download(attachment) } coAnswers {
            attempts++
            if (attempts == 1) awaitCancellation()
            true
        }
        coEvery { operations.deleteIncompleteCache(attachment) } coAnswers { allowCleanup.await() }

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        runCurrent()

        val firstRetry = manager.downloadAttachment("uuid-1", scope)
        val secondRetry = manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        val attemptsBeforeCleanup = attempts
        allowCleanup.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, attemptsBeforeCleanup)
        assertEquals(2, attempts)
        assertEquals(DownloadResult.Ready(attachment), firstRetry.await())
        assertEquals(firstRetry.await(), secondRetry.await())
    }

    @Test
    fun cancellingARetryDuringCleanup_doesNotLetAFollowingRetryBypassCleanup() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        val allowCleanup = CompletableDeferred<Unit>()
        var attempts = 0
        coEvery { operations.download(attachment) } coAnswers {
            attempts++
            if (attempts == 1) awaitCancellation()
            true
        }
        coEvery { operations.deleteIncompleteCache(attachment) } coAnswers { allowCleanup.await() }

        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        runCurrent()
        manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        manager.cancelDownload("uuid-1")
        runCurrent()
        val retry = manager.downloadAttachment("uuid-1", scope)
        runCurrent()
        val attemptsBeforeCleanup = attempts
        allowCleanup.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, attemptsBeforeCleanup)
        assertEquals(2, attempts)
        assertEquals(DownloadResult.Ready(attachment), retry.await())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
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
    }

    @Test
    fun downloadFailure_cleansCacheAndReturnsFailure() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.download(attachment) } returns false

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        assertEquals(DownloadResult.Failed(R.string.anErrorHasOccurred), download.await())
    }

    @Test
    fun downloadException_cleansCacheAndReturnsFailure() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        coEvery { operations.download(attachment) } throws IOException("Download failed")

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        assertEquals(DownloadResult.Failed(R.string.anErrorHasOccurred), download.await())
        verify { SentryLog.e(any(), any(), any()) }
    }

    @Test
    fun failureWithoutNetwork_returnsNoConnectionError() = runTest(dispatcher) {
        val attachment = attachment("uuid-1")
        every { networkManager.hasNetwork } returns false
        coEvery { operations.download(attachment) } returns false

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertEquals(DownloadResult.Failed(R.string.noConnection), download.await())
    }

    @Test
    fun preparationException_isReportedInsteadOfLeavingObserversWaitingForever() = runTest(dispatcher) {
        coEvery { operations.getAttachment("uuid-1") } throws IllegalStateException("Attachment unavailable")

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertTrue(manager.downloadingUuids.value.isEmpty())
        assertEquals(DownloadResult.Failed(R.string.anErrorHasOccurred), download.await())
    }

    @Test
    fun missingAttachment_returnsFailure() = runTest(dispatcher) {
        coEvery { operations.getAttachment("uuid-1") } returns null

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceUntilIdle()

        assertEquals(DownloadResult.Failed(R.string.anErrorHasOccurred), download.await())
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
    }

    @Test
    fun timeout_cleansIncompleteCacheAndReportsFailureAfterTwoMinutes() = runTest(dispatcher) {
        val attachment = attachment("uuid-1", duration = 180_000)

        val download = manager.downloadAttachment("uuid-1", scope)
        advanceTimeBy(119_999)
        runCurrent()
        assertEquals(setOf("uuid-1"), manager.downloadingUuids.value)
        assertTrue(!download.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(manager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        assertEquals(DownloadResult.Failed(R.string.anErrorHasOccurred), download.await())
    }
}
