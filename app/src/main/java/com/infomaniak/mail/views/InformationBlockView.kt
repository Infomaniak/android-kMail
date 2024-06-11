/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewInformationBlockBinding

class InformationBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewInformationBlockBinding.inflate(LayoutInflater.from(context), this, true) }

    private var onActionClicked: (() -> Unit)? = null
    private var onCloseClicked: (() -> Unit)? = null

    var title: CharSequence?
        get() = binding.informationTitle.text
        set(value) {
            binding.informationTitle.apply {
                text = value
                isGone = value.isNullOrBlank()
            }
        }

    var description: CharSequence?
        get() = binding.informationDescription.text
        set(value) {
            binding.informationDescription.apply {
                text = value
                isGone = value.isNullOrBlank()
            }
        }

    var buttonLabel: CharSequence?
        get() = binding.informationButton.text
        set(value) {
            binding.informationButton.apply {
                text = value
                isGone = value.isNullOrBlank()
            }
        }

    var icon: Drawable?
        get() = binding.icon.compoundDrawablesRelative[0]
        set(value) {
            binding.icon.setCompoundDrawablesRelativeWithIntrinsicBounds(value, null, null, null)
        }

    init {
        attrs?.getAttributes(context, R.styleable.InformationBlockView) {
            title = getString(R.styleable.InformationBlockView_title)
            description = getString(R.styleable.InformationBlockView_description)
            buttonLabel = getString(R.styleable.InformationBlockView_buttonLabel)
            binding.informationButton.setOnClickListener { onActionClicked?.invoke() }
            icon = getDrawable(R.styleable.InformationBlockView_icon)
            binding.closeButton.apply {
                isVisible = getBoolean(R.styleable.InformationBlockView_showCloseIcon, false)
                setOnClickListener { onCloseClicked?.invoke() }
            }
        }
    }

    fun setOnActionClicked(listener: () -> Unit) {
        onActionClicked = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseClicked = listener
    }
}
