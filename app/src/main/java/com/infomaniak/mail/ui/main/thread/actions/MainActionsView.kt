/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.*
import android.widget.ActionMenuView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.view.*
import com.google.android.material.button.MaterialButton
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMainActionsBinding

class MainActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewMainActionsBinding.inflate(LayoutInflater.from(context), this, true) }

    private val buttons: List<MaterialButton> by lazy { with(binding) { listOf(button1, button2, button3, button4) } }
    private val textViews: List<TextView> by lazy { with(binding) { listOf(textView1, textView2, textView3, textView4) } }
    private val menu: Menu by lazy { ActionMenuView(context).menu }

    init {
        attrs?.getAttributes(context, R.styleable.MainActionsView) {
            MenuInflater(context).inflate(getResourceIdOrThrow(R.styleable.MainActionsView_menu), menu)

            buttons.forEachIndexed { index, button ->
                val textView = textViews[index]
                if (index >= menu.size) {
                    button.isInvisible = true
                    textView.isInvisible = true
                } else {
                    with(menu[index]) { setAction(index, icon, title.toString()) }
                    textView.transferClickTo(button)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.transferClickTo(targetView: View) {
        setOnTouchListener { _, event ->
            (targetView.background as RippleDrawable).setHotspot(event.x, targetView.height.toFloat())
            when (event.action) {
                MotionEvent.ACTION_DOWN -> targetView.isPressed = true
                MotionEvent.ACTION_UP -> {
                    if (targetView.isPressed) {
                        targetView.isPressed = false
                        targetView.callOnClick()
                    }
                }
                MotionEvent.ACTION_MOVE -> targetView.isPressed = false
            }
            true
        }
        setOnClickListener {
            targetView.isPressed = true
            targetView.isPressed = false
        }
    }

    fun setOnItemClickListener(callback: (Int) -> Unit) {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { callback(menu[index].itemId) }
        }
    }

    fun setAction(@IdRes menuId: Int, @DrawableRes iconRes: Int, @StringRes titleRes: Int) {
        val title = context.getString(titleRes)
        val drawable = AppCompatResources.getDrawable(context, iconRes)
        val index = getIndexOfMenuItem(menuId)
        if (index >= 0) setAction(index, drawable, title)
    }

    private fun getIndexOfMenuItem(menuId: Int): Int {
        menu.forEachIndexed { index, item ->
            if (item.itemId == menuId) return index
        }

        return -1
    }

    private fun setAction(index: Int, drawable: Drawable?, title: String) {
        buttons[index].apply {
            icon = drawable
            contentDescription = title
        }
        textViews[index].text = title
    }
}
