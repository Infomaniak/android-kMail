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
package com.infomaniak.mail.data.cache.userInfo

import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.FeatureFlag.FeatureFlagType
import com.infomaniak.mail.di.UserInfoRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FeatureFlagController @Inject constructor(@UserInfoRealm private val userInfoRealm: Realm) {

    private fun getFeatureFlagQuery(featureFlagType: FeatureFlagType): RealmSingleQuery<FeatureFlag> {
        return userInfoRealm.query<FeatureFlag>("${FeatureFlag::id.name} == $0", featureFlagType.apiName).first()
    }

    fun getFeatureFlagAsync(featureFlagType: FeatureFlagType): Flow<SingleQueryChange<FeatureFlag>> {
        return getFeatureFlagQuery(featureFlagType).asFlow()
    }

    fun upsertFeatureFlag(featureFlag: FeatureFlag) {
        userInfoRealm.writeBlocking {
            copyToRealm(featureFlag, UpdatePolicy.ALL)
        }
    }
}
