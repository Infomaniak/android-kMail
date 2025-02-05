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

import com.infomaniak.core.myksuite.ui.data.MyKSuiteData
import com.infomaniak.core.myksuite.ui.data.MyKSuiteDataManager
import com.infomaniak.mail.data.cache.appSettings.AppSettingsController
import com.infomaniak.mail.data.models.AppSettings

object MyKSuiteDataUtils : MyKSuiteDataManager() {

    override var currentMyKSuiteId: Int = AppSettingsController.getAppSettings().currentMyKSuiteId
        set(myKSuiteId) {
            field = myKSuiteId
            AppSettingsController.updateAppSettings { appSettings -> appSettings.currentMyKSuiteId = myKSuiteId }
        }

    override var currentMyKSuite: MyKSuiteData? = null
        set(myKSuiteData) {
            field = myKSuiteData
            currentMyKSuiteId = myKSuiteData?.id ?: AppSettings.DEFAULT_ID
        }
}
