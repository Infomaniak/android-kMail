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

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.mail.utils.RealmChangesBinding.OnRealmChanged
import io.realm.kotlin.notifications.*
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject

/**
 * The RealmChangesBinding class is an utility class for binding RecyclerView UI elements to Realm data.
 *
 * This adapter will automatically handle any updates to its data and call `notifyDataSetChanged()`,
 * `notifyItemInserted()`, `notifyItemRemoved()` or `notifyItemRangeChanged()` as appropriate.
 * In case there are changes but we want to notify them only if needed,
 * we can override the [OnRealmChanged.areContentsTheSame] method.
 *
 * The RealmAdapter will stop receiving updates if the Realm instance providing the [ResultsChange] or [ListChange] is
 * closed.
 *
 * If the adapter contains Realm model classes with a primary key that is either an [Int] or a [Long], call
 * `setHasStableIds(true)` in the constructor and override [RecyclerView.Adapter.getItemId] as described by the Javadoc in that method.
 *
 * @param T type of [RealmObject] stored in the adapter.
 * @param VH type of [RecyclerView.ViewHolder] used in the adapter.
 * @param lifecycleOwner the lifecycle owner of the adapter.
 * @param recyclerViewAdapter the [RecyclerView.Adapter] to bind to.
 * @param data [LiveData] of [ListChange] or [ResultsChange] to bind to the adapter.
 * @see RecyclerView.Adapter.setHasStableIds
 * @see RecyclerView.Adapter.getItemId
 */
class RealmChangesBinding<T : BaseRealmObject, VH : RecyclerView.ViewHolder> private constructor(
    private val lifecycleOwner: LifecycleOwner,
    private val recyclerViewAdapter: RecyclerView.Adapter<VH>,
    private var resultsChangeLiveData: LiveData<ResultsChange<T>>? = null,
    private var listChangeLiveData: LiveData<ListChange<T>>? = null,
) {

    private var onRealmChanged: OnRealmChanged<T>

    private var previousList = emptyList<T>()

    var recyclerView: RecyclerView? = null
    var waitingBeforeNotifyAdapter: LiveData<Boolean>? = null
    var beforeUpdateAdapter: ((itemList: List<T>) -> Unit)? = null
    var afterUpdateAdapter: ((itemList: List<T>) -> Unit)? = null

    init {
        @Suppress("UNCHECKED_CAST")
        onRealmChanged = recyclerViewAdapter as OnRealmChanged<T>
    }

    private val resultsChangeObserver: (ResultsChange<T>) -> Unit = { resultsChange ->

        val list = resultsChange.list

        beforeUpdateAdapter?.invoke(list)

        when (resultsChange) {

            is InitialResults -> { // First call
                realmInitial(list)
                afterUpdate(list)
            }

            is UpdatedResults -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting {
                    resultsChange.notifyAdapter()
                    afterUpdate(list)
                } ?: run {
                    resultsChange.notifyAdapter()
                    afterUpdate(list)
                }
            }

        }
    }

    private val listChangeObserver: (ListChange<T>) -> Unit = { listChange ->

        val list = listChange.list

        beforeUpdateAdapter?.invoke(list)

        when (listChange) {

            is InitialList -> { // First call
                realmInitial(list)
                afterUpdate(list)
            }

            is UpdatedList -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting {
                    listChange.notifyAdapter()
                    afterUpdate(list)
                } ?: run {
                    listChange.notifyAdapter()
                    afterUpdate(list)
                }
            }

            is DeletedList -> { // Parent has been deleted
                onRealmChanged.deleteList()
                recyclerViewAdapter.notifyItemRangeRemoved(0, list.count())
                afterUpdate(list)
            }

        }

        previousList = list
    }

    private fun afterUpdate(list: List<T>) {
        afterUpdateAdapter?.invoke(list)
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

    @SuppressLint("NotifyDataSetChanged")
    private fun realmInitial(itemList: List<T>) {
        onRealmChanged.updateList(itemList)
        recyclerViewAdapter.notifyDataSetChanged()
    }

    private fun UpdatedResults<T>.notifyAdapter() {
        onRealmChanged.updateList(list)
        notifyItemRanges(list)
    }

    private fun UpdatedList<T>.notifyAdapter() {
        onRealmChanged.updateList(list)
        notifyItemRanges(list)
    }

    private fun ListChangeSet.notifyItemRanges(newList: List<T>) {
        deletionRanges.forEach { recyclerViewAdapter.notifyItemRangeRemoved(it.startIndex, it.length) }
        insertionRanges.forEach { recyclerViewAdapter.notifyItemRangeInserted(it.startIndex, it.length) }
        changeRanges.forEach { changeRange ->
            if (previousList.isEmpty()) {
                recyclerViewAdapter.notifyItemRangeChanged(changeRange.startIndex, changeRange.length)
            } else {
                runCatching {
                    // We will avoid notifying each item in a row, instead we will notify by range while we can.
                    // To do this, we count the number of changes in a row, then as soon as we have a different element
                    // we notify the previous ones with a notification by range.
                    onlyNotifyChangesIfNeeded(changeRange, newList)
                }.onFailure {
                    // In case the `areContentsTheSame` method has not been overridden, then we notify all changes
                    recyclerViewAdapter.notifyItemRangeChanged(changeRange.startIndex, changeRange.length)
                }
            }
        }
    }

    private fun onlyNotifyChangesIfNeeded(changeRange: ListChangeSet.Range, newList: List<T>) {
        var start = changeRange.startIndex
        var count = 0
        for (index in changeRange.startIndex until changeRange.startIndex + changeRange.length) {
            if (onRealmChanged.areContentsTheSame(previousList[index], newList[index])) {
                // The content has not changed so there is no need to notify the adapter.
                // However, if we had changes previously, we will notify them.
                if (count > 0) {
                    recyclerViewAdapter.notifyItemRangeChanged(start, count)
                    count = 0
                }
            } else {
                // For the first change we get its index in start and then we count the number of changes
                if (count == 0) start = index
                count++
            }
        }
        // If we finish the iteration and we still have some modifications to notify, we treat them here
        if (count > 0) recyclerViewAdapter.notifyItemRangeChanged(start, count)
    }

    private fun LiveData<Boolean>.observeWaiting(whenCanNotify: () -> Unit) {
        observe(lifecycleOwner) { canNotify ->
            if (canNotify) {
                if (recyclerView == null) {
                    throw NullPointerException("You forgot to assign the `recyclerView` used in `waitingBeforeNotifyAdapter`.")
                } else {
                    recyclerView?.postOnAnimation { whenCanNotify() }
                }
                waitingBeforeNotifyAdapter?.removeObservers(lifecycleOwner)
            }
        }
    }

    interface OnRealmChanged<T> {
        fun updateList(itemList: List<T>)
        fun deleteList() = Unit
        fun areContentsTheSame(oldItem: T, newItem: T): Boolean = throw UnsupportedOperationException()
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
