/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.search

import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.setPaddingRelative
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemSearchFolderBinding
import com.infomaniak.mail.utils.getLocalizedNameOrAllFolders

class SearchFolderAdapter(
    val folders: List<Folder?>,
    val onClickListener: (folder: Folder?, title: String) -> Unit
) : ListAdapter {

    val allFolderMargin by lazy { 42.toPx() }
    val iconFolderMargin by lazy { 12.toPx() }

    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit

    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

    override fun getCount(): Int = folders.count()

    override fun getItem(position: Int): Any? = folders[position]

    override fun getItemId(position: Int): Long = folders[position]?.id.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val context = parent!!.context
        (convertView ?: ItemSearchFolderBinding.inflate(LayoutInflater.from(context), parent, false).root).apply {
            findViewById<MaterialButton>(R.id.folderButton).apply {
                val folder = folders[position]

                val entryName: String = folder.getLocalizedNameOrAllFolders(context)
                text = entryName
                setIconResource(folder?.getIcon() ?: 0)
                setPaddingRelative(if (folder == null) allFolderMargin else iconFolderMargin)

                setOnClickListener { onClickListener(folder, entryName) }
            }
            return this
        }
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun getViewTypeCount(): Int = 1

    override fun isEmpty(): Boolean = folders.isEmpty()

    // TODO : add separators between common and custom folders ?
    override fun areAllItemsEnabled(): Boolean = true

    override fun isEnabled(position: Int): Boolean = true
}
