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
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder

object Utils {

    fun String?.getFormattedThreadSubject(context: Context): Spanned {
        return this?.replace("\n+".toRegex(), " ")?.toSpanned()
            ?: HtmlCompat.fromHtml("<i>${context.getString(R.string.noSubjectTitle)}</i>", HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    fun List<Folder>.formatFoldersListWithAllChildren(): List<Folder> {

        if (isEmpty()) return this

        tailrec fun formatFolderWithAllChildren(
            inputList: MutableList<Folder>,
            outputList: MutableList<Folder> = mutableListOf(),
        ): List<Folder> {

            val firstFolder = inputList.removeFirst()
            outputList.add(firstFolder)
            inputList.addAll(0, firstFolder.children)

            return if (inputList.isEmpty()) outputList else formatFolderWithAllChildren(inputList, outputList)
        }

        return formatFolderWithAllChildren(toMutableList())
    }
}
