/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.UiSettings
import com.infomaniak.mail.data.models.UiSettings.ColorTheme

open class ThemedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = when (UiSettings(this).colorTheme) {
            ColorTheme.BLUE -> R.style.AppTheme_Blue
            else -> R.style.AppTheme_Pink
        }
        setTheme(theme)

        super.onCreate(savedInstanceState)
    }
}
