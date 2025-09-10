/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.utils.extensions

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmElementQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke

suspend inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
    write { update<T>(items) }
}

inline fun <reified T : RealmObject> MutableRealm.update(items: List<RealmObject>) {
    delete(query<T>())
    copyListToRealm(items)
}

// There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
fun MutableRealm.copyListToRealm(items: List<RealmObject>, alsoCopyManagedItems: Boolean = true) {
    items.forEach { if (alsoCopyManagedItems || !it.isManaged()) copyToRealm(it, UpdatePolicy.ALL) }
}

inline fun <reified T> RealmList<T>.replaceContent(list: List<T>) {
    clear()
    addAll(list.toRealmList())
}

suspend fun <T : BaseRealmObject> RealmElementQuery<T>.findSuspend(): RealmResults<T> {
    return Dispatchers.IO { find() }
    // We are NOT using Realm's `asFlow().map { it.list }.first()` because it is less performant.
}

suspend fun <T> RealmScalarQuery<T>.findSuspend(): T {
    return Dispatchers.IO { find() }
    // We are NOT using Realm's `asFlow().map { it.list }.first()` because it is less performant.
}

suspend fun <T : BaseRealmObject> RealmSingleQuery<T>.findSuspend(): T? {
    return Dispatchers.IO { find() }
    // We are NOT using Realm's `asFlow().map { it.list }.first()` because it is less performant.
}
