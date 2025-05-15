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
package com.infomaniak.emojicomponents.views

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.annotation.StyleableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.AbstractComposeView
import com.infomaniak.core.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.emojicomponents.R
import com.infomaniak.emojicomponents.components.EmojiReactions
import com.infomaniak.emojicomponents.data.ReactionState
import com.infomaniak.emojicomponents.updateWithEmoji

class EmojiReactionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private class State(override val count: Int, override val hasReacted: Boolean) : ReactionState

    private val state = mutableStateMapOf<String, ReactionState>(
        "✅" to State(3, false),
        "\uD83D\uDCCC" to State(1, false),
        "\uD83D\uDE00" to State(5, true),
        "\uD83D\uDE0D" to State(3, false),
        "\uD83E\uDEE5" to State(2, true),
    )

    private var chipCornerRadius: Float? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.EmojiReactionsView, defStyleAttr, 0).apply {
            chipCornerRadius = getDimensionOrNull(R.styleable.EmojiReactionsView_chipCornerRadius)
            recycle()
        }
    }

    private fun TypedArray.getDimensionOrNull(@StyleableRes index: Int): Float? {
        return if (hasValue(index)) getDimension(index, -1f) else null
    }

    @Composable
    override fun Content() {
        MaterialThemeFromXml {
            EmojiReactions(
                reactions = { state },
                onEmojiClicked = { emoji -> state.updateWithEmoji(emoji) },
                shape = chipCornerRadius?.let { RoundedCornerShape(it) } ?: InputChipDefaults.shape
            )
        }
    }
}
