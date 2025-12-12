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
package com.infomaniak.mail.ui.components.views

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.infomaniak.core.extensions.goToAppStore
import com.infomaniak.core.inappupdate.AppUpdateSettingsRepository
import com.infomaniak.core.inappupdate.updatemanagers.InAppUpdateManager
import com.infomaniak.core.inappupdate.updaterequired.ui.composable.UpdateAvailableBottomSheetContent
import com.infomaniak.core.ui.compose.basics.Typography
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackInAppUpdateEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.components.compose.ButtonType
import com.infomaniak.mail.ui.components.compose.LargeButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.infomaniak.core.R as RCore
import com.infomaniak.core.inappupdate.R as RCoreInAppUpdate

@AndroidEntryPoint
class UpdateAvailableBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MailBottomSheetScaffoldComposeView(context, attrs, defStyleAttr) {
    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    @Composable
    override fun BottomSheetContent() {
        val context = LocalContext.current

        UpdateAvailableBottomSheetContent(
            illustration = painterResource(R.drawable.ic_update_logo),
            titleTextStyle = Typography.h2,
            descriptionTextStyle = Typography.bodyMedium,
            installUpdateButton = {
                LargeButton(
                    title = stringResource(RCoreInAppUpdate.string.buttonUpdate),
                    modifier = it,
                    onClick = {
                        inAppUpdateManager.set(AppUpdateSettingsRepository.IS_USER_WANTING_UPDATES_KEY, true)
                        context.goToAppStore()
                        hideBottomSheet()
                    }
                )
            },
            dismissButton = {
                LargeButton(
                    title = stringResource(RCore.string.buttonLater),
                    modifier = it,
                    style = ButtonType.Tertiary,
                    onClick = {
                        trackInAppUpdateEvent(MatomoName.DiscoverLater)
                        inAppUpdateManager.set(AppUpdateSettingsRepository.IS_USER_WANTING_UPDATES_KEY, false)
                        hideBottomSheet()
                    }
                )
            }
        )
    }
}
