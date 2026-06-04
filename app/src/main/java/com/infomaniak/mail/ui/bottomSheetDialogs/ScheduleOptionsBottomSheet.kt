/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.databinding.BottomSheetScheduleOptionsBinding


abstract class ScheduleOptionsBottomSheet : SelectScheduleOptionBottomSheet() {

    private var binding: BottomSheetScheduleOptionsBinding by safeBinding()

    @get:StringRes
    abstract val titleRes: Int

    override val lastScheduleOption get() = binding.lastScheduleOption
    override val scheduleOptionsContainer get() = binding.scheduleOptions
    override val customScheduleOption get() = binding.customScheduleOption

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = getString(titleRes)
        setupScheduleOptions()
    }
}
