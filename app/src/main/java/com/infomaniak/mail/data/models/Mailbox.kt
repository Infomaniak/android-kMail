/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.models

data class Mailbox(
    val objectId: String,
    val uuid: String,
    val email: String,
    val emailIdn: String,
    val mailbox: String,
    val realMailbox: String,
    val linkId: Int,
    val mailboxId: Int,
    val hostingId: Int,
    val isPrimary: Boolean,
    val passwordStatus: String,
    val isPasswordValid: Boolean,
    val isValid: Boolean,
    val isLocked: Boolean,
    val hasSocialAndCommercialFiltering: Boolean,
    val showConfigModal: Boolean,
    val forceResetPassword: Boolean,
    val mdaVersion: String,
    val isLimited: Boolean,
    val isFree: Boolean,
    val dailyLimit: Int,
    val userId: Int,
)