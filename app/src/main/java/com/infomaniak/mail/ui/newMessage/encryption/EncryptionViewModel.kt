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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.SingleLiveEvent
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.extensions.appContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class EncryptionViewModel @Inject constructor(
    application: Application,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val snackbarManager: SnackbarManager,
) : AndroidViewModel(application) {

    // We keep a list here instead of a set, because each recipient can be added in several field (Eg. To, CC, BCC) so there can
    // be several "instance" of the recipient that are unencryptable
    val unencryptableRecipients: MutableLiveData<List<String>?> = MutableLiveData(null)
    val isCheckingEmails: SingleLiveEvent<Boolean> = SingleLiveEvent(false)

    private var emailsCheckingJob: Job? = null
    private val emailsBeingChecked: MutableList<String> = mutableListOf()

    fun checkIfEmailsCanBeEncrypted(emails: List<String>) {
        emailsCheckingJob?.cancel(AutoBulkCallCancellationException())
        emailsBeingChecked.addAll(emails)

        emailsCheckingJob = viewModelScope.launch(ioDispatcher) {
            isCheckingEmails.postValue(true)

            runCatching {
                // The API only support 10 emails per call so we have to chunk the list
                val chunkedEmailsBeingChecked = emailsBeingChecked.chunked(size = MAX_EMAILS_PER_EXISTS_CALL).map { emailsChunk ->
                    async(ioDispatcher) { computeUnencryptableAddressesFromApi(emailsChunk) }
                }

                val newUnencryptableRecipients = chunkedEmailsBeingChecked.awaitAll().flatten()
                val existingUnencryptableRecipients = unencryptableRecipients.value ?: emptyList()
                clearDataAndPostResult(newUnencryptableRecipients + existingUnencryptableRecipients)
            }.onFailure { exception ->
                // We don't post result here as a new check will be executed
                if (exception is AutoBulkCallCancellationException) throw CancellationException()

                val existingUnencryptableRecipients = unencryptableRecipients.value ?: emptyList()
                clearDataAndPostResult(emailsBeingChecked + existingUnencryptableRecipients)
                if (exception is CancellationException) throw exception
            }
        }
    }

    /**
     * Takes the [emailsBeingChecked] list and check with the API if it can be encrypted.
     *
     * @return a list of the new unencryptable recipients
     */
    private suspend fun computeUnencryptableAddressesFromApi(emails: List<String>): List<String> {
        // By default, all the new addresses are considered unencryptable
        val newUnencryptableRecipients = emails.toMutableList()

        val apiResponse = ApiRepository.isInfomaniakMailboxes(emails.toSet())
        if (apiResponse.isSuccess()) {
            apiResponse.data?.let { mailboxHostingStatuses ->
                mailboxHostingStatuses.forEach { mailboxStatus ->
                    if (mailboxStatus.isInfomaniakHosted) newUnencryptableRecipients.removeAll { it == mailboxStatus.email }
                }
            }
        } else {
            // In case of error during the encryptable check, we consider all recipients as unencryptable and
            // we can stop the other call because the user will need a password either way
            snackbarManager.postValue(appContext.getString(apiResponse.translateError()))
            emailsCheckingJob?.cancel()
        }

        return newUnencryptableRecipients
    }

    fun cancelEmailCheckingIfNeeded(email: String) {
        if (email in emailsBeingChecked) emailsBeingChecked.remove(email)
        if (emailsBeingChecked.isEmpty()) {
            emailsCheckingJob?.cancel()
            emailsCheckingJob = null
        }
    }

    fun generatePassword(): String {
        val generator = SecureRandom.getInstanceStrong()
        var generatedPassword = ""
        val charactersSetCount = PASSWORD_CHARACTERS_SET.count()
        (0..<PASSWORD_MIN_LENGTH).forEach { _ ->
            generatedPassword += PASSWORD_CHARACTERS_SET[generator.nextInt(charactersSetCount)]
        }

        return generatedPassword
    }

    private fun clearDataAndPostResult(recipients: List<String>) {
        emailsBeingChecked.clear()
        unencryptableRecipients.postValue(recipients)
        isCheckingEmails.postValue(false)
    }

    private class AutoBulkCallCancellationException : CancellationException()

    companion object {
        private const val MAX_EMAILS_PER_EXISTS_CALL = 10
        private const val PASSWORD_MIN_LENGTH = 16
        private const val PASSWORD_CHARACTERS_SET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
    }
}
