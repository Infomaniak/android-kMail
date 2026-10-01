/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable

class ExternalComposeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetIntent = Intent(this, NewMessageActivity::class.java).apply {
            action = intent.action
            data = intent.data
            type = intent.type
            clipData = intent.clipData
            flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            copyEmails(intent, Intent.EXTRA_EMAIL)
            copyEmails(intent, Intent.EXTRA_CC)
            copyEmails(intent, Intent.EXTRA_BCC)

            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { putExtra(Intent.EXTRA_TEXT, it) }
            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)?.let { putExtra(Intent.EXTRA_STREAM, it) }
            intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let {
                putExtra(Intent.EXTRA_STREAM, it)
            }
        }

        startActivity(targetIntent)
        finish()
    }

    private fun Intent.copyEmails(sourceIntent: Intent, key: String) {
        val emails = sourceIntent.getStringArrayExtra(key)
            ?: sourceIntent.getStringArrayListExtra(key)?.toTypedArray()
            ?: sourceIntent.getStringExtra(key)?.let { arrayOf(it) }
        emails?.let { putExtra(key, it) }
    }
}
