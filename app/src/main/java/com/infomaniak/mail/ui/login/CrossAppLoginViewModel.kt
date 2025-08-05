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
package com.infomaniak.mail.ui.login

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.crosslogin.back.CrossAppLogin
import com.infomaniak.core.crosslogin.back.DerivedTokenGenerator
import com.infomaniak.core.crosslogin.back.DerivedTokenGeneratorImpl
import com.infomaniak.core.crosslogin.back.ExternalAccount
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.utils.extensions.loginUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class CrossAppLoginViewModel : ViewModel() {

    val availableAccounts: StateFlow<List<ExternalAccount>>
    val selectedAccounts: StateFlow<List<ExternalAccount>>

    val skippedAccountIds = MutableStateFlow(emptySet<Long>())

    val derivedTokenGenerator: DerivedTokenGenerator = DerivedTokenGeneratorImpl(
        coroutineScope = viewModelScope,
        tokenRetrievalUrl = "${loginUrl}token",
        hostAppPackageName = BuildConfig.APPLICATION_ID,
        clientId = BuildConfig.CLIENT_ID,
        userAgent = HttpUtils.getUserAgent,
    )

    private val _availableAccounts = MutableStateFlow(emptyList<ExternalAccount>()).also {
        availableAccounts = it.asStateFlow()
    }

    init {
        selectedAccounts = combine(availableAccounts, skippedAccountIds) { allExternalAccounts, idsToSkip ->
            allExternalAccounts.filter { it.id !in idsToSkip }
        }.stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())
    }

    suspend fun activateUpdates(hostActivity: ComponentActivity): Nothing = coroutineScope {
        val crossAppLogin = CrossAppLogin.forContext(
            context = hostActivity,
            coroutineScope = this + Dispatchers.Default
        )
        hostActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
            _availableAccounts.emit(crossAppLogin.retrieveAccountsFromOtherApps())
        }
        awaitCancellation() // Should never be reached. Unfortunately, `repeatOnLifecycle` doesn't return `Nothing`.
    }
}
