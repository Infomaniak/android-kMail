package com.infomaniak.mail.utils.date

import android.content.Context
import android.content.res.Resources
import com.infomaniak.mail.R
import com.infomaniak.mail.utils.date.DateFormatUtils.formatDelayText
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatUtilsTest {

    private val context = mockk<Context>()
    private val resources = mockk<Resources>()

    init {
        every { context.resources } returns resources
    }

    @Test
    fun `formatDelayText returns days when delay is 1 day`() {
        val delayMinutes = 24 * 60 * 1 // 1 day
        val expectedText = "1 day"
        
        every { resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 1, 1) } returns expectedText

        val result = context.formatDelayText(delayMinutes)

        assertEquals(expectedText, result)
    }

    @Test
    fun `formatDelayText returns hours when delay is 1 hour`() {
        val delayMinutes = 60 // 1 hour
        val expectedText = "1 hour"
        
        every { resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, 1, 1) } returns expectedText

        val result = context.formatDelayText(delayMinutes)

        assertEquals(expectedText, result)
    }

    @Test
    fun `formatDelayText returns days when delay is 3 days`() {
        val delayMinutes = 24 * 60 * 3 // 3 days
        val expectedText = "3 days"
        
        every { resources.getQuantityString(R.plurals.daysBeforeSendingReminder, 3, 3) } returns expectedText

        val result = context.formatDelayText(delayMinutes)

        assertEquals(expectedText, result)
    }

    @Test
    fun `formatDelayText returns hours when delay is 3 hours`() {
        val delayMinutes = 60 * 3 // 3 hours
        val expectedText = "3 hours"
        
        every { resources.getQuantityString(R.plurals.hoursBeforeSendingReminder, 3, 3) } returns expectedText

        val result = context.formatDelayText(delayMinutes)

        assertEquals(expectedText, result)
    }
}
