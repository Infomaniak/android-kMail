package com.infomaniak.mail.data.cache

import android.content.Context
import com.infomaniak.lib.core.utils.transaction

class SettingsPreferences private constructor(context: Context) {

    private val settingsPreferences = context.applicationContext.getSharedPreferences(
        "settings_preferences",
        Context.MODE_PRIVATE,
    )

    var currentUserId
        get() = settingsPreferences.getInt(CURRENT_USER_ID_KEY, DEFAULT_ID)
        set(value) = settingsPreferences.transaction { putInt(CURRENT_USER_ID_KEY, value) }

    var currentMailboxId
        get() = settingsPreferences.getInt(CURRENT_MAILBOX_ID, DEFAULT_ID)
        set(value) = settingsPreferences.transaction { putInt(CURRENT_MAILBOX_ID, value) }

    fun flush() {
        settingsPreferences.transaction { clear() }
    }

    companion object {
        const val DEFAULT_ID = -1

        private const val CURRENT_USER_ID_KEY = "current_user_id_key"
        private const val CURRENT_MAILBOX_ID = "current_mailbox_id"

        @Volatile
        lateinit var INSTANCE: SettingsPreferences

        fun initInstance(context: Context) {
            if (!(::INSTANCE.isInitialized)) synchronized(this) { INSTANCE = SettingsPreferences(context) }
        }
    }
}
