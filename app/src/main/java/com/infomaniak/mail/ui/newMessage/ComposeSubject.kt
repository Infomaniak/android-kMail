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
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.google.android.material.R
import com.google.android.material.textfield.TextInputEditText

/**
 * Allows IME behavior for multiline text fields to be overridden so that
 * pressing next/enter will take the user to the body field instead of
 * creating a newline in this field. Based on UnifiedEmail
 */
class ComposeSubject @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        val imeActions = outAttrs.imeOptions and EditorInfo.IME_MASK_ACTION
        if (imeActions and EditorInfo.IME_ACTION_NEXT != 0) {
            // clear the existing action
            outAttrs.imeOptions = outAttrs.imeOptions xor imeActions
            // set the NEXT action
            outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_ACTION_NEXT
        }
        if (outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            outAttrs.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
        }
        return connection
    }
}
