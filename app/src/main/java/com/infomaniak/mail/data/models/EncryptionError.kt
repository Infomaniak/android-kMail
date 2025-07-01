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
package com.infomaniak.mail.data.models

import com.infomaniak.core.utils.ApiEnum

enum class EncryptionError(override val apiValue: String) : ApiEnum {
    KeyNotFound("key_not_found"),
    KeyBadFormat("key_bad_formatted"),
    Unknown("unknown"),
    RouteNotFound("route_not_found"),
    BadFormat("bad_formatted"),
    WrongPassword("wrong_password"),
    ServiceNotFound("service_not_found"),
}
