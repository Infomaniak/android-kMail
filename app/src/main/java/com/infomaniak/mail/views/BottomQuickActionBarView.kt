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
package com.infomaniak.mail.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import com.google.android.material.button.MaterialButton
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBottomQuickActionBarBinding

class BottomQuickActionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ViewBottomQuickActionBarBinding
    lateinit var buttons: List<MaterialButton>

    init {
        binding = ViewBottomQuickActionBarBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            if (attrs != null) {
                val defaultIconColor = button1.iconTint.defaultColor
                val defaultTextColor = button1.textColors.defaultColor

                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BottomQuickActionBarView, 0, 0)
                val iconColor = typedArray.getColor(R.styleable.BottomQuickActionBarView_iconColor, defaultIconColor)
                val textColor = typedArray.getColor(R.styleable.BottomQuickActionBarView_textColor, defaultTextColor)
                val src1 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_src1)
                val src2 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_src2)
                val src3 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_src3)
                val src4 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_src4)
                val src5 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_src5)
                val text1 = typedArray.getString(R.styleable.BottomQuickActionBarView_text1)
                val text2 = typedArray.getString(R.styleable.BottomQuickActionBarView_text2)
                val text3 = typedArray.getString(R.styleable.BottomQuickActionBarView_text3)
                val text4 = typedArray.getString(R.styleable.BottomQuickActionBarView_text4)
                val text5 = typedArray.getString(R.styleable.BottomQuickActionBarView_text5)

                buttons = listOf(button1, button2, button3, button4, button5)
                val srcs = listOf(src1, src2, src3, src4, src5)
                val texts = listOf(text1, text2, text3, text4, text5)

                for (button in buttons) {
                    button.setTextColor(textColor)
                    button.iconTint = ColorStateList.valueOf(iconColor)
                }

                srcs.forEachIndexed { index, drawable ->
                    if (drawable == null) buttons[index].isGone = true
                    else buttons[index].icon = drawable
                }

                texts.forEachIndexed { index, text ->
                    if (text == null) buttons[index].isGone = true
                    else buttons[index].text = text
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

    fun changeIcon(index: Int, @DrawableRes icon: Int) {
        buttons[index].setIconResource(icon)
    }

    fun changeText(index: Int, @StringRes text: Int) {
        buttons[index].setText(text)
    }
}
