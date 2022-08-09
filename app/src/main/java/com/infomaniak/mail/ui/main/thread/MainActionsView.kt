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
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isInvisible
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMainActionsBinding

@SuppressLint("ClickableViewAccessibility")
class MainActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ViewMainActionsBinding
    lateinit var buttons: List<MaterialButton>
    lateinit var textViews: List<TextView>

    init {
        binding = ViewMainActionsBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            if (attrs != null) {
                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MainActionsView, 0, 0)
                val icon1 = typedArray.getDrawable(R.styleable.MainActionsView_icon1)
                val icon2 = typedArray.getDrawable(R.styleable.MainActionsView_icon2)
                val icon3 = typedArray.getDrawable(R.styleable.MainActionsView_icon3)
                val icon4 = typedArray.getDrawable(R.styleable.MainActionsView_icon4)
                val title1 = typedArray.getString(R.styleable.MainActionsView_title1)
                val title2 = typedArray.getString(R.styleable.MainActionsView_title2)
                val title3 = typedArray.getString(R.styleable.MainActionsView_title3)
                val title4 = typedArray.getString(R.styleable.MainActionsView_title4)

                buttons = listOf(button1, button2, button3, button4)
                textViews = listOf(textView1, textView2, textView3, textView4)
                val icons = listOf(icon1, icon2, icon3, icon4)
                val titles = listOf(title1, title2, title3, title4)

                icons.forEachIndexed { index, drawable ->
                    if (drawable == null) {
                        buttons[index].isInvisible = true
                        textViews[index].isInvisible = true
                    } else {
                        buttons[index].icon = drawable
                    }
                }

                titles.forEachIndexed { index, text ->
                    val associatedButton = buttons[index]
                    val thisTextView = textViews[index]

                    if (text == null) {
                        associatedButton.isInvisible = true
                        thisTextView.isInvisible = true
                    } else {
                        thisTextView.text = text
                        thisTextView.setOnTouchListener { _, event ->
                            (associatedButton.background as RippleDrawable).setHotspot(event.x, associatedButton.height.toFloat())
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> associatedButton.isPressed = true
                                MotionEvent.ACTION_UP -> {
                                    if (associatedButton.isPressed) {
                                        associatedButton.isPressed = false
                                        associatedButton.callOnClick()
                                    }
                                }
                                MotionEvent.ACTION_MOVE -> associatedButton.isPressed = false
                            }
                            true
                        }
                        thisTextView.setOnClickListener {
                            associatedButton.isPressed = true
                            associatedButton.isPressed = false
                        }
                    }
                }

                typedArray.recycle()
            }
        }
    }

    fun setOnItemClickListener(callback: (Int) -> Unit) {
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { callback(index) }
        }
    }
}
