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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import com.infomaniak.core.compose.materialthemefromxml.MaterialThemeFromXml
import com.infomaniak.mail.ui.components.MailBottomSheetScaffold
import kotlinx.coroutines.launch

abstract class MailBottomSheetScaffoldComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var isVisible by mutableStateOf(false)
    private var startHidingAnimation by mutableStateOf(false)

    @Composable
    abstract fun BottomSheetContent()

    protected fun showBottomSheet() {
        isVisible = true
    }

    protected fun hideBottomSheet() {
        startHidingAnimation = true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    final override fun Content() {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(startHidingAnimation) {
            if (startHidingAnimation.not()) return@LaunchedEffect

            scope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                isVisible = false
                startHidingAnimation = false
            }
        }

        MaterialThemeFromXml {
            MailBottomSheetScaffold(
                isVisible = { isVisible },
                onDismissRequest = { isVisible = false },
                sheetState = sheetState,
                content = { BottomSheetContent() }
            )
        }
    }
}
