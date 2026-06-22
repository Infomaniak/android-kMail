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
@file:OptIn(InternalModelProperties::class)

package com.infomaniak.mail.data.models.extensions

import android.content.Context
import com.infomaniak.core.common.FormatterFileSize.formatShortFileSize
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.InternalModelProperties
import com.infomaniak.mail.data.models.Quotas

fun Quotas.getText(context: Context): String {

    val usedSize = context.formatShortFileSize(size)
    val maxSize = maxStorage?.let { context.formatShortFileSize(it) }

    return context.getString(R.string.menuDrawerMailboxStorage, usedSize, maxSize)
}
