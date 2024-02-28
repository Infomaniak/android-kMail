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

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrintMailViewModel : ViewModel() {

    fun startPrintingService(
        activityContext: Context,
        subject: String?,
        webView: WebView,
        onFinish: () -> Unit,
    ) = viewModelScope.launch(Dispatchers.Main) {
        subject?.let { subject ->
            val webViewPrintAdapter = PrintAdapterWrapper(webView.createPrintDocumentAdapter(subject), onFinish)
            val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(subject, webViewPrintAdapter, null)
        }
    }

    private class PrintAdapterWrapper(
        private val printAdapter: PrintDocumentAdapter,
        private val onFinish: () -> Unit,
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) {
            printAdapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) {
            printAdapter.onWrite(pages, destination, cancellationSignal, callback)
        }

        override fun onFinish() {
            printAdapter.onFinish()
            onFinish.invoke()
        }

        override fun onStart() {
            printAdapter.onStart()
        }
    }
}
