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
package com.infomaniak.mail.ui.newMessage.selectMailbox

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AccountUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectMailboxViewModel @Inject constructor(
    application: Application,
    private val mailboxController: MailboxController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {
    private val _usersWithMailboxes = MutableStateFlow<List<UserMailboxesUi>>(emptyList())
    val usersWithMailboxes: StateFlow<List<UserMailboxesUi>> get() = _usersWithMailboxes

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()
    val selectedMailboxState = mutableStateOf<SelectedMailboxUi?>(null)

    private var defaultMailbox: SelectedMailboxUi? = null

    init {
        viewModelScope.launch(ioDispatcher) {
            getDefaultUserWithMailbox()
            getUsersAndMailboxes()
        }
    }

    private suspend fun getUsersAndMailboxes() {
        _usersWithMailboxes.value = AccountUtils.getAllUsersSync().map { user ->
            UserMailboxesUi(
                userId = user.id,
                userEmail = user.email,
                avatarUrl = user.avatar,
                initials = user.getInitials(),
                fullName = user.displayName ?: user.run { "$firstname $lastname" },
                mailboxes = mailboxController.getMailboxes(user.id).map { mailbox ->
                    MailboxUi(
                        mailboxId = mailbox.mailboxId,
                        email = mailbox.email
                    )
                }
            )
        }
    }

    private suspend fun getDefaultUserWithMailbox() {
        AccountUtils.currentUser?.let { currentUser ->
            val selectedMailbox = SelectedMailboxUi(
                userId = currentUser.id,
                mailbox = mailboxController.getMailboxes(currentUser.id)
                    .first { mailbox ->
                        mailbox.mailboxId == AccountUtils.currentMailboxId
                    }
                    .let { mailbox ->
                        MailboxUi(
                            mailboxId = mailbox.mailboxId,
                            email = mailbox.email
                        )
                    },
                avatarUrl = currentUser.avatar,
                initials = currentUser.getInitials()
            )
            defaultMailbox = selectedMailbox
            _uiState.value = UiState.DefaultMailbox(selectedMailbox)
        }
    }

    fun chooseAnotherMailbox(choosingAnotherMailbox: Boolean) {
        if (choosingAnotherMailbox) {
            _uiState.value = UiState.SelectMailbox(selectedMailboxState)
        } else {
            defaultMailbox?.let { _uiState.value = UiState.DefaultMailbox(it) }
        }
    }

    fun selectMailbox(selectedMailbox: SelectedMailboxUi?) {
        selectedMailboxState.value = selectedMailbox
    }

    data class UserMailboxesUi(
        val userId: Int,
        val userEmail: String,
        val avatarUrl: String?,
        val initials: String,
        val fullName: String,
        val mailboxes: List<MailboxUi>,
    )

    data class MailboxUi(
        val mailboxId: Int,
        val email: String
    )

    data class SelectedMailboxUi(
        val userId: Int,
        val mailbox: MailboxUi,
        val avatarUrl: String?,
        val initials: String,
    )

    sealed interface UiState {
        data object Loading : UiState
        data class DefaultMailbox(val defaultMailbox: SelectedMailboxUi) : UiState
        data class SelectMailbox(val selectedMailbox: State<SelectedMailboxUi?>) : UiState
    }
}
