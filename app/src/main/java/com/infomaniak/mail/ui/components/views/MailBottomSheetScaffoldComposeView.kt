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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.infomaniak.core.compose.basics.bottomsheet.ThemedBottomSheetScaffold
import com.infomaniak.mail.ui.theme.MailTheme
import kotlinx.coroutines.launch

abstract class MailBottomSheetScaffoldComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var isVisible by mutableStateOf(false)
    private var startHidingAnimation by mutableStateOf(false)

    protected open val title: String? = null
    protected open val dragHandleBackgroundColor: Color? = null

    @Composable
    abstract fun BottomSheetContent()

    protected fun showBottomSheet() {
        isVisible = true
    }

    protected fun hideBottomSheet() {
        startHidingAnimation = true
    }

    /**
     * Only needed when used in a DialogFragment.
     * Use it to close the DialogFragment completely.
     */
    protected open fun onDialogFragmentDismissRequest() = Unit

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
                onDialogFragmentDismissRequest()
            }
        }

        MailTheme {
            if (isVisible) {
                ThemedBottomSheetScaffold(
                    onDismissRequest = {
                        isVisible = false
                        onDialogFragmentDismissRequest()
                    },
                    sheetState = sheetState,
                    title = title,
                    content = { BottomSheetContent() },
                )
            }
        }
    }
}
