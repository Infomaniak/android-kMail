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
package com.infomaniak.mail.ui.main

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewEmptyStateBinding

class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewEmptyStateBinding by lazy { ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this, true) }

    var illustration: Drawable?
        get() = binding.illustration.drawable
        set(value) {
            binding.illustration.setImageDrawable(value)
        }

    var title: CharSequence?
        get() = binding.title.text
        set(value) {
            binding.title.apply {
                isVisible = true
                text = value
            }
        }

    var description: CharSequence?
        get() = binding.description.text
        set(value) {
            binding.description.apply {
                isVisible = true
                text = value
            }
        }

    init {
        attrs?.getAttributes(context, R.styleable.EmptyStateView) {
            with(binding) {
                illustration.setImageDrawable(getDrawable(R.styleable.EmptyStateView_icon))

                getString(R.styleable.EmptyStateView_title).let {
                    title.apply {
                        text = it
                        isGone = it == null
                    }
                }

                getString(R.styleable.EmptyStateView_description).let {
                    description.apply {
                        text = it
                        isGone = it == null
                    }
                }
            }
        }
    }
}
