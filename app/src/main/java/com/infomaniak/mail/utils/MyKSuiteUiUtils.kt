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

import android.content.Context
import androidx.fragment.app.Fragment
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.screens.MyKSuiteDashboardScreenData
import com.infomaniak.core.myksuite.ui.screens.components.KSuiteProductsWithQuotas
import com.infomaniak.core.myksuite.ui.views.MyKSuiteDashboardFragmentArgs
import com.infomaniak.lib.core.utils.FormatterFileSize.formatShortFileSize
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.extensions.animatedNavigation

object MyKSuiteUiUtils {

    fun Fragment.openMyKSuiteDashboard(myKSuiteData: MyKSuiteData) {
        val args = MyKSuiteDashboardFragmentArgs(dashboardData = getDashboardData(requireContext(), myKSuiteData))
        animatedNavigation(resId = R.id.myKSuiteDashboardFragment, args = args.toBundle())
    }

    fun getDashboardData(context: Context, myKSuiteData: MyKSuiteData): MyKSuiteDashboardScreenData {
        return MyKSuiteDashboardScreenData(
            myKSuiteTier = myKSuiteData.tier,
            email = myKSuiteData.mail.email,
            avatarUri = AccountUtils.currentUser?.avatar ?: "",
            dailySendingLimit = myKSuiteData.mail.dailyLimitSent.toString(),
            kSuiteProductsWithQuotas = context.getKSuiteQuotasApp(myKSuiteData).toList(),
            trialExpiryDate = myKSuiteData.trialExpiryDate,
        )
    }

    private fun Context.getKSuiteQuotasApp(myKSuite: MyKSuiteData): Array<KSuiteProductsWithQuotas> {

        val mailProduct = with(myKSuite.mail) {
            KSuiteProductsWithQuotas.Mail(
                usedSize = formatShortFileSize(usedSizeInBytes),
                maxSize = formatShortFileSize(storageSizeLimit),
                progress = (usedSizeInBytes.toDouble() / storageSizeLimit.toDouble()).toFloat(),
            )
        }

        val driveProduct = with(myKSuite.drive) {
            KSuiteProductsWithQuotas.Drive(
                usedSize = formatShortFileSize(usedSize),
                maxSize = formatShortFileSize(size),
                progress = (usedSize.toDouble() / size.toDouble()).toFloat(),
            )
        }

        return arrayOf(mailProduct, driveProduct)
    }
}
