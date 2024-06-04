/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.views.itemViews

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBannerWithActionBinding

class BannerWithActionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewBannerWithActionBinding.inflate(LayoutInflater.from(context), this, true) }

    var description: CharSequence?
        get() = binding.description.text
        set(value) {
            binding.description.text = value
        }

    var actionButtonText: CharSequence? by binding.actionButton::text

    private val baseConstraints by lazy {
        ConstraintSet().apply {
            clone(binding.root)
        }
    }
    private val buttonAlignConstraints by lazy {
        ConstraintSet().apply {
            clone(baseConstraints)
            connect(R.id.description, ConstraintSet.END, R.id.actionButton, ConstraintSet.START)
            connect(R.id.actionButton, ConstraintSet.START, R.id.description, ConstraintSet.END)
            connect(R.id.actionButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(R.id.actionButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(R.id.actionButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }
    }

    init {
        attrs?.getAttributes(context, R.styleable.BannerWithActionView) { initView(attributes = this) }
    }

    private fun initView(attributes: TypedArray) {
        with(binding) {
            if (attributes.getBoolean(R.styleable.BannerWithActionView_isButtonAlignedWithDescription, false)) {
                description.setMarginsRelative(end = resources.getDimensionPixelSize(R.dimen.alternativeMargin))
                buttonAlignConstraints.applyTo(root)
            }

            attributes.getDrawable(R.styleable.BannerWithActionView_descriptionIcon)?.let {
                descriptionIcon.isVisible = true
                descriptionIcon.setCompoundDrawablesWithIntrinsicBounds(it, null, null, null)
            }

            actionButton.icon = attributes.getDrawable(R.styleable.BannerWithActionView_buttonIcon)
        }

        description = attributes.getString(R.styleable.BannerWithActionView_description) ?: ""
        actionButtonText = attributes.getString(R.styleable.BannerWithActionView_buttonText) ?: ""
    }

    fun setOnActionClickListener(callback: () -> Unit) {
        binding.actionButton.setOnClickListener { callback() }
    }

    fun getGoneHandle() = binding.goneHandle
}
