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

    var waitingBeforeNotifyAdapter: LiveData<Boolean>? = null
    var beforeUpdateAdapter: ((itemList: List<T>) -> Unit)? = null
    var afterUpdateAdapter: ((itemList: List<T>) -> Unit)? = null

    init {
        @Suppress("UNCHECKED_CAST")
        onRealmChanged = recyclerViewAdapter as OnRealmChanged<T>
    }

    private val resultsChangeObserver: (ResultsChange<T>) -> Unit = { resultsChange ->
        beforeUpdateAdapter?.invoke(resultsChange.list)
        when (resultsChange) {
            is InitialResults -> { // First call
                realmInitial(resultsChange.list)
            }
            is UpdatedResults -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting { resultsChange.notifyAdapter() } ?: resultsChange.notifyAdapter()
            }
        }
        afterUpdateAdapter?.invoke(resultsChange.list)
    }

    private val listChangeObserver: (ListChange<T>) -> Unit = { listChange ->
        beforeUpdateAdapter?.invoke(listChange.list)
        when (listChange) {
            is InitialList -> { // First call
                realmInitial(listChange.list)
            }
            is UpdatedList -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting { listChange.notifyAdapter() } ?: listChange.notifyAdapter()
            }
            is DeletedList -> { // Parent has been deleted
                onRealmChanged.deleteList()
                recyclerViewAdapter.notifyItemRangeRemoved(0, listChange.list.count())
            }
        }
        afterUpdateAdapter?.invoke(listChange.list)
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
        waitingBeforeNotifyAdapter?.removeObservers(lifecycleOwner)
    }

    private fun realmInitial(itemList: List<T>) {
        onRealmChanged.updateList(itemList)
        recyclerViewAdapter.notifyDataSetChanged()
    }

    private fun UpdatedResults<T>.notifyAdapter() {
        onRealmChanged.updateList(list)
        notifyItemRanges()
    }

    private fun UpdatedList<T>.notifyAdapter() {
        onRealmChanged.updateList(list)
        notifyItemRanges()
    }

    private fun ListChangeSet.notifyItemRanges() {
        deletionRanges.forEach { recyclerViewAdapter.notifyItemRangeRemoved(it.startIndex, it.length) }
        insertionRanges.forEach { recyclerViewAdapter.notifyItemRangeInserted(it.startIndex, it.length) }
        changeRanges.forEach { recyclerViewAdapter.notifyItemRangeChanged(it.startIndex, it.length) }
    }

    private fun LiveData<Boolean>.observeWaiting(whenCanNotify: () -> Unit) {
        observe(lifecycleOwner) { canNotify ->
            if (canNotify) {
                onRealmChanged.recyclerView.postOnAnimation { whenCanNotify() }
                waitingBeforeNotifyAdapter?.removeObservers(lifecycleOwner)
            }
        }
    }

    interface OnRealmChanged<T> {
        var recyclerView: RecyclerView
        fun updateList(itemList: List<T>)
        fun deleteList() = Unit
    }

    companion object {
        fun <VH : RecyclerView.ViewHolder, T : BaseRealmObject> LiveData<ResultsChange<T>>.bindResultsChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: RecyclerView.Adapter<VH>,
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindResultsChange(this) }
        }

        fun <VH : RecyclerView.ViewHolder, T : BaseRealmObject> LiveData<ListChange<T>>.bindListChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: RecyclerView.Adapter<VH>,
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindListChange(this) }
        }
    }
}
