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

package com.infomaniak.mail.ui.main.thread

import android.app.Application
import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrintMailViewModel(application: Application) : AndroidViewModel(application) {

    fun startPrintingService(activityContext: Context, subject: String?, webView: WebView) {
        viewModelScope.launch(Dispatchers.Main) {
            subject?.let { subject ->
                val webViewPrintAdapter = webView.createPrintDocumentAdapter(subject)
                val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(subject, webViewPrintAdapter, null)
            }
        }
    }
}
