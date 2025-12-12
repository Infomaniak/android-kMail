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
package com.infomaniak.mail.ui.main.easterEgg

import androidx.annotation.CallSuper
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.core.utils.year
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.trackEasterEggEvent
import com.infomaniak.mail.utils.AccountUtils
import io.sentry.Sentry
import java.util.Calendar
import java.util.Date
import kotlin.random.Random

sealed interface EventsEasterEgg {

    val pack: KSuite?
    val isCorrectPeriod: Boolean
    val matomoName: MatomoMail.MatomoName
    private val isStaff: Boolean get() = AccountUtils.currentUser?.isStaff ?: false

    @CallSuper
    fun shouldTrigger(): Boolean {
        val isAllowed = pack !is KSuite.Pro || isStaff // We only display for individual users not the business ones
        return isCorrectPeriod && isAllowed
    }

    fun show(displayUi: () -> Unit) {
        if (!shouldTrigger()) return
        displayUi()
        Sentry.captureMessage("Easter egg ${matomoName.value} has been triggered! Woohoo!")
        trackEasterEggEvent("${matomoName.value}${Date().year()}")
    }

    data class Halloween(override val pack: KSuite?) : EventsEasterEgg {

        private val calendar by lazy { Calendar.getInstance() }

        override val matomoName = MatomoMail.MatomoName.Halloween
        override val isCorrectPeriod: Boolean
            get() {
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                return (month == Calendar.OCTOBER && day >= 26) || (month == Calendar.NOVEMBER && day <= 1)
            }
    }

    data class Christmas(override val pack: KSuite?) : EventsEasterEgg {

        private val calendar by lazy { Calendar.getInstance() }

        override val matomoName = MatomoMail.MatomoName.Xmas
        override val isCorrectPeriod: Boolean
            get() {
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                return month == Calendar.DECEMBER && day <= 25
            }

        override fun shouldTrigger(): Boolean {
            val spawnProbability = calendar.get(Calendar.DAY_OF_MONTH) / 25.0f
            return super.shouldTrigger() && Random.nextFloat() < spawnProbability
        }
    }

    data class NewYear(override val pack: KSuite?) : EventsEasterEgg {

        private val calendar by lazy { Calendar.getInstance() }

        override val matomoName = MatomoMail.MatomoName.NewYear
        override val isCorrectPeriod: Boolean
            get() {
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                return (month == Calendar.DECEMBER && day >= 31) || (month == Calendar.JANUARY && day <= 1)
            }
    }
}
