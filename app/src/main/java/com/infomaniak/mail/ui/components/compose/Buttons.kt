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
package com.infomaniak.mail.ui.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.infomaniak.core.ui.compose.basicbutton.BasicButton
import com.infomaniak.core.ui.compose.basics.Typography
import com.infomaniak.core.ui.compose.margin.Margin
import com.infomaniak.core.ui.compose.preview.PreviewLightAndDark
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.theme.MailTheme

@Composable
fun LargeButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonType = ButtonType.Primary,
    enabled: () -> Boolean = { true },
    showIndeterminateProgress: () -> Boolean = { false },
) {
    BasicButton(
        modifier = modifier.height(
            dimensionResource(R.dimen.textButtonPrimaryHeight) - dimensionResource(R.dimen.textButtonPrimaryVerticalInset) * 2
        ),
        colors = style.colors(),
        shape = RoundedCornerShape(dimensionResource(R.dimen.textButtonCornerRadius)),
        onClick = onClick,
        enabled = enabled,
        showIndeterminateProgress = showIndeterminateProgress,
    ) {
        Text(text = title,  style = Typography.bodyMedium)
    }
}

enum class ButtonType(val colors: @Composable () -> ButtonColors) {
    Primary({
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }),
    // There is no “secondary” button because the “secondary” theme is not defined in the app.
    Tertiary({
        ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color.Transparent,
        )
    }),
    Destructive({
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    })
}

@PreviewLightAndDark
@Composable
private fun LargeButtonPreview() {
    MailTheme {
        Surface {
            Column {
                ButtonType.entries.forEach {
                    Row {
                        LargeButton(
                            title = stringResource(R.string.buttonContinue),
                            style = it,
                            onClick = { },
                        )
                    }
                    Spacer(Modifier.height(Margin.Medium))
                }
            }
        }
    }
}
