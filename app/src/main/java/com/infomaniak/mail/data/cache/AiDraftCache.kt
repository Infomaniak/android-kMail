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
package com.infomaniak.mail.data.cache

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDraftCache @Inject constructor() {
    var pendingAiSubject: String? = null
        private set
    var pendingAiContent: String? = null
        private set

    fun setAiDraft(subject: String?, content: String?) {
        pendingAiSubject = subject
        pendingAiContent = content
    }

    fun reset() {
        pendingAiSubject = null
        pendingAiContent = null
    }

}
