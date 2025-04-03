/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.di

import androidx.fragment.app.FragmentActivity
import com.infomaniak.lib.stores.reviewmanagers.InAppReviewManager
import com.infomaniak.lib.stores.updatemanagers.InAppUpdateManager
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @ActivityScoped
    @Provides
    fun provideInAppUpdateManager(activity: FragmentActivity) = InAppUpdateManager(
        activity = activity,
        appId = BuildConfig.APPLICATION_ID,
        versionCode = BuildConfig.VERSION_CODE,
    )

    @ActivityScoped
    @Provides
    fun provideInAppReviewManager(activity: FragmentActivity) = InAppReviewManager(
        activity = activity,
        reviewDialogTheme = R.style.DialogStyle,
        reviewDialogTitleResId = R.string.reviewAlertTitle,
        feedbackUrlResId = R.string.urlUserReportAndroid,
    )
}
