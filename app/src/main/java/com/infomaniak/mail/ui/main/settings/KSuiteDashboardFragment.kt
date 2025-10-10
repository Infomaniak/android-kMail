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
package com.infomaniak.mail.ui.main.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.infomaniak.core.ksuite.myksuite.ui.views.MyKSuiteDashboardFragment
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.getDashboardData
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KSuiteDashboardFragment : MyKSuiteDashboardFragment() {

    private val myKSuiteViewModel: MykSuiteViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myKSuiteViewModel.refreshMyKSuite()
        myKSuiteViewModel.myKSuiteDataResult.observeNotNull(viewLifecycleOwner) { myKSuiteData ->
            AccountUtils.currentUser?.let { resetContent(dashboardData = getDashboardData(myKSuiteData, user = it)) }
        }
    }
}
