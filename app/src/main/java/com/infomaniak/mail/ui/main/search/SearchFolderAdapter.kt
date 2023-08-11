/*
 * Infomaniak ikMail - Android
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

import android.content.Context
import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import androidx.appcompat.content.res.AppCompatResources
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.databinding.ItemDividerHorizontalBinding
import com.infomaniak.mail.databinding.ItemSearchFolderBinding
import com.infomaniak.mail.ui.main.search.SearchFolderAdapter.SearchFolderElement.DIVIDER
import com.infomaniak.mail.ui.main.search.SearchFolderAdapter.SearchFolderElement.FOLDER
import com.infomaniak.mail.utils.getLocalizedNameOrAllFolders
import com.infomaniak.mail.views.itemViews.SelectableFolderItemView

class SearchFolderAdapter(
    val folders: List<Any>,
    val onClickListener: (folder: Folder?, title: String) -> Unit,
) : ListAdapter {

    private var selectedFolder: Folder? = null

    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit

    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

    override fun getCount(): Int = folders.count()

    override fun getItem(position: Int): Any = folders[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val context = parent!!.context
        return when (getItemViewType(position)) {
            DIVIDER.itemId -> bindDivider(convertView, context, parent)
            else -> bindFolder(convertView, context, parent, getItem(position) as? Folder)
        }
    }

    private fun bindDivider(convertView: View?, context: Context?, parent: ViewGroup): View {
        return convertView ?: ItemDividerHorizontalBinding.inflate(LayoutInflater.from(context), parent, false).root.apply {
            setDividerColorResource(R.color.popupDividerColor)
        }
    }

    private fun bindFolder(
        convertView: View?,
        context: Context,
        parent: ViewGroup?,
        folder: Folder?,
    ): View {
        return (convertView ?: ItemSearchFolderBinding.inflate(LayoutInflater.from(context), parent, false).root).apply {
            findViewById<SelectableFolderItemView>(R.id.simpleFolderItemView).apply {
                val entryName: String = context.getLocalizedNameOrAllFolders(folder)
                text = entryName
                icon = AppCompatResources.getDrawable(context, folder?.getIcon() ?: R.drawable.ic_all_folders)

                setSelectedState(folder == selectedFolder)

                setOnClickListener { onClickListener(folder, entryName) }
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is SearchFolderElement -> item.itemId
        else -> FOLDER.itemId
    }

    override fun getViewTypeCount(): Int = 3

    override fun isEmpty(): Boolean = folders.isEmpty()

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = getItemViewType(position) != DIVIDER.itemId

    fun updateVisuallySelectedFolder(folder: Folder?) {
        selectedFolder = folder
    }

    enum class SearchFolderElement(val itemId: Int) {
        FOLDER(0),
        ALL_FOLDERS(1),
        DIVIDER(2),
    }
}
