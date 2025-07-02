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
package com.infomaniak.mail.utils

import android.content.res.ColorStateList
import android.util.StateSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

inline fun colorStateList(block: ColorStateListBuilder.() -> Unit): ColorStateList {
    return ColorStateListBuilder().apply(block).build()
}

class ColorStateListBuilder @PublishedApi internal constructor() {

    private val entries = mutableListOf<Entry>()

    fun addForStates(@AttrRes vararg stateSet: Int, @ColorInt color: Int) {
        entries.add(Entry(stateSet, color))
    }

    fun addForState(@AttrRes state: Int, @ColorInt color: Int) {
        entries.add(Entry(intArrayOf(state), color))
    }

    fun addForRemainingStates(@ColorInt color: Int) {
        entries.add(Entry(StateSet.WILD_CARD, color))
    }

    @PublishedApi
    internal fun build(): ColorStateList {
        return ColorStateList(
            /* states = */ Array(entries.size) { i -> entries[i].stateSet },
            /* colors = */ IntArray(entries.size) { i -> entries[i].color },
        )
    }

    private class Entry(
        @AttrRes val stateSet: IntArray,
        @ColorInt val color: Int,
    )
}
