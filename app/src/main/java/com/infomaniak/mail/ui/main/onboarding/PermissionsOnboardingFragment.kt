/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.FragmentPermissionsOnboardingBinding
import com.infomaniak.mail.utils.extensions.applyWindowInsetsListener
import com.infomaniak.mail.utils.extensions.statusBar
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

        binding.applyWindowInsetsListener { _, insets ->
            binding.dummyToolbarEdgeToEdge.apply {
                context?.getColor(R.color.onboarding_secondary_background)?.let(::setBackgroundColor)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    insets.statusBar().top,
                )
            }
        }

        setPermissionUi()
    }

    private fun setPermissionUi() = with(binding) {
        val permission = if (navigationArgs.position == 0) PermissionType.CONTACTS else PermissionType.NOTIFICATIONS
        animationLayout.apply {
            animationRes = permission.animationRes
            theme = localSettings.accentColor.getDotLottieTheme(requireContext())
        }
        title.setText(permission.titleRes)
        description.setText(permission.descritionRes)
        waveBackground.apply {
            setImageResource(permission.waveRes)
            imageTintList = ColorStateList.valueOf(localSettings.accentColor.getOnboardingSecondaryBackground(context))
        }
    }

    enum class PermissionType(
        @DrawableRes val animationRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descritionRes: Int,
        @DrawableRes val waveRes: Int,
    ) {
        CONTACTS(
            animationRes = R.raw.book_persons,
            titleRes = R.string.onBoardingContactsTitle,
            descritionRes = R.string.onBoardingContactsDescription,
            waveRes = R.drawable.ic_back_wave_1,
        ),
        NOTIFICATIONS(
            animationRes = R.raw.phone_notification,
            titleRes = R.string.onBoardingNotificationsTitle,
            descritionRes = R.string.onBoardingNotificationsDescription,
            waveRes = R.drawable.ic_back_wave_2,
        ),
    }
}
