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
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.databinding.BottomSheetScheduleOptionsBinding
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.utils.date.DateFormatUtils.dayOfWeekDateWithoutYear

abstract class SimpleSchedulePickerBottomSheet : EdgeToEdgeBottomSheetDialog() {

    private var binding: BottomSheetScheduleOptionsBinding by safeBinding()

    @get:StringRes
    abstract val titleRes: Int

    abstract val lastSelectedEpoch: Long?
    abstract val currentlyScheduledEpochMillis: Long?
    abstract val currentKSuite: KSuite?

    abstract fun onLastScheduleOptionClicked()
    abstract fun onScheduleOptionClicked(dateItem: ScheduleOption)
    abstract fun onCustomScheduleOptionClicked()

    protected open fun createScheduleOptionItem(scheduleOption: ScheduleOption): View {
        return ActionItemView(requireContext()).apply {
            setTitle(scheduleOption.titleRes)
            setDescription(context.dayOfWeekDateWithoutYear(date = scheduleOption.date()))
            setIconResource(scheduleOption.iconRes)
            setOnClickListener { onScheduleOptionClicked(scheduleOption) }
        }
    }

    protected open fun bindLastScheduleOptionDescription(description: String) {
        binding.lastScheduleOption.setDescription(description)
    }

    protected open fun setupFirstScheduleOptionDivider(firstItem: View, shouldDisplayDivider: Boolean) {
        (firstItem as? ActionItemView)?.setDividerVisibility(shouldDisplayDivider)
    }

    protected fun setupScheduleOptions() {
        ScheduleOptionsHelper(
            context = requireContext(),
            lastScheduleOption = binding.lastScheduleOption,
            scheduleOptionsContainer = binding.scheduleOptions,
            customScheduleOption = binding.customScheduleOption,
            lastSelectedEpoch = lastSelectedEpoch,
            currentlyScheduledEpochMillis = currentlyScheduledEpochMillis,
            currentKSuite = currentKSuite,
            onLastScheduleOptionClicked = ::onLastScheduleOptionClicked,
            onCustomScheduleOptionClicked = ::onCustomScheduleOptionClicked,
            createScheduleOptionItem = ::createScheduleOptionItem,
            bindLastScheduleOptionDescription = ::bindLastScheduleOptionDescription,
            setupFirstScheduleOptionDivider = ::setupFirstScheduleOptionDivider,
        ).setup()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetScheduleOptionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = getString(titleRes)
        setupScheduleOptions()
    }
}
