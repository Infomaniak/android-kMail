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
import androidx.compose.ui.graphics.Color
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.ksuite.ksuitepro.views.components.ProOfferContent
import com.infomaniak.core.ksuite.ksuitepro.R as RCore

class KSuiteProBottomSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MailBottomSheetScaffoldComposeView(context, attrs, defStyleAttr) {

    override val dragHandleBackgroundColor: Color
        get() = Color(resources.getColor(RCore.color.kSuiteBackground, null))

    private var kSuite = KSuite.ProStandard
    private var isAdmin = false
    private var onClose: (() -> Unit)? = null

    @Composable
    override fun BottomSheetContent() {
        ProOfferContent(
            kSuite = kSuite,
            isAdmin = isAdmin,
            onClick = { hideBottomSheet() },
        )
    }

    fun setKSuite(kSuite: KSuite) {
        this.kSuite = kSuite
    }

    fun setIsAdmin(isAdmin: Boolean) {
        this.isAdmin = isAdmin
    }

    fun setOnClose(listener: () -> Unit) {
        onClose = listener
    }

    fun show() {
        showBottomSheet()
    }

    override fun onDialogFragmentDismissRequest() {
        onClose?.invoke()
    }
}
