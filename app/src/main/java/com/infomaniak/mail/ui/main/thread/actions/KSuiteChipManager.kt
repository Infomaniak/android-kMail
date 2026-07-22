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
package com.infomaniak.mail.ui.main.thread.actions

import android.view.ViewGroup
import com.infomaniak.core.ksuite.ksuitepro.views.EvolveChipView
import com.infomaniak.core.ksuite.myksuite.ui.views.MyKSuitePlusChipView

class KSuiteChipManager(private val container: ViewGroup) {

    private val context get() = container.context

    private val kSuitePersoChipView by lazy { MyKSuitePlusChipView(context) }
    private val kSuiteProChipView by lazy { EvolveChipView(context) }

    fun displayChipFor(trailingContent: TrailingContent): Boolean {
        container.removeView(kSuitePersoChipView)
        container.removeView(kSuiteProChipView)

        return when (trailingContent) {
            // ComposeView are not compatible with view without lifecycles (ex: PopupWindow in RecipientFieldView).
            // This is causing a crash so to avoid that, we have to programmatically
            // add the Compose view only where it's needed.
            TrailingContent.KSuitePersoChip -> container.addView(kSuitePersoChipView).let { true }
            TrailingContent.KSuiteProChip -> container.addView(kSuiteProChipView).let { true }
            else -> false
        }
    }
}

/** Keep the entries order, it's used by the attribute (or change also the attributes order in attrs.xml) */
enum class TrailingContent {
    None,
    Chevron,
    Description,
    KSuitePersoChip,
    KSuiteProChip,
}
