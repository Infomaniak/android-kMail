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

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewbinding.ViewBinding

fun ViewBinding.applyWindowInsetsListener(
    shouldConsume: Boolean = true,
    listener: (rootView: View, insets: WindowInsetsCompat) -> Unit,
) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        listener(view, insets)
        if (shouldConsume) WindowInsetsCompat.CONSUMED else insets
    }
}

fun View.applyStatusBarInsets(insets: WindowInsetsCompat) {
    val statusBar = insets.statusBar()
    updatePadding(top = statusBar.top, left = statusBar.left, right = statusBar.right, bottom = statusBar.bottom)
}

fun View.applySideAndBottomSystemInsets(insets: WindowInsetsCompat) {
    val systemBars = insets.systemBars()
    updatePadding(left = systemBars.left, right = systemBars.right, bottom = systemBars.bottom)
}

fun WindowInsetsCompat.statusBar() = getInsets(WindowInsetsCompat.Type.statusBars())
fun WindowInsetsCompat.systemBars() = getInsets(WindowInsetsCompat.Type.systemBars())
fun WindowInsetsCompat.ime() = getInsets(WindowInsetsCompat.Type.ime())
