/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.mail.data.cache

import android.content.Context
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.data.cache.migrations.MAILBOX_CONTENT_MIGRATION
import com.infomaniak.mail.data.cache.migrations.MAILBOX_INFO_MIGRATION
import com.infomaniak.mail.data.cache.migrations.USER_INFO_MIGRATION
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.SwissTransferContainer
import com.infomaniak.mail.data.models.SwissTransferFile
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.CalendarEvent
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.data.models.mailbox.SenderDetails
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Body
import com.infomaniak.mail.data.models.message.EmojiReactionAuthor
import com.infomaniak.mail.data.models.message.EmojiReactionState
import com.infomaniak.mail.data.models.message.Headers
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.message.SubBody
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.LocalStorageUtils
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.RealmObject
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

@Suppress("ObjectPropertyName")
object RealmDatabase {

    val TAG: String = RealmDatabase::class.java.simpleName

    //region Realms
    private var _appSettings: Realm? = null
    private var _userInfo: Realm? = null
    private var _mailboxContent: Realm? = null

    private var oldUserInfo = WeakReference<Realm?>(null)
    private var oldMailboxContent = WeakReference<Realm?>(null)
    //endregion

    //region Realms' mutexes
    private val appSettingsMutex = Mutex()
    private val userInfoMutex = Mutex()
    private val mailboxContentMutex = Mutex()
    //endregion

    //region Open Realms
    fun appSettings(): Realm = runBlocking(Dispatchers.IO) {
        appSettingsMutex.withLock {
            _appSettings ?: openRealmOrDropDbAndReboot(RealmConfig.appSettings).also { _appSettings = it }
        }
    }

    fun userInfo(): Realm = runBlocking(Dispatchers.IO) {
        userInfoMutex.withLock {
            _userInfo ?: openRealmOrDropDbAndReboot(RealmConfig.userInfo).also { _userInfo = it }
        }
    }

    val mailboxInfo get() = openRealmOrDropDbAndReboot(RealmConfig.mailboxInfo)

    val newMailboxContentInstance get() = newMailboxContentInstance(AccountUtils.currentUserId, AccountUtils.currentMailboxId)
    fun newMailboxContentInstance(userId: Int, mailboxId: Int, loadDataInMemory: Boolean = false): Realm {
        return openRealmOrDropDbAndReboot(RealmConfig.mailboxContent(userId, mailboxId, loadDataInMemory))
    }

    /**
     * When the user has an error like a migration error that prevents from opening a realm database rendering the app unusable,
     * at least delete the realm and reboot the app so the user will be able to access the app even if it means downloading all
     * messages again from zero.
     *
     * Realm doesn't seem to let us simply delete the realm and reopen it so we needed to find another alternative like rebooting
     * the app. Anyway, this heavy operation should only affect users as a last resort when we can't recover in any other way.
     */
    private fun openRealmOrDropDbAndReboot(configuration: RealmConfiguration): Realm = runCatching {
        Realm.open(configuration)
    }.onFailure {
        if (BuildConfig.DEBUG.not()) {
            Sentry.captureException(it)

            File(configuration.path).delete()
            runBlocking { AccountUtils.reloadApp?.invoke() }
        }
    }.getOrThrow()

    open class MailboxContent {
        open operator fun invoke() = runBlocking(Dispatchers.IO) {
            mailboxContentMutex.withLock {
                _mailboxContent ?: newMailboxContentInstance.also { _mailboxContent = it }
            }
        }
    }
    //endregion

    //region Close Realms
    fun closeOldRealms() {
        oldUserInfo.get()?.let {
            if (!it.isClosed()) it.close()
        }
        oldMailboxContent.get()?.let {
            if (!it.isClosed()) it.close()
        }
    }

    private fun closeUserInfo() {
        _userInfo?.close()
        resetUserInfo()
    }

    fun closeMailboxContent() {
        _mailboxContent?.close()
        resetMailboxContent()
    }
    //endregion

    //region Reset Realms
    fun backupPreviousRealms() {
        oldUserInfo = WeakReference(_userInfo)
        backupPreviousMailboxContent()
    }

    fun backupPreviousMailboxContent() {
        oldMailboxContent = WeakReference(_mailboxContent)
    }

    fun resetUserInfo() {
        _userInfo = null
    }

    fun resetMailboxContent() {
        _mailboxContent = null
    }
    //endregion

