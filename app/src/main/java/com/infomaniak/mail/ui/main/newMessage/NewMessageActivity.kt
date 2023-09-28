/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.newMessage

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.viewModels
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.updateNavigationBarColor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewMessageActivity : BaseActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(binding.root)

        if (!isAuth()) {
            finish()
            return
        }

        setupSnackBar()
        setupSystemBars()

        updateAiFeatureFlag()
    }

    private fun updateAiFeatureFlag() {
        newMessageViewModel.updateFeatureFlag()
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == AppSettings.DEFAULT_ID) {
            startActivity(Intent(this, LaunchActivity::class.java))
            return false
        }
        return true
    }

    private fun setupSnackBar() {
        newMessageViewModel.snackBarManager.setup(view = binding.root, activity = this)
    }

    private fun setupSystemBars() {
        val backgroundColor = getColor(R.color.newMessageBackgroundColor)
        window.apply {
            statusBarColor = backgroundColor
            updateNavigationBarColor(backgroundColor)
        }
    }
}
