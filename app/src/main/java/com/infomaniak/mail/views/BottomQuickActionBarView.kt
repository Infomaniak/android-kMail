/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.widget.ActionMenuView
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBottomQuickActionBarBinding
import com.infomaniak.mail.utils.extensions.applySideAndBottomSystemInsets

class BottomQuickActionBarView @JvmOverloads constructor(
    context: Context,
    val attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewBottomQuickActionBarBinding.inflate(LayoutInflater.from(context), this, true) }

    private val buttons: List<MaterialButton> by lazy { with(binding) { listOf(button1, button2, button3, button4, button5) } }
    private val menu: Menu by lazy { ActionMenuView(context).menu }

    init {
        init()
        handleEdgeToEdge()
    }

    fun init(@MenuRes menuRes: Int? = null) {
        attrs?.getAttributes(context, R.styleable.BottomQuickActionBarView) {

            val menuResId = menuRes
                ?: runCatching { getResourceIdOrThrow(R.styleable.BottomQuickActionBarView_menu) }.getOrNull()
                ?: return@getAttributes
            MenuInflater(context).inflate(menuResId, menu)

            buttons.forEachIndexed { index, button ->
                if (index >= menu.size) {
                    button.isGone = true
                } else {
                    with(menu[index]) {
                        button.icon = icon
                        button.text = title
                    }
                }
            }
        }
    }

    fun setOnItemClickListener(callback: (menuId: Int) -> Unit) {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { callback(menu[index].itemId) }
        }
    }

    fun changeIcon(index: Int, @DrawableRes icon: Int) {
        buttons[index].setIconResource(icon)
    }

    fun changeText(index: Int, @StringRes text: Int) {
        buttons[index].setText(text)
    }

    fun enable(index: Int) {
        buttons[index].isEnabled = true
    }

    fun disable(index: Int) {
        buttons[index].isEnabled = false
    }

    fun getButtonCount() = buttons.count()

    private fun handleEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            root.applySideAndBottomSystemInsets(insets)
            WindowInsetsCompat.CONSUMED
        }
    }
}
