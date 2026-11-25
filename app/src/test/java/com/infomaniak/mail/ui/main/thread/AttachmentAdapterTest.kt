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
package com.infomaniak.mail.ui.main.thread

import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.ui.main.thread.AttachmentAdapter.AttachmentViewHolder
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.realm.kotlin.internal.interop.ErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentAdapterTest {

    @Test
    fun closedRealmRow_doesNotBlockProgressUpdatesOrCrashOnPendingPayload() {
        val stale = mockk<Attachable> { every { localUuid } returns "stale" }
        val current = mockk<Attachable> { every { localUuid } returns "current" }
        val holder = mockk<AttachmentViewHolder> { every { binding } returns mockk() }
        val adapter = spyk(AttachmentAdapter())
        every { adapter.notifyDataSetChanged() } just Runs
        every { adapter.notifyItemChanged(any(), any()) } just Runs
        adapter.submitList(listOf(stale, current))

        adapter.setDownloadingUuids(setOf("stale"))
        val progressPayload = slot<Any>()
        verify(exactly = 1) { adapter.notifyItemChanged(0, capture(progressPayload)) }

        every { stale.localUuid } throws IllegalStateException(ErrorCode.RLM_ERR_CLOSED_REALM.name)
        adapter.setDownloadingUuids(setOf("current"))
        adapter.onBindViewHolder(holder, 0, mutableListOf(progressPayload.captured))

        assertFalse(adapter.isAttachmentDownloading("stale"))
        assertTrue(adapter.isAttachmentDownloading("current"))
        verify(exactly = 1) { adapter.notifyItemChanged(1, progressPayload.captured) }
    }
}
