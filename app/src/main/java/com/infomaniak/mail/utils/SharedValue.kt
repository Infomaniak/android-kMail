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
package com.infomaniak.mail.utils

import android.content.SharedPreferences
import com.infomaniak.lib.core.utils.Utils
import com.infomaniak.lib.core.utils.transaction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


fun SharedPreferences.sharedValue(key: String, defaultValue: Int): ReadWriteProperty<Any, Int> {
    return object : ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Int = getInt(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) = transaction { putInt(key, value) }
    }
}

fun SharedPreferences.sharedValue(key: String, defaultValue: Boolean): ReadWriteProperty<Any, Boolean> {
    return object : ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean = getBoolean(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) = transaction { putBoolean(key, value) }
    }
}

fun SharedPreferences.sharedValue(key: String, defaultValue: Float): ReadWriteProperty<Any, Float> {
    return object : ReadWriteProperty<Any, Float> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Float = getFloat(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) = transaction { putFloat(key, value) }
    }
}

fun SharedPreferences.sharedValue(key: String, defaultValue: Long): ReadWriteProperty<Any, Long> {
    return object : ReadWriteProperty<Any, Long> {
        override fun getValue(thisRef: Any, property: KProperty<*>): Long = getLong(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) = transaction { putLong(key, value) }
    }
}

fun SharedPreferences.sharedValue(key: String, defaultValue: String): ReadWriteProperty<Any, String> {
    return object : ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String = getString(key, defaultValue) ?: defaultValue
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String) = transaction { putString(key, value) }
    }
}

fun SharedPreferences.sharedValueNullable(key: String, defaultValue: String?): ReadWriteProperty<Any, String?> {
    return object : ReadWriteProperty<Any, String?> {
        override fun getValue(thisRef: Any, property: KProperty<*>): String? = getString(key, defaultValue)
        override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) = transaction { putString(key, value) }
    }
}

fun SharedPreferences.sharedValue(key: String, defaultValue: Set<String>): ReadWriteProperty<Any, Set<String>> {
    return object : ReadWriteProperty<Any, Set<String>> {
        override fun getValue(thisRef: Any, property: KProperty<*>) = getStringSet(key, defaultValue) ?: defaultValue
        override fun setValue(thisRef: Any, property: KProperty<*>, value: Set<String>) = transaction { putStringSet(key, value) }
    }
}

inline fun <reified E : Enum<E>> SharedPreferences.sharedValue(
    key: String,
    defaultValue: E
): ReadWriteProperty<Any, E> {
    return object : ReadWriteProperty<Any, E> {
        override fun getValue(thisRef: Any, property: KProperty<*>): E {
            return Utils.enumValueOfOrNull<E>(getString(key, defaultValue.name)) ?: defaultValue
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: E) {
            transaction { putString(key, value.name) }
        }
    }
}
