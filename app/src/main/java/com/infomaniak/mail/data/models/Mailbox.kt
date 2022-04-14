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

import com.google.gson.annotations.SerializedName

data class Mailbox(
    val uuid: String,
    val email: String,
    @SerializedName("email_idn")
    val emailIdn: String,
    val mailbox: String,
    @SerializedName("real_mailbox")
    val realMailbox: String,
    @SerializedName("link_id")
    val linkId: Int,
    @SerializedName("mailbox_id")
    val mailboxId: Int,
    @SerializedName("hosting_id")
    val hostingId: Int,
    @SerializedName("is_primary")
    val isPrimary: Boolean,
    @SerializedName("password_status")
    val passwordStatus: String,
    @SerializedName("is_password_valid")
    val isPasswordValid: Boolean,
    @SerializedName("is_valid")
    val isValid: Boolean,
    @SerializedName("is_locked")
    val isLocked: Boolean,
    @SerializedName("has_social_and_commercial_filtering")
    val hasSocialAndCommercialFiltering: Boolean,
    @SerializedName("show_config_modal")
    val showConfigModal: Boolean,
    @SerializedName("force_reset_password")
    val forceResetPassword: Boolean,
    @SerializedName("mda_version")
    val mdaVersion: String,
    @SerializedName("is_limited")
    val isLimited: Boolean,
    @SerializedName("is_free")
    val isFree: Boolean,
    @SerializedName("daily_limit")
    val dailyLimit: Int,

    /**
     * Local
     */
    val userId: Int,
    val objectId: String,
)