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

import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import com.google.android.material.textfield.TextInputEditText

class BackspaceAwareTextInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {

    private var backspaceOnEmptyField: () -> Unit = {}
    private var onPasteIntercept: ((String) -> Boolean)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()) backspaceOnEmptyField()
        return super.onKeyDown(keyCode, event)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip

            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                if (onPasteIntercept?.invoke(pastedText) == true) {
                    return true
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    fun setBackspaceOnEmptyFieldListener(listener: () -> Unit) {
        backspaceOnEmptyField = listener
    }

    fun setOnPasteInterceptListener(listener: (String) -> Boolean) {
        onPasteIntercept = listener
    }
}