    //region Delete Realm
    fun deleteMailboxContent(mailboxId: Int, userId: Int = AccountUtils.currentUserId) {
        Realm.deleteRealm(RealmConfig.mailboxContent(userId, mailboxId))
    }

    fun removeUserData(context: Context, userId: Int) {
        closeMailboxContent()
        closeUserInfo()
        deleteUserFiles(context, userId)
    }

    private fun deleteUserFiles(context: Context, userId: Int) {
        LocalStorageUtils.deleteUserData(context, userId)
        context.filesDir.listFiles()?.forEach { file ->
            val isMailboxContent = file.name.startsWith(RealmConfig.mailboxContentDbNamePrefix(userId))
            val isUserInfo = file.name.startsWith(RealmConfig.userInfoDbName(userId))
            if (isMailboxContent || isUserInfo) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }
    //endregion

    private object RealmConfig {

        //region Configurations versions
        const val USER_INFO_SCHEMA_VERSION = 5L
        const val MAILBOX_INFO_SCHEMA_VERSION = 13L
        const val MAILBOX_CONTENT_SCHEMA_VERSION = 36L
        //endregion

        //region Configurations names
        const val appSettingsDbName = "AppSettings.realm"
        const val mailboxInfoDbName = "MailboxInfo.realm"
        fun userInfoDbName(userId: Int) = "User-${userId}.realm"
        fun mailboxContentDbNamePrefix(userId: Int) = "Mailbox-${userId}-"
        fun mailboxContentDbName(userId: Int, mailboxId: Int) = "${mailboxContentDbNamePrefix(userId)}${mailboxId}.realm"
        //endregion

        //region Configurations sets
        val appSettingsSet = setOf(
            AppSettings::class,
        )
        val userInfoSet: Set<KClass<out RealmObject>> = setOf(
            AddressBook::class,
            ContactGroup::class,
            MergedContact::class,
        )
        val mailboxInfoSet = setOf(
            Mailbox::class,
            SendersRestrictions::class,
            SenderDetails::class,
            MailboxPermissions::class,
            Quotas::class,
            Signature::class,
        )
        val mailboxContentSet = setOf(
            Folder::class,
            Thread::class,
            Message::class,
            Headers::class,
            Draft::class,
            Recipient::class,
            Body::class,
            SubBody::class,
            Attachment::class,
            CalendarEventResponse::class,
            CalendarEvent::class,
            SwissTransferContainer::class,
            SwissTransferFile::class,
            Attendee::class,
            Bimi::class,
            EmojiReactionState::class,
            EmojiReactionAuthor::class,
        )
        //endregion

        //region Configurations
        val appSettings = RealmConfiguration
            .Builder(appSettingsSet)
            .name(appSettingsDbName)
            .build()

        val userInfo
            get() = RealmConfiguration
                .Builder(userInfoSet)
                .name(userInfoDbName(AccountUtils.currentUserId))
                .schemaVersion(USER_INFO_SCHEMA_VERSION)
                .migration(USER_INFO_MIGRATION)
                .build()

        val mailboxInfo = RealmConfiguration
            .Builder(mailboxInfoSet)
            .name(mailboxInfoDbName)
            .schemaVersion(MAILBOX_INFO_SCHEMA_VERSION)
            .migration(MAILBOX_INFO_MIGRATION)
            .build()

        fun mailboxContent(userId: Int, mailboxId: Int, loadDataInMemory: Boolean = false): RealmConfiguration {

            if (mailboxId <= -1) { // DEFAULT_ID
                Sentry.captureMessage("RealmConfiguration wrongly used Mailbox DEFAULT_ID") { scope ->
                    scope.level = SentryLevel.ERROR
                    scope.setTag("mailboxId", "$mailboxId")
                }
            }

            return RealmConfiguration.Builder(mailboxContentSet)
                .name(mailboxContentDbName(userId, mailboxId))
                .schemaVersion(MAILBOX_CONTENT_SCHEMA_VERSION)
                .loadDataInMemoryIfNeeded(loadDataInMemory)
                .migration(MAILBOX_CONTENT_MIGRATION)
                .build()
        }

        private fun RealmConfiguration.Builder.loadDataInMemoryIfNeeded(loadDataInMemory: Boolean): RealmConfiguration.Builder {
            return apply { if (loadDataInMemory) inMemory() }
        }
        //endregion
    }
}
