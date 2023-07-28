/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.ui.main.menu

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMenuDrawerDropdownBinding
import com.infomaniak.mail.views.CollapsableItem

class MenuDrawerDropdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), CollapsableItem {

    override val binding by lazy { ViewMenuDrawerDropdownBinding.inflate(LayoutInflater.from(context), this, true) }

    override var canCollapse = false
    override var isCollapsed = false
        set(shouldCollapse) {
            binding.collapseButton.rotateChevron()
            field = shouldCollapse
        }

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.MenuDrawerDropdownView) {
                title.text = getString(R.styleable.MenuDrawerDropdownView_title)
                actionButton.isVisible = getBoolean(R.styleable.MenuDrawerDropdownView_showIcon, false)
                isCollapsed = getBoolean(R.styleable.MenuDrawerDropdownView_collapsedByDefault, false)

                actionButton.contentDescription = getString(R.styleable.MenuDrawerDropdownView_actionContentDescription)
            }

            collapseButton.rotateChevron()
            setOnClickListener(null)
        }
    }

    override fun setOnClickListener(listener: OnClickListener?) = with(binding) {
        root.setOnCollapsableItemClickListener(listener, collapseButton)
    }

    fun setOnActionClickListener(listener: OnClickListener) {
        binding.actionButton.setOnClickListener(listener)
    }
}
