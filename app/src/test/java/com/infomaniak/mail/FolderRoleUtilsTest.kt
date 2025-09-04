/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.dataset.DummyFolders.folderDraft
import com.infomaniak.mail.dataset.DummyFolders.folderInbox
import com.infomaniak.mail.dataset.DummyFolders.folderSearch
import com.infomaniak.mail.dataset.DummyFolders.folderSent
import com.infomaniak.mail.dataset.DummyFolders.folderSnoozed
import com.infomaniak.mail.dataset.DummyMailboxContent
import com.infomaniak.mail.dataset.DummyMessages.messageDraft
import com.infomaniak.mail.dataset.DummyMessages.messageInbox
import com.infomaniak.mail.dataset.DummyMessages.messageSnoozed
import com.infomaniak.mail.dataset.DummyThreads.threadDraft
import com.infomaniak.mail.dataset.DummyThreads.threadInbox
import com.infomaniak.mail.dataset.DummyThreads.threadSearchInbox
import com.infomaniak.mail.dataset.DummyThreads.threadSearchSnoozed
import com.infomaniak.mail.dataset.DummyThreads.threadSnoozed
import com.infomaniak.mail.utils.FolderRoleUtils
import io.mockk.mockk
import io.realm.kotlin.UpdatePolicy
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class FolderRoleUtilsTest {

    private val mockContext = mockk<Context>() // We can mock this one because we don't use it in our tests
    private val folderController = FolderController(mockContext, mailboxContentRealm)
    private val folderRoleUtils = FolderRoleUtils(folderController)

    @Test
    fun messageInbox_should_be_INBOX() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(messageInbox) }
        assertTrue(folderRole == FolderRole.INBOX)
    }

    @Test
    fun threadInbox_should_be_INBOX() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(threadInbox) }
        assertTrue(folderRole == FolderRole.INBOX)
    }

    @Test
    fun messageDraft_should_be_DRAFT() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(messageDraft) }
        assertTrue(folderRole == FolderRole.DRAFT)
    }

    @Test
    fun threadDraft_should_be_DRAFT() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(threadDraft) }
        assertTrue(folderRole == FolderRole.DRAFT)
    }

    @Test
    fun messageSnoozed_should_be_SNOOZED() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(messageSnoozed) }
        assertTrue(folderRole == FolderRole.SNOOZED)
    }

    @Test
    fun threadSnoozed_should_be_SNOOZED() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(threadSnoozed) }
        assertTrue(folderRole == FolderRole.SNOOZED)
    }

    @Test
    fun threadSearchInbox_should_be_INBOX() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(threadSearchInbox) }
        assertTrue(folderRole == FolderRole.INBOX)
    }

    @Test
    fun threadSearchSnoozed_should_be_SNOOZED() {
        val folderRole = runBlocking { folderRoleUtils.getActionFolderRole(threadSearchSnoozed) }
        assertTrue(folderRole == FolderRole.SNOOZED)
    }

    companion object {

        private val mailboxContentRealm = DummyMailboxContent()

        @BeforeClass
        @JvmStatic
        fun setup() {
            mailboxContentRealm().writeBlocking {
                copyToRealm(folderInbox, UpdatePolicy.ALL)
                copyToRealm(folderSent, UpdatePolicy.ALL)
                copyToRealm(folderDraft, UpdatePolicy.ALL)
                copyToRealm(folderSnoozed, UpdatePolicy.ALL)
                copyToRealm(folderSearch, UpdatePolicy.ALL)
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            mailboxContentRealm().close()
        }
    }
}
