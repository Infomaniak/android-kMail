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
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import com.infomaniak.core.ui.compose.basics.bottomsheet.LocalBottomSheetTheme
import com.infomaniak.core.ui.compose.basics.bottomsheet.ProvideBottomSheetTheme
import com.infomaniak.core.ui.compose.basics.bottomsheet.ThemedBottomSheetScaffold
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.mail.ui.theme.MailTheme
import kotlinx.coroutines.launch

// This view extends FrameLayout instead of a ComposeView to avoid system initiated animations that try to call addView() on this
// custom view which throws a UnsupportedOperationException when called on a ComposeView.
abstract class MailBottomSheetScaffoldComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var isVisible by mutableStateOf(false)
    private var startHidingAnimation by mutableStateOf(false)

    protected open val containerColor: Color? = null
    protected open val title: String? = null
    protected open val bottomPadding: Dp = Margin.Medium
    protected open val skipPartiallyExpanded: Boolean = false

    @Composable
    abstract fun BottomSheetContent()

    fun showBottomSheet() {
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
        addView(
            ComposeView(context, attrs, defStyleAttr).apply {
                setContent { ComposeViewContent() }
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ComposeViewContent() {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded)
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
                when (val color = containerColor) {
                    null -> Scaffold(sheetState)
                    else -> ProvideBottomSheetTheme(LocalBottomSheetTheme.current.copy(containerColor = color)) {
                        Scaffold(sheetState)
                    }
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun Scaffold(sheetState: SheetState) {
        ThemedBottomSheetScaffold(
            onDismissRequest = {
                isVisible = false
                onDialogFragmentDismissRequest()
            },
            sheetState = sheetState,
            title = title,
            content = {
                BottomSheetContent()
                Spacer(Modifier.height(bottomPadding))
            },
        )
    }
}
