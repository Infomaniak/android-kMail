/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMessageAlertBinding

class MessageAlertView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewMessageAlertBinding.inflate(LayoutInflater.from(context), this, true) }

    init {
        attrs?.getAttributes(context, R.styleable.MessageAlertView) {
            with(binding) {
                description.text = getString(R.styleable.MessageAlertView_description)
                icon.setImageDrawable(getDrawable(R.styleable.MessageAlertView_icon))
                action1.text = getString(R.styleable.MessageAlertView_action1)

                val hasAction2 = hasValue(R.styleable.MessageAlertView_action2)
                action2.apply {
                    text = getString(R.styleable.MessageAlertView_action2)
                    isVisible = hasAction2
                }
                divider.isVisible = hasAction2
            }
        }
    }

    fun onAction1(listener: OnClickListener) {
        binding.action1.setOnClickListener(listener)
    }

    fun onAction2(listener: OnClickListener) {
        binding.action2.setOnClickListener(listener)
    }
}
