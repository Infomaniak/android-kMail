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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.views.MyKSuiteDashboardFragment
import com.infomaniak.mail.utils.MyKSuiteDataUtils
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.infomaniak.core.myksuite.R as RMyKSuite

class KSuiteDashboardFragment : MyKSuiteDashboardFragment() {

    private var kSuiteData: MyKSuiteData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            if (kSuiteData == null) MyKSuiteDataUtils.requestKSuiteData(MyKSuiteDataUtils.myKSuiteId)
            Log.e("TOTO", "onCreate: $kSuiteData")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setSystemBarsColors(statusBarColor = RMyKSuite.color.dashboardBackground)
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}
