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
package com.infomaniak.mail.ui.main.newMessage

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.databinding.ViewAiPromptBinding

class AiPromptView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewAiPromptBinding by lazy { ViewAiPromptBinding.inflate(LayoutInflater.from(context), this, true) }

    private var onCloseCallback: (() -> Unit)? = null

    init {
        binding.closeButton.setOnClickListener { onCloseCallback?.invoke() }
    }

    fun initListeners(onClosed: () -> Unit) {
        onCloseCallback = onClosed
    }

    fun focusPrompt() {
        binding.prompt.showKeyboard()
    }
}
