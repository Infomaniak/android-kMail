/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.content.Context
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder

object Utils {

    fun String?.getFormattedThreadSubject(context: Context): Spanned {
        return this?.replace("\n+".toRegex(), " ")?.toSpanned()
            ?: HtmlCompat.fromHtml("<i>${context.getString(R.string.noSubjectTitle)}</i>", HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    fun List<Folder>.formatFoldersListWithAllChildren(): List<Folder> {

        fun formatFolderWithAllChildren(parent: Folder): List<Folder> {
            return mutableListOf<Folder>().apply {
                add(parent)
                parent.children.forEach { child ->
                    child.parentLink = parent
                    addAll(formatFolderWithAllChildren(child))
                }
            }
        }

        return map(::formatFolderWithAllChildren).flatten()
    }

    class PairTrigger<A, B>(a: LiveData<A>, b: LiveData<B>) : MediatorLiveData<Pair<A?, B?>>() {
        init {
            addSource(a) { value = it to b.value }
            addSource(b) { value = a.value to it }
        }
    }
}
