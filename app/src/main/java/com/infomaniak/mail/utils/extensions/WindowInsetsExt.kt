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
import androidx.core.graphics.Insets
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

fun View.applyStatusBarInsets(insets: WindowInsetsCompat) = with(insets.safeArea()) {
    updatePadding(top = top, left = left, right = right)
    this@applyStatusBarInsets
}

fun View.applySideAndBottomSystemInsets(
    insets: WindowInsetsCompat,
    withTop: Boolean = false,
    withSides: Boolean = true,
    withBottom: Boolean = true,
) = with(insets.safeArea()) {
    updatePadding(
        left = if (withSides) left else 0,
        top = if (withTop) top else 0,
        right = if (withSides) right else 0,
        bottom = if (withBottom) bottom else 0,
    )
    this@applySideAndBottomSystemInsets
}

fun View.applyImeInsets(insets: WindowInsetsCompat) = with(insets.safeArea()) {
    updatePadding(left = left, top = top, right = right, bottom = bottom)
    this@applyImeInsets
}

fun WindowInsetsCompat.cutout() = getInsets(WindowInsetsCompat.Type.displayCutout())
fun WindowInsetsCompat.statusBar() = getInsets(WindowInsetsCompat.Type.statusBars())
fun WindowInsetsCompat.systemBars() = getInsets(WindowInsetsCompat.Type.systemBars())
fun WindowInsetsCompat.ime() = getInsets(WindowInsetsCompat.Type.ime())
fun WindowInsetsCompat.safeArea() = Insets.max(systemBars(), cutout())
