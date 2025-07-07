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
@file:OptIn(ExperimentalSerializationApi::class)

package com.infomaniak.mail.utils

import com.infomaniak.core.crossloginui.data.CrossLoginUiAccount
import com.infomaniak.core.login.crossapp.ExternalAccount
import kotlinx.serialization.ExperimentalSerializationApi

data class CrossLoginAccount(
    val tokens: Set<String>,
    val isCurrentlySelectedInAnApp: Boolean,
    val uiAccount: CrossLoginUiAccount,
)

fun List<ExternalAccount>.toCrossLoginAccounts(): List<CrossLoginAccount> = map { it.toCrossLoginAccount() }

fun ExternalAccount.toCrossLoginAccount(): CrossLoginAccount {
    return CrossLoginAccount(
        tokens = tokens,
        isCurrentlySelectedInAnApp = isCurrentlySelectedInAnApp,
        uiAccount = CrossLoginUiAccount(
            id = id,
            name = fullName,
            initials = initials,
            email = email,
            url = avatarUrl,
        ),
    )
}

fun List<CrossLoginAccount>.uiAccounts(): List<CrossLoginUiAccount> = map { it.uiAccount }
