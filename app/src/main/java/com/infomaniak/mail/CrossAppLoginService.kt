package com.infomaniak.mail

import com.infomaniak.core.crossapplogin.back.BaseCrossAppLoginService
import com.infomaniak.mail.data.cache.RealmDatabase
import com.infomaniak.mail.data.models.AppSettings
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class CrossAppLoginService : BaseCrossAppLoginService(
    selectedUserIdFlow = flow {
        val flow = RealmDatabase.appSettings().query<AppSettings>().first().asFlow().map { it.obj?.currentUserId ?: -1 }
        emitAll(flow)
    }.flowOn(Dispatchers.IO)
)
