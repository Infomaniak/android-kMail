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
package com.infomaniak.mail

import com.infomaniak.core.network.ApiEnvironment

private val host = ApiEnvironment.current.host

val CREATE_ACCOUNT_URL = "https://welcome.$host/signup/ikmail?app=true"
val CREATE_ACCOUNT_SUCCESS_HOST = "ksuite.$host"
val CREATE_ACCOUNT_CANCEL_HOST = "welcome.$host"
val IMPORT_EMAILS_URL = "https://import-email.$host"
val MAIL_API = "https://mail.$host"

val CHATBOT_URL = "https://www.$host/chatbot"
val FAQ_URL = "https://www.$host/fr/support/faq/admin2/service-mail"
val MANAGE_SIGNATURES_URL = "https://mail.$host/0/settings/signatures"
