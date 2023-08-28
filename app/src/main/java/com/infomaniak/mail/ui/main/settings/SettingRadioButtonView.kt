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
package com.infomaniak.mail.ui.main.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewSettingRadioButtonBinding
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

class SettingRadioButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), RadioCheckable {

    private val binding by lazy { ViewSettingRadioButtonBinding.inflate(LayoutInflater.from(context), this, true) }

    private var onClickListener: OnClickListener? = null

    override var associatedValue: String? = null

    init {
        attrs?.getAttributes(context, R.styleable.SettingRadioButtonView) {
            with(binding) {
                val iconDrawable = getDrawable(R.styleable.SettingRadioButtonView_icon)
                val textString = getString(R.styleable.SettingRadioButtonView_text)
                val checkMarkColor = getColor(
                    R.styleable.SettingRadioButtonView_checkMarkColor,
                    context.getAttributeColor(RMaterial.attr.colorPrimary),
                )
                associatedValue = getString(R.styleable.SettingRadioButtonView_value)

                setIcon(iconDrawable)
                text.text = textString
                checkMark.setColorFilter(checkMarkColor)

                root.setOnClickListener {
                    (parent as? OnCheckListener)?.onChecked(this@SettingRadioButtonView.id) ?: onClickListener?.onClick(root)
                }
            }
        }
    }

    override fun check() = with(binding) {
        root.isEnabled = false
        checkMark.isVisible = true
    }

    override fun uncheck() = with(binding) {
        root.isEnabled = true
        checkMark.isGone = true
    }

    fun setText(newText: String) {
        binding.text.text = newText
    }

    fun setCheckMarkColor(@ColorInt color: Int) {
        binding.checkMark.setColorFilter(color)
    }

    fun setIcon(iconDrawable: Drawable?) {
        binding.icon.apply {
            isVisible = iconDrawable != null
            setImageDrawable(iconDrawable)
        }
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        onClickListener = listener
    }
}
