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
package com.infomaniak.mail.data.models.thread

import com.infomaniak.mail.MatomoMail

enum class ThreadFilter(val matomoName: MatomoMail.MatomoName) {
    ALL(MatomoMail.MatomoName.FolderFilter),
    SEEN(MatomoMail.MatomoName.ReadFilter),
    UNSEEN(MatomoMail.MatomoName.UnreadFilter),
    STARRED(MatomoMail.MatomoName.FavoriteFilter),
    ATTACHMENTS(MatomoMail.MatomoName.AttachmentFilter),
    FOLDER(MatomoMail.MatomoName.FolderFilter),
}
