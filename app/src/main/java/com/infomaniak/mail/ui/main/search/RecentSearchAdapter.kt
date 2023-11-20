/*
 * Infomaniak Mail - Android
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.mail.databinding.ItemRecentSearchBinding

class RecentSearchAdapter(
    private var searchQueries: MutableList<String>,
    private val onSearchQueryClicked: (searchQuery: String) -> Unit,
    private val onSearchQueryDeleted: (history: List<String>) -> Unit,
) : Adapter<RecentSearchAdapter.RecentSearchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        return RecentSearchViewHolder(ItemRecentSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) = with(holder.binding) {
        val searchQuery = searchQueries[position]
        searchQueryButton.text = searchQuery
        root.setOnClickListener { onSearchQueryClicked(searchQuery) }
        deleteButton.setOnClickListener {
            val searchQueryPosition = searchQueries.indexOf(searchQuery)
            if (searchQueryPosition >= 0) {
                searchQueries.removeAt(searchQueryPosition)
                onSearchQueryDeleted(searchQueries)
                notifyItemRemoved(searchQueryPosition)
            }
        }
    }

    override fun getItemCount(): Int = searchQueries.count()

    /**
     * Returns `true` if insertion is successful.
     */
    fun addSearchQuery(searchQuery: String): Boolean = with(searchQueries) {
        val previousCopyPosition = indexOf(searchQuery)

        if (previousCopyPosition == 0) {
            return@with false
        } else if (previousCopyPosition != -1) {
            // Place search query back at the top if it already exists instead of making it appear twice
            removeAt(previousCopyPosition)
            notifyItemRemoved(previousCopyPosition)
        }

        add(0, searchQuery)
        notifyItemInserted(0)

        while (count() > MAX_HISTORY_COUNT) {
            notifyItemRemoved(lastIndex)
            removeLast()
        }

        return true
    }

    fun getSearchQueries(): MutableList<String> = searchQueries

    class RecentSearchViewHolder(val binding: ItemRecentSearchBinding) : ViewHolder(binding.root)

    companion object {
        private const val MAX_HISTORY_COUNT = 10
    }
}
