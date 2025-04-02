/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.screens.KSuiteApp
import com.infomaniak.core.myksuite.ui.screens.MyKSuiteDashboardScreenData
import com.infomaniak.core.myksuite.ui.utils.MyKSuiteUiUtils
import com.infomaniak.core.myksuite.ui.utils.MyKSuiteUiUtils.openMyKSuiteUpgradeBottomSheet
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorResBasedOnId
import com.infomaniak.lib.core.utils.canNavigate
import com.infomaniak.mail.MatomoMail.trackMyKSuiteUpgradeBottomSheetEvent
import com.infomaniak.mail.R

fun Fragment.openMyKSuiteUpgradeBottomSheet(matomoTrackerName: String) {
    if (canNavigate()) requireActivity().openMyKSuiteUpgradeBottomSheet(findNavController(), matomoTrackerName)
}

fun Activity.openMyKSuiteUpgradeBottomSheet(navController: NavController, matomoTrackerName: String) {
    trackMyKSuiteUpgradeBottomSheetEvent(matomoTrackerName)
    navController.openMyKSuiteUpgradeBottomSheet(KSuiteApp.Mail)
}

fun Fragment.getDashboardData(myKSuiteData: MyKSuiteData): MyKSuiteDashboardScreenData {
    val backgroundColor = requireContext().getBackgroundColorResBasedOnId(
        id = AccountUtils.currentUser?.id ?: -1,
        array = R.array.AvatarColors,
    )

    return MyKSuiteUiUtils.getDashboardData(
        context = requireContext(),
        myKSuiteData = myKSuiteData,
        avatarUri = AccountUtils.currentUser?.avatar,
        userInitials = AccountUtils.currentUser?.getInitials(),
        iconColor = requireContext().getColor(R.color.onColorfulBackground),
        userInitialsBackgroundColor = backgroundColor,
    )
}
