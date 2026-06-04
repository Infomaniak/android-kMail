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
package com.infomaniak.mail.data.models.extensions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.RefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.defaultRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.inboxRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.scheduledDraftRefreshStrategy
import com.infomaniak.mail.data.cache.mailboxContent.refreshStrategies.snoozeRefreshStrategy
import com.infomaniak.mail.data.models.FolderRole

val FolderRole.folderNameRes: Int
    @StringRes
    get() = when (this) {
        FolderRole.INBOX -> R.string.inboxFolder
        FolderRole.COMMERCIAL -> R.string.commercialFolder
        FolderRole.SOCIALNETWORKS -> R.string.socialNetworksFolder
        FolderRole.SENT -> R.string.sentFolder
        FolderRole.SNOOZED -> R.string.snoozedFolder
        FolderRole.SCHEDULED_DRAFTS -> R.string.scheduledMessagesFolder
        FolderRole.DRAFT -> R.string.draftFolder
        FolderRole.SPAM -> R.string.spamFolder
        FolderRole.TRASH -> R.string.trashFolder
        FolderRole.ARCHIVE -> R.string.archiveFolder
    }

val FolderRole.folderIconRes: Int
    @DrawableRes
    get() = when (this) {
        FolderRole.INBOX -> R.drawable.ic_drawer_inbox
        FolderRole.COMMERCIAL -> R.drawable.ic_promotions
        FolderRole.SOCIALNETWORKS -> R.drawable.ic_social_media
        FolderRole.SENT -> R.drawable.ic_send
        FolderRole.SNOOZED -> R.drawable.ic_alarm_clock
        FolderRole.SCHEDULED_DRAFTS -> R.drawable.ic_schedule_send
        FolderRole.DRAFT -> R.drawable.ic_draft
        FolderRole.SPAM -> R.drawable.ic_spam
        FolderRole.TRASH -> R.drawable.ic_bin
        FolderRole.ARCHIVE -> R.drawable.ic_archive_folder
    }

val FolderRole.matomoName: MatomoMail.MatomoName
    get() = when (this) {
        FolderRole.INBOX -> MatomoMail.MatomoName.InboxFolder
        FolderRole.COMMERCIAL -> MatomoMail.MatomoName.CommercialFolder
        FolderRole.SOCIALNETWORKS -> MatomoMail.MatomoName.SocialNetworksFolder
        FolderRole.SENT -> MatomoMail.MatomoName.SentFolder
        FolderRole.SNOOZED -> MatomoMail.MatomoName.SnoozedFolder
        FolderRole.SCHEDULED_DRAFTS -> MatomoMail.MatomoName.ScheduledDraftsFolder
        FolderRole.DRAFT -> MatomoMail.MatomoName.DraftFolder
        FolderRole.SPAM -> MatomoMail.MatomoName.SpamFolder
        FolderRole.TRASH -> MatomoMail.MatomoName.TrashFolder
        FolderRole.ARCHIVE -> MatomoMail.MatomoName.ArchiveFolder
    }

val FolderRole.refreshStrategy: RefreshStrategy
    get() = when (this) {
        FolderRole.INBOX -> inboxRefreshStrategy
        FolderRole.COMMERCIAL -> defaultRefreshStrategy
        FolderRole.SOCIALNETWORKS -> defaultRefreshStrategy
        FolderRole.SENT -> defaultRefreshStrategy
        FolderRole.SNOOZED -> snoozeRefreshStrategy
        FolderRole.SCHEDULED_DRAFTS -> scheduledDraftRefreshStrategy
        FolderRole.DRAFT -> defaultRefreshStrategy
        FolderRole.SPAM -> defaultRefreshStrategy
        FolderRole.TRASH -> defaultRefreshStrategy
        FolderRole.ARCHIVE -> defaultRefreshStrategy
    }
