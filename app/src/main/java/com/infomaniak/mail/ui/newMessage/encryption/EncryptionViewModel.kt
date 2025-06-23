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
package com.infomaniak.mail.ui.newMessage.encryption

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.userInfo.MergedContactController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class EncryptionViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val mergedContactController: MergedContactController,
    private val snackbarManager: SnackbarManager,
) : AndroidViewModel(application) {

    val unencryptableRecipients: MutableLiveData<List<String>> = MutableLiveData()

    fun checkIfEmailsCanBeEncrypted(emails: List<String>) {
        viewModelScope.launch(ioDispatcher) {
            val unknownEncryptionStatusRecipients = emails.filter { unencryptableRecipients.value?.contains(it) != true }
            if (unknownEncryptionStatusRecipients.isEmpty()) return@launch

            val apiResponse = ApiRepository.isInfomaniakMailboxes(unknownEncryptionStatusRecipients)
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { mailboxHostingStatuses ->
                    val unencryptableEmailAddresses = mergedContactController.updateEncryptionStatus(mailboxHostingStatuses)
                    val newRecipients = (unencryptableRecipients.value ?: emptyList()) + unencryptableEmailAddresses

                    unencryptableRecipients.postValue(newRecipients)
                }
            } else {
                snackbarManager.postValue(appContext.getString(apiResponse.translateError()))
            }
        }
    }

    fun generatePassword(): String {

        val generator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }

        var generatedPassword = ""
        val charactersSetCount = PASSWORD_CHARACTERS_SET.count()
        (0..<PASSWORD_MIN_LENGTH).forEach {
            generatedPassword += PASSWORD_CHARACTERS_SET[generator.nextInt(charactersSetCount)]
        }

        return generatedPassword
    }

    companion object {

        private const val PASSWORD_MIN_LENGTH = 16
        private const val PASSWORD_CHARACTERS_SET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
    }
}
