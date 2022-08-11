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
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var binding: ViewBottomQuickActionBarBinding
    lateinit var buttons: List<MaterialButton>

    init {
        binding = ViewBottomQuickActionBarBinding.inflate(LayoutInflater.from(context), this, true)

        with(binding) {
            if (attrs != null) {
                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BottomQuickActionBarView, 0, 0)
                val icon1 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_icon1)
                val icon2 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_icon2)
                val icon3 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_icon3)
                val icon4 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_icon4)
                val icon5 = typedArray.getDrawable(R.styleable.BottomQuickActionBarView_icon5)
                val title1 = typedArray.getString(R.styleable.BottomQuickActionBarView_title1)
                val title2 = typedArray.getString(R.styleable.BottomQuickActionBarView_title2)
                val title3 = typedArray.getString(R.styleable.BottomQuickActionBarView_title3)
                val title4 = typedArray.getString(R.styleable.BottomQuickActionBarView_title4)
                val title5 = typedArray.getString(R.styleable.BottomQuickActionBarView_title5)

                buttons = listOf(button1, button2, button3, button4, button5)
                val icons = listOf(icon1, icon2, icon3, icon4, icon5)
                val titles = listOf(title1, title2, title3, title4, title5)

                icons.forEachIndexed { index, drawable ->
                    if (drawable == null) buttons[index].isGone = true
                    else buttons[index].icon = drawable
                }

                titles.forEachIndexed { index, text ->
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
