/*
 * Infomaniak kMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.login

import android.os.Bundle
import android.webkit.WebView
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.databinding.ActivityNoValidMailboxesBinding
import com.infomaniak.mail.ui.ThemedActivity

class NoValidMailboxesActivity : ThemedActivity() {

    private val binding by lazy { ActivityNoValidMailboxesBinding.inflate(layoutInflater) }
    // private val noValidMailboxesViewModel: NoValidMailboxesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(binding.root)
    }
}
