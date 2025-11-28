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
package com.infomaniak.mail.views.itemViews

import androidx.core.view.marginEnd
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding
import com.infomaniak.core.legacy.R as RCore

interface IndentableFolder {

    val binding: ViewDecoratedTextItemBinding

    fun setIndent(indent: Int, hasCollapsibleFolder: Boolean = false, canBeCollapsed: Boolean = false) {
        updateConstraintLayoutMarginStart()
        updateItemNameMarginStart(indent, canBeCollapsed)
    }

    private fun updateConstraintLayoutMarginStart() = with(binding) {
        val marginStart = context.resources.getDimension(R.dimen.decoratedItemConstraintMarginStart).toInt()
        constraintLayout.setMarginsRelative(start = marginStart)
    }

    private fun updateItemNameMarginStart(indent: Int, canBeCollapsed: Boolean) {
        val totalMarginStart = binding.context.resources.getDimension(R.dimen.decoratedItemTextMarginStart).toInt() + computeIndent(indent)
        binding.itemName.apply { setMarginsRelative(start = totalMarginStart, end = marginEnd) }
    }

    private fun computeIndent(indent: Int) = binding.context.resources.getDimension(RCore.dimen.marginStandardMedium).toInt() * indent
}
