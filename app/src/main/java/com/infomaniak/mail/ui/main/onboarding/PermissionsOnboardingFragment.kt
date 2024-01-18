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
package com.infomaniak.mail.ui.main.onboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.LocalSettings.*
import com.infomaniak.mail.databinding.FragmentPermissionsOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PermissionsOnboardingFragment : Fragment() {

    private var binding: FragmentPermissionsOnboardingBinding by safeBinding()
    private val navigationArgs: PermissionsOnboardingFragmentArgs by navArgs()

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPermissionsOnboardingBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        setPermissionUi()
    }

    private fun setPermissionUi() = with(binding) {
        val permission = if (navigationArgs.position == 0) PermissionType.CONTACTS else PermissionType.NOTIFICATIONS
        iconLayout.setImageResource(getIconResWithAccentColor(permission))
        title.setText(permission.titleRes)
        description.setText(permission.descritionRes)
        waveBackground.apply {
            setImageResource(permission.waveRes)
            imageTintList = ColorStateList.valueOf(localSettings.accentColor.getOnboardingSecondaryBackground(context))
        }
    }


    private fun getIconResWithAccentColor(permission: PermissionType) = when (localSettings.accentColor) {
        AccentColor.PINK -> permission.pinkIconRes
        AccentColor.BLUE -> permission.blueIconRes
        AccentColor.SYSTEM -> permission.systemIconRes
    }

    enum class PermissionType(
        @DrawableRes val pinkIconRes: Int,
        @DrawableRes val blueIconRes: Int,
        @DrawableRes val systemIconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descritionRes: Int,
        @DrawableRes val waveRes: Int,
    ) {
        CONTACTS(
            pinkIconRes = R.drawable.illustration_onboarding_contacts,
            blueIconRes = R.drawable.illustration_onboarding_contacts_blue,
            systemIconRes = R.drawable.illustration_onboarding_contacts_material,
            titleRes = R.string.onBoardingContactsTitle,
            descritionRes = R.string.onBoardingContactsDescription,
            waveRes = R.drawable.ic_back_wave_1,
        ),
        NOTIFICATIONS(
            pinkIconRes = R.drawable.illustration_onboarding_notifications,
            blueIconRes = R.drawable.illustration_onboarding_notifications_blue,
            systemIconRes = R.drawable.illustration_onboarding_notifications_material,
            titleRes = R.string.onBoardingNotificationsTitle,
            descritionRes = R.string.onBoardingNotificationsDescription,
            waveRes = R.drawable.ic_back_wave_2,
        ),
    }
}
