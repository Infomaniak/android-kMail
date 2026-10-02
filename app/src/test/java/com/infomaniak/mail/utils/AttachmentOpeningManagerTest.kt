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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.infomaniak.core.legacy.R
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.attachment.AttachmentDownloadManager
import com.infomaniak.mail.utils.attachment.AttachmentOpeningManager
import com.infomaniak.mail.utils.attachment.AttachmentOperations
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.SAVE_TO_DRIVE
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentOpeningManagerTest {

    private val context = mockk<Context>()
    private val networkManager = mockk<NetworkManager>()
    private val operations = mockk<AttachmentOperations>()
    private val snackbarManager = mockk<SnackbarManager>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val viewScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val activityScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val openedIntents = mutableListOf<Intent>()

    private lateinit var downloadManager: AttachmentDownloadManager
    private lateinit var openingManager: AttachmentOpeningManager

    @Before
    fun setUp() {
        mockkObject(SentryLog)
        every { SentryLog.e(any(), any(), any()) } just Runs
        every { context.getString(any()) } answers { "error-${firstArg<Int>()}" }
        every { networkManager.hasNetwork } returns true
        downloadManager = AttachmentDownloadManager(networkManager, dispatcher, operations)
        openingManager = AttachmentOpeningManager(context, downloadManager, operations, dispatcher, snackbarManager)
        openingManager.observeOpening(viewScope, openedIntents::add)
    }

    @After
    fun tearDown() {
        viewScope.cancel()
        activityScope.cancel()
        dispatcher.scheduler.runCurrent()
        unmockkObject(SentryLog)
    }

    private fun attachment(localUuid: String, cached: Boolean = false, duration: Long = 100): Pair<Attachment, Intent> {
        val attachment = mockk<Attachment> { every { this@mockk.localUuid } returns localUuid }
        val intent = mockk<Intent>()
        var isCached = cached
        coEvery { operations.getAttachment(localUuid) } returns attachment
        coEvery { operations.hasSupportedApp(attachment) } returns true
        coEvery { operations.isCached(attachment) } answers { isCached }
        coEvery { operations.getOpenIntent(attachment) } returns intent
        coEvery { operations.deleteIncompleteCache(attachment) } just Runs
        coEvery { operations.download(attachment) } coAnswers {
            delay(duration)
            isCached = true
            true
        }
        return attachment to intent
    }

    @Test
    fun directClick_downloadsAndOpensTheAttachmentReturnedByTheDownload() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1")

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        assertEquals(setOf("uuid-1"), downloadManager.downloadingUuids.value)
        assertTrue(openedIntents.isEmpty())

        advanceUntilIdle()

        assertEquals(listOf(intent), openedIntents)
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.getOpenIntent(attachment) }
        coVerify(exactly = 1) { operations.download(attachment) }
    }

    @Test
    fun directClick_cachedAttachment_opensWithoutDownloading() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1", cached = true)

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(intent), openedIntents)
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 0) { operations.download(attachment) }
    }

    @Test
    fun downloadFinishesBeforeTheOpeningWaiterSubscribes_doesNotStartAnotherDownload() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1", duration = 0)
        coEvery { operations.isCached(attachment) } returns false

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(intent), openedIntents)
        coVerify(exactly = 1) { operations.download(attachment) }
        coVerify(exactly = 1) { operations.getOpenIntent(attachment) }
    }

    @Test
    fun twoRapidClicks_waitsForTheLastAttachmentEvenWhenItFinishesLater() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1", duration = 100)
        val (second, secondIntent) = attachment("uuid-2", duration = 300)

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        runCurrent()
        assertEquals(setOf("uuid-1", "uuid-2"), downloadManager.downloadingUuids.value)

        advanceTimeBy(150)
        runCurrent()
        assertTrue(openedIntents.isEmpty())
        assertEquals(setOf("uuid-2"), downloadManager.downloadingUuids.value)

        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.download(second) }
    }

    @Test
    fun lastAttachmentFinishesFirst_doesNotOpenTheEarlierOneAfterwards() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1", duration = 300)
        val (_, secondIntent) = attachment("uuid-2", duration = 100)

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        advanceTimeBy(150)
        runCurrent()

        assertEquals(listOf(secondIntent), openedIntents)
        assertEquals(setOf("uuid-1"), downloadManager.downloadingUuids.value)

        advanceUntilIdle()
        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(first) }
    }

    @Test
    fun openWithFromActionsMenu_supersedesDirectClickAndContinuesAfterTheSheetCloses() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1", duration = 100)
        val (second, secondIntent) = attachment("uuid-2", duration = 300)
        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", activityScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.download(second) }
    }

    @Test
    fun actionDialog_staysVisibleUntilDownloadCompletesAndClosesBeforeOpening() = runTest(dispatcher) {
        val (_, intent) = attachment("uuid-1", duration = 300)
        val dialogScope = CoroutineScope(SupervisorJob() + dispatcher)
        var isDialogVisible = true
        val events = mutableListOf<String>()
        openingManager.observeOpening(viewScope) {
            assertTrue(!isDialogVisible)
            events.add("open")
            openedIntents.add(it)
        }

        openingManager.requestOpen("uuid-1", dialogScope, onFinished = {
            isDialogVisible = false
            events.add("close")
            dialogScope.cancel()
        })
        advanceTimeBy(150)
        runCurrent()
        assertTrue(isDialogVisible)
        assertTrue(openedIntents.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf("close", "open"), events)
        assertEquals(listOf(intent), openedIntents)
    }

    @Test
    fun actionDialog_joinsDirectDownloadWithoutOpeningTwice() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1", duration = 300)
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen()
        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertEquals(listOf(intent), openedIntents)
        coVerify(exactly = 1) { operations.download(attachment) }
    }

    @Test
    fun restoredActionDialog_requestsBeforeHostObservation_stillClosesAndOpens() = runTest(dispatcher) {
        val (_, intent) = attachment("uuid-1")
        val restoredOpeningManager = AttachmentOpeningManager(context, downloadManager, operations, dispatcher, snackbarManager)
        var dialogClosedCount = 0

        restoredOpeningManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()
        assertTrue(openedIntents.isEmpty())
        assertEquals(0, dialogClosedCount)

        restoredOpeningManager.observeOpening(viewScope, openedIntents::add)
        advanceUntilIdle()
        assertEquals(1, dialogClosedCount)
        assertEquals(listOf(intent), openedIntents)
    }

    @Test
    fun actionDialog_downloadFails_closesOnceAndReportsError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.download(attachment) } returns false
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun actionDialog_downloadTimesOut_closesAndDoesNotOpenAnEarlierAttachment() = runTest(dispatcher) {
        attachment("uuid-1", duration = 100)
        val (second, _) = attachment("uuid-2", duration = 180_000)
        var isDialogVisible = true

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", activityScope, onFinished = { isDialogVisible = false })
        advanceTimeBy(119_999)
        runCurrent()
        assertTrue(isDialogVisible)
        assertTrue(openedIntents.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertTrue(!isDialogVisible)
        assertTrue(openedIntents.isEmpty())
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(second) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun actionDialog_noSupportingApp_closesWithoutDownloading() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.hasSupportedApp(attachment) } returns false
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        coVerify(exactly = 0) { operations.download(attachment) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.errorNoSupportingAppFound}") }
    }

    @Test
    fun actionDialog_supportingAppCheckFails_closesAndReportsError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.hasSupportedApp(attachment) } throws IllegalStateException("Cannot query applications")
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        coVerify(exactly = 0) { operations.download(attachment) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun actionDialog_nullOpeningIntent_closesAndReportsError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.getOpenIntent(attachment) } returns null
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun actionDialog_intentPreparationFails_closesAndReportsError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.getOpenIntent(attachment) } throws IllegalStateException("Could not prepare intent")
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun actionDialog_intentPreparationCancelled_closesWithoutSwallowingCancellation() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.getOpenIntent(attachment) } throws CancellationException()
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun saveToDriveAction_usesSharedDownloadWithoutRequiringAnOpenWithApp() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        val driveIntent = mockk<Intent>()
        coEvery { operations.hasSupportedApp(attachment) } returns false
        coEvery { operations.getOpenIntent(attachment, SAVE_TO_DRIVE) } returns driveIntent
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, SAVE_TO_DRIVE, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertEquals(listOf(driveIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(attachment) }
        coVerify(exactly = 0) { operations.hasSupportedApp(any()) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun saveToDriveAction_redirectsToStore_closesWithoutReportingAnotherError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.getOpenIntent(attachment, SAVE_TO_DRIVE) } returns null
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", activityScope, SAVE_TO_DRIVE, onFinished = { dialogClosedCount++ })
        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun doubleClickOnAnUncachedAttachment_sharesTheDownloadAndOpensOnce() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1")

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(intent), openedIntents)
        coVerify(exactly = 1) { operations.download(attachment) }
        coVerify(exactly = 1) { operations.getOpenIntent(attachment) }
    }

    @Test
    fun reselectingAnAttachmentStillDownloading_opensItInsteadOfTheOtherAttachment() = runTest(dispatcher) {
        val (first, firstIntent) = attachment("uuid-1", duration = 300)
        val (second, _) = attachment("uuid-2", duration = 100)

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(firstIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.download(second) }
    }

    @Test
    fun cachedAttachment_supersedesPendingDownload() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2", cached = true)

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.download(first) }
    }

    @Test
    fun twoCachedAttachmentsClickedRapidly_onlyOpensTheLastOne() = runTest(dispatcher) {
        attachment("uuid-1", cached = true)
        val (_, secondIntent) = attachment("uuid-2", cached = true)

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
    }

    @Test
    fun newClickWhileAnEarlierIntentIsBeingPrepared_cancelsTheEarlierOpening() = runTest(dispatcher) {
        val (first, firstIntent) = attachment("uuid-1", cached = true)
        val (_, secondIntent) = attachment("uuid-2", cached = true)
        coEvery { operations.getOpenIntent(first) } coAnswers {
            delay(300)
            firstIntent
        }

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
    }

    @Test
    fun failureOfLastDownload_showsErrorWithoutOpeningAnEarlierAttachment() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (second, _) = attachment("uuid-2")
        coEvery { operations.download(second) } coAnswers {
            delay(300)
            false
        }

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.deleteIncompleteCache(second) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun threeDownloadsFailWithoutNetwork_onlyShowsTheLastRequestedError() = runTest(dispatcher) {
        every { networkManager.hasNetwork } returns false
        val attachments = (1..3).map { index ->
            val (attachment, _) = attachment("uuid-$index")
            coEvery { operations.download(attachment) } coAnswers {
                delay(index * 100L)
                false
            }
            attachment
        }

        attachments.forEach { openingManager.requestOpen(it.localUuid, viewScope) }
        advanceTimeBy(250)
        runCurrent()

        assertEquals(setOf("uuid-3"), downloadManager.downloadingUuids.value)
        verify(exactly = 0) { snackbarManager.postValue(any()) }

        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.noConnection}") }
        attachments.forEach { attachment ->
            coVerify(exactly = 1) { operations.download(attachment) }
            coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        }
    }

    @Test
    fun lastDownloadFailsFirst_earlierFailuresDoNotShowMoreErrorsAfterwards() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (second, _) = attachment("uuid-2")
        val (third, _) = attachment("uuid-3")
        listOf(third, second, first).forEachIndexed { index, attachment ->
            coEvery { operations.download(attachment) } coAnswers {
                delay((index + 1) * 100L)
                false
            }
        }

        listOf(first, second, third).forEach { openingManager.requestOpen(it.localUuid, viewScope) }
        advanceTimeBy(150)
        runCurrent()

        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
        assertEquals(setOf("uuid-1", "uuid-2"), downloadManager.downloadingUuids.value)

        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue(any()) }
    }

    @Test
    fun lastDownloadSucceeds_earlierFailuresRemainSilent() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2", duration = 100)
        coEvery { operations.download(first) } coAnswers {
            delay(300)
            false
        }

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.deleteIncompleteCache(first) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun earlierDownloadThrowsAfterTheLastOpening_logsFailureWithoutShowingSnackbar() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2", duration = 100)
        coEvery { operations.download(first) } coAnswers {
            delay(300)
            throw IOException("Download failed")
        }

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.deleteIncompleteCache(first) }
        verify { SentryLog.e(any(), any(), any()) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun actionDialogFails_afterEarlierDownloadFailures_closesAndShowsOnlyItsError() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (second, _) = attachment("uuid-2")
        coEvery { operations.download(first) } coAnswers {
            delay(100)
            false
        }
        coEvery { operations.download(second) } coAnswers {
            delay(300)
            false
        }
        var dialogClosedCount = 0

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen()
        openingManager.requestOpen("uuid-2", activityScope, onFinished = { dialogClosedCount++ })
        advanceTimeBy(150)
        runCurrent()

        assertEquals(0, dialogClosedCount)
        verify(exactly = 0) { snackbarManager.postValue(any()) }

        advanceUntilIdle()

        assertEquals(1, dialogClosedCount)
        assertTrue(openedIntents.isEmpty())
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun earlierSupportingAppCheckFails_afterANewRequest_doesNotShowAnError() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2", cached = true)
        coEvery { operations.hasSupportedApp(first) } coAnswers {
            delay(300)
            throw IllegalStateException("Cannot query applications")
        }

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        verify { SentryLog.e(any(), any(), any()) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun earlierAttachmentHasNoSupportingApp_afterANewRequest_doesNotShowAnError() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2", cached = true)
        coEvery { operations.hasSupportedApp(first) } coAnswers {
            delay(300)
            false
        }

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun cancelledOpening_downloadFailureDoesNotShowAnError() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.download(attachment) } coAnswers {
            delay(300)
            false
        }

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen()
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun cancelPendingOpen_leavesDownloadsRunningWithoutOpeningAnyAttachment() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (second, _) = attachment("uuid-2")

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen()
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        coVerify(exactly = 1) { operations.download(first) }
        coVerify(exactly = 1) { operations.download(second) }
        coVerify(exactly = 0) { operations.deleteIncompleteCache(any()) }
    }

    @Test
    fun deletingTheSelectedAttachment_cancelsItsDownloadAndOpening() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")

        openingManager.requestOpen("uuid-1", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen("uuid-1")
        downloadManager.cancelDownload("uuid-1")
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        assertTrue(downloadManager.downloadingUuids.value.isEmpty())
        coVerify(exactly = 1) { operations.deleteIncompleteCache(attachment) }
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun deletingAnEarlierAttachment_doesNotCancelTheLastOpening() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2")

        openingManager.requestOpen("uuid-1", viewScope)
        openingManager.requestOpen("uuid-2", viewScope)
        runCurrent()
        openingManager.cancelPendingOpen("uuid-1")
        downloadManager.cancelDownload("uuid-1")
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        coVerify(exactly = 1) { operations.deleteIncompleteCache(first) }
    }

    @Test
    fun nullIntent_showsErrorAndDoesNotPreventLaterOpenings() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2")
        coEvery { operations.getOpenIntent(first) } returns null

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()
        assertTrue(openedIntents.isEmpty())
        verify { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }

        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()
        assertEquals(listOf(secondIntent), openedIntents)
    }

    @Test
    fun missingSupportingApp_showsErrorAndDoesNotDownload() = runTest(dispatcher) {
        val (attachment, _) = attachment("uuid-1")
        coEvery { operations.hasSupportedApp(attachment) } returns false

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
        coVerify(exactly = 0) { operations.download(attachment) }
        verify(exactly = 1) { snackbarManager.postValue("error-${R.string.errorNoSupportingAppFound}") }
    }

    @Test
    fun intentPreparationCancellation_isNotReportedAsAnError() = runTest(dispatcher) {
        val (first, _) = attachment("uuid-1")
        val (_, secondIntent) = attachment("uuid-2")
        coEvery { operations.getOpenIntent(first) } throws CancellationException()

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()
        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(secondIntent), openedIntents)
        verify(exactly = 0) { snackbarManager.postValue(any()) }
    }

    @Test
    fun startActivityFailure_showsErrorAndKeepsTheOpeningObserverAlive() = runTest(dispatcher) {
        val (_, firstIntent) = attachment("uuid-1", cached = true)
        val (_, secondIntent) = attachment("uuid-2", cached = true)
        openingManager.observeOpening(viewScope) { intent ->
            if (intent == firstIntent) throw ActivityNotFoundException("No viewer")
            openedIntents.add(intent)
        }

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()
        verify { snackbarManager.postValue("error-${R.string.errorNoSupportingAppFound}") }

        openingManager.requestOpen("uuid-2", viewScope)
        advanceUntilIdle()
        assertEquals(listOf(secondIntent), openedIntents)
    }

    @Test
    fun securityFailureWhenStartingActivity_showsError() = runTest(dispatcher) {
        attachment("uuid-1", cached = true)
        openingManager.observeOpening(viewScope) { throw SecurityException("Access denied") }

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        verify { snackbarManager.postValue("error-${R.string.anErrorHasOccurred}") }
    }

    @Test
    fun leavingTheScreen_clearsItsPendingOpening() = runTest(dispatcher) {
        attachment("uuid-1")

        openingManager.requestOpen("uuid-1", activityScope)
        runCurrent()
        viewScope.cancel()
        runCurrent()
        openingManager.observeOpening(activityScope, openedIntents::add)
        advanceUntilIdle()

        assertTrue(openedIntents.isEmpty())
    }

    @Test
    fun subsequentClickAfterOpening_canOpenTheSameAttachmentAgain() = runTest(dispatcher) {
        val (attachment, intent) = attachment("uuid-1")

        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()
        openingManager.requestOpen("uuid-1", viewScope)
        advanceUntilIdle()

        assertEquals(listOf(intent, intent), openedIntents)
        coVerify(exactly = 1) { operations.download(attachment) }
    }

    @Test
    fun twoRapidClicksWithSeparateUiAndIoDispatchers_openOnlyTheLastAttachment() = runBlocking {
        val ioDownloadManager = AttachmentDownloadManager(networkManager, Dispatchers.IO, operations)
        val ioOpeningManager = AttachmentOpeningManager(context, ioDownloadManager, operations, Dispatchers.IO, snackbarManager)
        val (first, _) = attachment("uuid-1")
        val (second, secondIntent) = attachment("uuid-2")
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Boolean>()
        val finishSecond = CompletableDeferred<Boolean>()
        val opened = CompletableDeferred<Intent>()
        val realOpenedIntents = ConcurrentLinkedQueue<Intent>()
        coEvery { operations.download(first) } coAnswers {
            firstStarted.complete(Unit)
            finishFirst.await()
        }
        coEvery { operations.download(second) } coAnswers {
            secondStarted.complete(Unit)
            finishSecond.await()
        }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { uiDispatcher ->
            val scope = CoroutineScope(SupervisorJob() + uiDispatcher)
            try {
                withContext(uiDispatcher) {
                    ioOpeningManager.observeOpening(scope) {
                        realOpenedIntents.add(it)
                        opened.complete(it)
                    }
                    ioOpeningManager.requestOpen("uuid-1", scope)
                    ioOpeningManager.requestOpen("uuid-2", scope)
                }
                withTimeout(5.seconds) {
                    firstStarted.await()
                    secondStarted.await()
                    finishFirst.complete(true)
                    finishSecond.complete(true)
                    assertEquals(secondIntent, opened.await())
                    ioDownloadManager.downloadingUuids.first { it.isEmpty() }
                }
                assertEquals(listOf(secondIntent), realOpenedIntents.toList())
                coVerify(exactly = 1) { operations.download(first) }
                coVerify(exactly = 1) { operations.download(second) }
            } finally {
                scope.cancel()
            }
        }
    }
}
