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

package com.infomaniak.mail.utils.extensions

import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.main.thread.actions.ActionItemView
import com.infomaniak.mail.ui.main.thread.actions.TrailingContent

fun KSuite?.toTrailingContent(default: TrailingContent = TrailingContent.Chevron): TrailingContent = when (this) {
    KSuite.Perso.Free -> TrailingContent.KSuitePersoChip
    KSuite.Pro.Free, KSuite.StarterPack -> TrailingContent.KSuiteProChip
    else -> default
}

fun ActionItemView.setKSuiteTrailingContent(kSuite: KSuite?, default: TrailingContent = TrailingContent.Chevron) {
    trailingContent = kSuite.toTrailingContent(default)
}

fun ItemSettingView.setKSuiteTrailingContent(kSuite: KSuite?, default: TrailingContent = TrailingContent.Chevron) {
    trailingContent = kSuite.toTrailingContent(default)
}
