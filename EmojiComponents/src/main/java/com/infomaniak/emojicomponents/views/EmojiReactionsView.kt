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
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.vectorResource
import com.infomaniak.core.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.emojicomponents.R
import com.infomaniak.emojicomponents.components.AddReactionChipDefaults
import com.infomaniak.emojicomponents.components.EmojiReactions
import com.infomaniak.emojicomponents.components.EmojiReactionsDefaults
import com.infomaniak.emojicomponents.data.ReactionState

class EmojiReactionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val reactionsState = mutableStateMapOf<String, ReactionState>()
    private var isAddReactionEnabled by mutableStateOf(true)

    private var addReactionClickListener: (() -> Unit)? = null
    private var onEmojiClickListener: ((emoji: String) -> Unit)? = null

    private var chipCornerRadius: Float? = null
    private var addReactionIconRes: Int? = null
    @ColorInt
    private var addReactionDisabledColor: Int? = null

    init {
        context.obtainStyledAttributes(attrs, R.styleable.EmojiReactionsView, defStyleAttr, 0).apply {
            chipCornerRadius = getDimensionOrNull(R.styleable.EmojiReactionsView_chipCornerRadius)
            addReactionIconRes = getResourceIdOrNull(R.styleable.EmojiReactionsView_addReactionIcon)
            addReactionDisabledColor = getColorOrNull(R.styleable.EmojiReactionsView_addReactionDisabledColor)
            recycle()
        }
    }

    private fun TypedArray.getDimensionOrNull(@StyleableRes index: Int): Float? {
        return if (hasValue(index)) getDimension(index, -1f) else null
    }

    private fun TypedArray.getResourceIdOrNull(@StyleableRes index: Int): Int? {
        return if (hasValue(index)) getResourceId(index, -1) else null
    }

    @ColorInt
    private fun TypedArray.getColorOrNull(@StyleableRes index: Int): Int? {
        return if (hasValue(index)) getColor(index, -1) else null
    }

    @Composable
    override fun Content() {
        MaterialThemeFromXml {
            val addReactionIcon = addReactionIconRes?.let { ImageVector.vectorResource(it) }
                ?: EmojiReactionsDefaults.addReactionIcon

            val emojiReactionsColors = addReactionDisabledColor?.let { createEmojiReactionsColors(it) }
                ?: EmojiReactionsDefaults.colors()

            EmojiReactions(
                reactions = { reactionsState },
                onEmojiClicked = { emoji -> onEmojiClickListener?.invoke(emoji) },
                shape = chipCornerRadius?.let { RoundedCornerShape(it) } ?: InputChipDefaults.shape,
                addReactionIcon = addReactionIcon,
                isAddReactionEnabled = { isAddReactionEnabled },
                colors = emojiReactionsColors,
                onAddReactionClick = { addReactionClickListener?.invoke() },
            )
        }
    }

    /**
     * Calling this method is ambiguous. You should choose between detecting clicks on existing emojis or on the "add" button
     */
    @AmbiguousClickListener
    override fun setOnClickListener(listener: OnClickListener?) = super.setOnClickListener(listener)

    fun setOnAddReactionClickListener(listener: () -> Unit) {
        addReactionClickListener = listener
    }

    fun setOnEmojiClickListener(listener: (emoji: String) -> Unit) {
        onEmojiClickListener = listener
    }

    fun setEmojiReactions(emojiReactions: Map<String, ReactionState>) {
        reactionsState.clear()
        reactionsState.putAll(emojiReactions)
    }

    fun setAddReactionEnabledState(isEnabled: Boolean) {
        isAddReactionEnabled = isEnabled
    }

    @Composable
    private fun createEmojiReactionsColors(disabledContentColor: Int) = EmojiReactionsDefaults.colors(
        addReactionColors = AddReactionChipDefaults.addReactionColors(
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = Color(disabledContentColor),
            )
        )
    )
}

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Use the other methods to set click listeners when a reaction is clicked and when the add reaction button is clicked"
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
private annotation class AmbiguousClickListener
