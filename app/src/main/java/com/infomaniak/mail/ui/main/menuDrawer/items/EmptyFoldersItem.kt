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
package com.infomaniak.mail.ui.main.menuDrawer.items

import android.view.LayoutInflater
import android.view.ViewGroup
import com.infomaniak.mail.databinding.ItemMenuDrawerEmptyCustomFoldersBinding
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter

class EmptyFoldersItem(
    inflater: LayoutInflater,
    parent: ViewGroup
) : MenuDrawerAdapter.MenuDrawerViewHolder(ItemMenuDrawerEmptyCustomFoldersBinding.inflate(inflater, parent, false))
