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
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.utils.FolderRoleUtils
import com.infomaniak.mail.utils.extensions.toRealmInstant
import io.realm.kotlin.UpdatePolicy
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class FolderRoleUtilsTest {

    @Mock
    private val mockContext = mock<Context>()
    private val mailboxContentRealm = RealmDatabase.TestMailboxContent()
    private val folderController = FolderController(mockContext, mailboxContentRealm)
    private val folderRoleUtils = FolderRoleUtils(folderController)

    //region Messages
    private val messageInbox = Message().apply {
        uid = MESSAGE_INBOX_ID
        messageId = uid
        folderId = FOLDER_INBOX_ID
    }

    private val messageInboxSnoozed = Message().apply {
        uid = MESSAGE_INBOX_SNOOZED_ID
        messageId = uid
        folderId = FOLDER_INBOX_ID
        snoozeState = SnoozeState.Snoozed
        snoozeEndDate = Date().toRealmInstant()
        snoozeUuid = uid
    }

    private val messageDraft = Message().apply {
        uid = MESSAGE_DRAFT_ID
        messageId = uid
        folderId = FOLDER_DRAFT_ID
    }
    //endregion

    //region Threads
    private val threadInboxSnoozed = messageInbox.toThread().apply {
        addMessageWithConditions(messageInboxSnoozed, mailboxContentRealm())
        recomputeThread()
    }

    private val threadDraft = messageDraft.toThread().apply {
        recomputeThread()
    }
    //endregion

    //region Folders
    private val folderInbox = Folder().apply {
        id = FOLDER_INBOX_ID
        _role = FolderRole.INBOX.name

        threadInboxSnoozed.folderId = id
        threads.add(threadInboxSnoozed)
    }

    private val folderDraft = Folder().apply {
        id = FOLDER_DRAFT_ID
        _role = FolderRole.DRAFT.name

        threadDraft.folderId = id
        threads.add(threadDraft)
    }
    //endregion

    private fun setup() {
        mailboxContentRealm().writeBlocking {
            copyToRealm(folderInbox, UpdatePolicy.ALL)
            copyToRealm(folderDraft, UpdatePolicy.ALL)
        }
    }

    @Test
    fun messageInbox_should_be_INBOX() {
        setup()

        val folderRole = folderRoleUtils.getActionFolderRole(messageInbox)
        assertTrue(folderRole == FolderRole.INBOX)
    }

    @Test
    fun messageInboxSnoozed_should_be_SNOOZED() {
        setup()

        val folderRole = folderRoleUtils.getActionFolderRole(messageInboxSnoozed)
        assertTrue(folderRole == FolderRole.SNOOZED)
    }

    @Test
    fun messageDraft_should_be_DRAFT() {
        setup()

        val folderRole = folderRoleUtils.getActionFolderRole(messageDraft)
        assertTrue(folderRole == FolderRole.DRAFT)
    }

    @Test
    fun threadInboxSnoozed_should_be_SNOOZED() {
        setup()

        val folderRole = folderRoleUtils.getActionFolderRole(threadInboxSnoozed)
        assertTrue(folderRole == FolderRole.SNOOZED)
    }

    @Test
    fun threadDraft_should_be_DRAFT() {
        setup()

        val folderRole = folderRoleUtils.getActionFolderRole(threadDraft)
        assertTrue(folderRole == FolderRole.DRAFT)
    }

    companion object {
        private const val FOLDER_INBOX_ID = "FOLDER_INBOX_ID"
        private const val FOLDER_DRAFT_ID = "FOLDER_DRAFT_ID"
        private const val MESSAGE_INBOX_ID = "MESSAGE_INBOX_ID"
        private const val MESSAGE_INBOX_SNOOZED_ID = "MESSAGE_INBOX_SNOOZED_ID"
        private const val MESSAGE_DRAFT_ID = "MESSAGE_DRAFT_ID"
    }
}
