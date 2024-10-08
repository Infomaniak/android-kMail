/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import com.google.android.material.chip.Chip

class BackspaceAwareChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Chip(context, attrs) {

    private var onBackspace: () -> Unit = {}

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (isInTouchMode && focused) {
            performClick()
            clearFocus()
        } else {
            super.onFocusChanged(focused, direction, previouslyFocusedRect)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) onBackspace()
        return super.onKeyDown(keyCode, event)
    }

    fun setOnBackspaceListener(listener: () -> Unit) {
        onBackspace = listener
    }
}
