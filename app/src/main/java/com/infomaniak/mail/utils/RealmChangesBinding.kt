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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import io.realm.kotlin.notifications.*
import io.realm.kotlin.types.BaseRealmObject

class RealmChangesBinding<T : BaseRealmObject, VH : RecyclerView.ViewHolder> private constructor(
    private val lifecycleOwner: LifecycleOwner,
    private val recyclerViewAdapter: RecyclerView.Adapter<VH>,
    private var resultsChangeLiveData: LiveData<ResultsChange<T>>? = null,
    private var listChangeLiveData: LiveData<ListChange<T>>? = null,
) {
    private var onRealmChanged: OnRealmChanged<T>

    var beforeUpdateAdapter: ((itemList: List<T>) -> Unit)? = null
    var afterUpdateAdapter: (() -> Unit)? = null

    init {
        onRealmChanged = recyclerViewAdapter as OnRealmChanged<T>
    }

    private val resultsChangeObserver: (ResultsChange<T>) -> Unit = { resultsChange ->
        beforeUpdateAdapter?.invoke(resultsChange.list)
        when (resultsChange) {
            is InitialResults -> { // First call
                realmInitial(resultsChange.list)
            }
            is UpdatedResults -> { // Any update
                onRealmChanged.updateList(resultsChange.list)
                resultsChange.deletionRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeRemoved(it.startIndex, it.length)
                }
                resultsChange.insertionRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeInserted(it.startIndex, it.length)
                }
                resultsChange.changeRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeChanged(it.startIndex, it.length)
                }
            }
        }
        afterUpdateAdapter?.invoke()
    }

    private val listChangeObserver: (ListChange<T>) -> Unit = { listChange ->
        beforeUpdateAdapter?.invoke(listChange.list)
        when (listChange) {
            is InitialList -> { // First call
                realmInitial(listChange.list)
            }
            is UpdatedList -> { // Any update
                onRealmChanged.updateList(listChange.list)
                listChange.deletionRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeRemoved(it.startIndex, it.length)
                }
                listChange.insertionRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeInserted(it.startIndex, it.length)
                }
                listChange.changeRanges.forEach {
                    recyclerViewAdapter.notifyItemRangeChanged(it.startIndex, it.length)
                }
            }
            is DeletedList -> { // Parent has been deleted
                onRealmChanged.deleteList()
                recyclerViewAdapter.notifyItemRangeRemoved(0, listChange.list.count())
            }
        }
        afterUpdateAdapter?.invoke()
    }

    fun bindResultsChange(resultsChangeLiveData: LiveData<ResultsChange<T>>) {
        this.resultsChangeLiveData = resultsChangeLiveData
        this.resultsChangeLiveData?.observe(lifecycleOwner, resultsChangeObserver)
    }

    fun bindListChange(listChangeLiveData: LiveData<ListChange<T>>) {
        this.listChangeLiveData = listChangeLiveData
        this.listChangeLiveData?.observe(lifecycleOwner, listChangeObserver)
    }

    fun clear() {
        resultsChangeLiveData?.removeObserver(resultsChangeObserver)
        listChangeLiveData?.removeObserver(listChangeObserver)
    }

    private fun realmInitial(itemList: List<T>) {
        onRealmChanged.updateList(itemList)
        recyclerViewAdapter.notifyDataSetChanged()
    }

    interface OnRealmChanged<T> {
        fun updateList(itemList: List<T>)
        fun deleteList() = Unit
    }

    companion object {
        fun <VH : RecyclerView.ViewHolder, T : BaseRealmObject> LiveData<ResultsChange<T>>.bindResultsChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: RecyclerView.Adapter<VH>
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindResultsChange(this) }
        }

        fun <VH : RecyclerView.ViewHolder, T : BaseRealmObject> LiveData<ListChange<T>>.bindListChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: RecyclerView.Adapter<VH>
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindListChange(this) }
        }
    }
}