/*
 * Infomaniak Mail - Android
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
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.*
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
 * we can override the [OnRealmChanged.realmAsyncListDiffer].
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
class RealmChangesBinding<T : BaseRealmObject, VH : ViewHolder> private constructor(
    private val lifecycleOwner: LifecycleOwner,
    private val recyclerViewAdapter: Adapter<VH>,
    private var resultsChangeLiveData: LiveData<ResultsChange<T>>? = null,
    private var listChangeLiveData: LiveData<ListChange<T>>? = null,
) {

    private var onRealmChanged: OnRealmChanged<T>

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

        fun notifyAdapter() {
            notifyAdapter(list)
            notifyAfterUpdate(list)
        }

        beforeUpdateAdapter?.invoke(list)

        when (resultsChange) {

            is InitialResults -> { // First call
                notifyAdapter()
            }

            is UpdatedResults -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting {
                    notifyAdapter()
                } ?: run {
                    notifyAdapter()
                }
            }

        }
    }

    private val listChangeObserver: (ListChange<T>) -> Unit = { listChange ->

        val list = listChange.list

        fun notifyAdapter() {
            notifyAdapter(list)
            notifyAfterUpdate(list)
        }

        beforeUpdateAdapter?.invoke(list)

        when (listChange) {

            is InitialList -> { // First call
                notifyAdapter()
            }

            is UpdatedList -> { // Any update
                waitingBeforeNotifyAdapter?.observeWaiting {
                    notifyAdapter()
                } ?: run {
                    notifyAdapter()
                }
            }

            is DeletedList -> { // Parent has been deleted
                onRealmChanged.deleteList()
                recyclerViewAdapter.notifyItemRangeRemoved(0, list.count())
                notifyAfterUpdate(list)
            }

        }
    }

    private fun notifyAfterUpdate(list: List<T>) {
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

    private fun notifyAdapter(itemList: List<T>) {
        onRealmChanged.updateList(itemList)
        onRealmChanged.realmAsyncListDiffer?.submitList(itemList)
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

        val realmAsyncListDiffer: AsyncListDiffer<T>?

        fun updateList(itemList: List<T>) = Unit

        fun deleteList() = Unit
    }

    companion object {
        fun <VH : ViewHolder, T : BaseRealmObject> LiveData<ResultsChange<T>>.bindResultsChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: Adapter<VH>,
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindResultsChange(this) }
        }

        fun <VH : ViewHolder, T : BaseRealmObject> LiveData<ListChange<T>>.bindListChangeToAdapter(
            lifecycleOwner: LifecycleOwner,
            recyclerViewAdapter: Adapter<VH>,
        ): RealmChangesBinding<T, VH> {
            return RealmChangesBinding<T, VH>(lifecycleOwner, recyclerViewAdapter).also { it.bindListChange(this) }
        }
    }
}
