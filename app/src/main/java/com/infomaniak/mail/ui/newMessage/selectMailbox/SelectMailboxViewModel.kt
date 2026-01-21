/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025-2026 Infomaniak Network SA
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.cache.mailboxInfo.MailboxController
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.di.MailboxInfoRealm
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SharedUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.Realm
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
    @MailboxInfoRealm private val mailboxInfoRealm: Realm,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {
    private val _usersWithMailboxes = MutableStateFlow<List<UserMailboxesUi>>(emptyList())
    val usersWithMailboxes: StateFlow<List<UserMailboxesUi>> get() = _usersWithMailboxes

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var defaultMailboxUi: SelectedMailboxUi? = null

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
                mailboxesUi = mailboxController.getMailboxes(user.id).map { mailbox ->
                    MailboxUi(mailboxId = mailbox.mailboxId, emailIdn = mailbox.emailIdn)
                }
            )
        }
    }

    private suspend fun getDefaultUserWithMailbox() {
        val currentUser = AccountUtils.currentUser
        if (currentUser == null) {
            _uiState.value = UiState.Error
            return
        }

        val defaultMailbox = mailboxController.getMailboxes(currentUser.id).firstOrNull { mailbox ->
            mailbox.mailboxId == AccountUtils.currentMailboxId
        }

        if (defaultMailbox != null) {
            val selectedMailbox = SelectedMailboxUi(
                userId = currentUser.id,
                mailboxUi = MailboxUi(mailboxId = defaultMailbox.mailboxId, emailIdn = defaultMailbox.emailIdn),
                avatarUrl = currentUser.avatar,
                initials = currentUser.getInitials()
            )
            defaultMailboxUi = selectedMailbox
            _uiState.value = UiState.DefaultScreen.Idle(selectedMailbox)
        } else {
            _uiState.value = UiState.Error
        }
    }

    fun showSelectionScreen(isShown: Boolean) {
        if (isShown) {
            _uiState.value = UiState.SelectionScreen.NoSelection
        } else {
            defaultMailboxUi?.let { _uiState.value = UiState.DefaultScreen.Idle(it) }
        }
    }

    fun selectMailbox(selectedMailbox: SelectedMailboxUi) {
        _uiState.value = UiState.SelectionScreen.Selected(selectedMailbox)
    }

    suspend fun ensureMailboxIsFetched(userId: Int, mailboxId: Int) {
        val mailbox = mailboxController.getMailbox(userId, mailboxId) ?: return
        if (!mailbox.haveSignaturesBeenFetched) {
            SharedUtils.updateSignatures(mailbox, mailboxInfoRealm, AccountUtils.getHttpClient(userId))
        }
    }

    data class UserMailboxesUi(
        val userId: Int,
        val userEmail: String,
        val avatarUrl: String?,
        val initials: String,
        val fullName: String,
        val mailboxesUi: List<MailboxUi>,
    )

    data class MailboxUi(
        val mailboxId: Int,
        val emailIdn: String
    )

    data class SelectedMailboxUi(
        val userId: Int,
        val mailboxUi: MailboxUi,
        val avatarUrl: String?,
        val initials: String,
    )

    interface SelectedMailboxState {
        val mailboxUi: SelectedMailboxUi
    }

    sealed interface UiState {
        data object Loading : UiState
        data object Error : UiState

        sealed interface DefaultScreen : UiState, SelectedMailboxState {
            data class Idle(override val mailboxUi: SelectedMailboxUi) : DefaultScreen
            data class FetchingNewMailbox(override val mailboxUi: SelectedMailboxUi) : DefaultScreen
        }

        sealed interface SelectionScreen : UiState {
            data class Selected(override val mailboxUi: SelectedMailboxUi) : SelectionScreen, SelectedMailboxState
            data object NoSelection : SelectionScreen
        }
    }
}
