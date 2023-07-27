/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.views.decoratedTextItemView

import androidx.core.view.marginStart
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewDecoratedTextItemBinding
import com.infomaniak.lib.core.R as RCore

interface IndentableFolder {

    val binding: ViewDecoratedTextItemBinding

    fun setIndent(indent: Int, hasCollapsableFolder: Boolean = false, folderCanCollapse: Boolean = false) {
        val totalStartMargin = computeStartMargin(hasCollapsableFolder, folderCanCollapse) + computeIndent(indent)
        binding.itemName.setMarginsRelative(start = totalStartMargin)
    }

    private fun computeIndent(indent: Int): Int {
        return binding.context.resources.getDimension(RCore.dimen.marginStandard).toInt() * indent
    }

    private fun computeStartMargin(hasCollapsableFolder: Boolean, folderCanCollapse: Boolean): Int = with(binding) {
        return if (hasCollapsableFolder && !folderCanCollapse) {
            context.resources.getDimension(R.dimen.folderUncollapsableIndent).toInt()
        } else {
            itemName.marginStart
        }
    }
}
