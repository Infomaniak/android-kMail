/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.isInvisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMainActionsBinding

@SuppressLint("ClickableViewAccessibility")
class MainActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ViewMainActionsBinding
    lateinit var buttons: List<MaterialButton>
    lateinit var textViews: List<TextView>
    private val menu: MenuBuilder by lazy { MenuBuilder(context) }

    init {
        binding = ViewMainActionsBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            if (attrs != null) {
                buttons = listOf(button1, button2, button3, button4)
                textViews = listOf(textView1, textView2, textView3, textView4)

                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MainActionsView, 0, 0)

                val menuRes = typedArray.getResourceId(R.styleable.BottomQuickActionBarView_menu, -1)
                if (menuRes == -1) return@with

                MenuInflater(context).inflate(menuRes, menu)

                val items = mutableListOf<Pair<Drawable, CharSequence>>().apply {
                    menu.forEach { item -> add(item.icon to item.title) }
                }.take(buttons.count())

                buttons.forEachIndexed { index, button ->
                    val textView = textViews[index]
                    if (index >= items.count()) {
                        button.isInvisible = true
                        textView.isInvisible = true
                    } else {
                        val (icon, title) = items[index]
                        button.icon = icon
                        textView.apply {
                            text = title
                            setOnTouchListener { _, event ->
                                (button.background as RippleDrawable).setHotspot(event.x, button.height.toFloat())
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> button.isPressed = true
                                    MotionEvent.ACTION_UP -> {
                                        if (button.isPressed) {
                                            button.isPressed = false
                                            button.callOnClick()
                                        }
                                    }
                                    MotionEvent.ACTION_MOVE -> button.isPressed = false
                                }
                                true
                            }
                            setOnClickListener {
                                button.isPressed = true
                                button.isPressed = false
                            }
                        }
                    }
                }

                typedArray.recycle()
            }
        }
    }

    fun setOnItemClickListener(callback: (Int) -> Unit) {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { callback(menu[index].itemId) }
        }
    }
}
