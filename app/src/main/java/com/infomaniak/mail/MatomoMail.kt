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
package com.infomaniak.mail

import android.content.Context
import androidx.fragment.app.Fragment
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import org.matomo.sdk.Tracker

object MatomoMail : MatomoCore {

    override val Context.tracker: Tracker get() = (this as ApplicationMain).matomoTracker
    override val siteId = 9

    fun Context.trackMessageEvent(name: String, value: Float? = null) {
        trackEvent("message", name, value = value)
    }

    fun Context.trackNewMessageEvent(name: String, value: Float? = null, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("newMessage", name, action, value)
    }

    fun Fragment.trackNewMessageEvent(name: String, value: Float? = null) {
        activity?.trackNewMessageEvent(name, value)
    }

    fun Fragment.trackMenuDrawerEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "menuDrawer", name = name, value = value?.toFloat())
    }

    // We need to invert this logical value to keep a coherent value for analytics
    fun Boolean.toMailActionValue() = (!this).toFloat()
}
