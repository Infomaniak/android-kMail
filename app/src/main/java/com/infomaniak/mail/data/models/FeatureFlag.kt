/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class FeatureFlag : RealmObject {
    @PrimaryKey
    var id: String? = null
    var isEnabled: Boolean = false

    constructor()

    constructor(type: FeatureFlagType, isEnabled: Boolean) {
        this.id = type.apiName
        this.isEnabled = isEnabled
    }

    enum class FeatureFlagType(val apiName: String) {
        AI("ai-mail-composer")
    }
}
