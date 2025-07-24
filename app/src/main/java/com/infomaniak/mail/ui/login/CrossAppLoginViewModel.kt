/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.autoCancelScope
import com.infomaniak.core.login.crossapp.CrossAppLogin
import com.infomaniak.core.login.crossapp.DerivedTokenGenerator
import com.infomaniak.core.login.crossapp.DerivedTokenGeneratorImpl
import com.infomaniak.core.login.crossapp.ExternalAccount
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.utils.extensions.loginUrl
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class CrossAppLoginViewModel : ViewModel() {

    val crossLoginAccounts = MutableLiveData(emptyList<ExternalAccount>())
    val crossLoginSelectedIds = MutableLiveData(emptySet<Int>())

    val derivedTokenGenerator: DerivedTokenGenerator = DerivedTokenGeneratorImpl(
        coroutineScope = viewModelScope,
        tokenRetrievalUrl = "${loginUrl}token",
        hostAppPackageName = BuildConfig.APPLICATION_ID,
        clientId = BuildConfig.CLIENT_ID,
        userAgent = HttpUtils.getUserAgent,
    )

    suspend fun getCrossLoginAccounts(context: Context): List<ExternalAccount> = autoCancelScope {
        CrossAppLogin.forContext(context, coroutineScope = this).retrieveAccountsFromOtherApps()
    }
}
