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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.materialswitch.MaterialSwitch
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewItemSettingBinding

class ItemSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewItemSettingBinding.inflate(LayoutInflater.from(context), this, true) }

    private var action: Action = Action.NONE

    var isChecked
        get() = binding.toggle.isChecked
        set(value) {
            binding.toggle.isChecked = value
        }

    val toggle: MaterialSwitch? get() = if (action == Action.TOGGLE) binding.toggle else null

    init {
        attrs?.getAttributes(context, R.styleable.ItemSettingView) {
            with(binding) {
                action = Action.entries[getInteger(R.styleable.ItemSettingView_itemAction, 0)]

                title.text = getString(R.styleable.ItemSettingView_title) ?: ""

                getDrawable(R.styleable.ItemSettingView_icon).let {
                    icon.setImageDrawable(it)
                    icon.isGone = it == null
                }

                getString(R.styleable.ItemSettingView_subtitle).let {
                    subtitle.apply {
                        text = it
                        isGone = it == null
                    }
                }

                chevron.isVisible = action == Action.CHEVRON
                toggle.isVisible = action == Action.TOGGLE
                openExternal.isVisible = action == Action.OPEN_EXTERNAL

                if (!getBoolean(R.styleable.ItemSettingView_ripple, true)) root.background = null
            }
        }
    }

    override fun setOnClickListener(listener: OnClickListener?) = with(binding) {
        root.setOnClickListener {
            toggle.toggle()
            listener?.onClick(root)
        }

        toggle.setOnClickListener(listener)
    }

    fun setTitle(title: String) {
        binding.title.text = title
    }

    fun setSubtitle(@StringRes subtitle: Int) {
        binding.subtitle.apply {
            setText(subtitle)
            isVisible = true
        }
    }

    fun setSubtitle(subtitle: String) {
        binding.subtitle.apply {
            text = subtitle
            isVisible = true
        }
    }

    fun toggleMailboxBlockedState(mustBlock: Boolean) = with(binding) {
        warning.isVisible = mustBlock
        chevron.isGone = mustBlock
    }

    private enum class Action {
        NONE,
        CHEVRON,
        TOGGLE,
        OPEN_EXTERNAL,
    }
}
