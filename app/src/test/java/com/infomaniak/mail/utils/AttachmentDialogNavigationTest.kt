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
package com.infomaniak.mail.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.ui.main.thread.actions.AttachmentActionsBottomSheetDialog
import com.infomaniak.mail.utils.extensions.AttachmentExt
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType.OPEN_WITH
import com.infomaniak.mail.utils.extensions.AttachmentExt.createDownloadDialogNavArgs
import com.infomaniak.mail.utils.extensions.navigateToDownloadProgressDialog
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("DEPRECATION")
class AttachmentDialogNavigationTest {

    private val fragment = mockk<Fragment>()
    private val navController = mockk<NavController>()
    private val currentDestination = mockk<NavDestination>()
    private val attachment = mockk<Attachment>()
    private val args = mockk<Bundle>()
    private val navigationOptions = slot<NavOptions>()

    @Before
    fun setUp() {
        mockkStatic("androidx.navigation.fragment.FragmentKt", "com.infomaniak.core.legacy.utils.ExtensionsKt")
        mockkObject(AttachmentExt)
        every { fragment.findNavController() } returns navController
        every { navController.currentDestination } returns currentDestination
        every { currentDestination.id } returns R.id.attachmentActionsBottomSheetDialog
        every { attachment.createDownloadDialogNavArgs(OPEN_WITH) } returns args
        every {
            fragment.safeNavigate(
                resId = R.id.downloadAttachmentProgressDialog,
                args = args,
                navOptions = capture(navigationOptions),
                currentClassName = AttachmentActionsBottomSheetDialog::class.java.name,
            )
        } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(AttachmentExt)
        unmockkStatic("androidx.navigation.fragment.FragmentKt", "com.infomaniak.core.legacy.utils.ExtensionsKt")
    }

    @Test
    fun actionDialog_replacesTheActionsSheetInTheSameNavigation() {
        fragment.navigateToDownloadProgressDialog(attachment, OPEN_WITH, closeAttachmentActions = true)

        assertEquals(R.id.attachmentActionsBottomSheetDialog, navigationOptions.captured.popUpToId)
        assertTrue(navigationOptions.captured.isPopUpToInclusive())
        verify(exactly = 1) {
            fragment.safeNavigate(
                resId = R.id.downloadAttachmentProgressDialog,
                args = args,
                navOptions = any(),
                currentClassName = AttachmentActionsBottomSheetDialog::class.java.name,
            )
        }
        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun repeatedActionClick_doesNotStackAnotherDownloadDialog() {
        every { currentDestination.id } returns R.id.downloadAttachmentProgressDialog

        fragment.navigateToDownloadProgressDialog(attachment, OPEN_WITH, closeAttachmentActions = true)

        verify(exactly = 0) {
            fragment.safeNavigate(
                resId = any(),
                args = any(),
                navOptions = any(),
                currentClassName = any(),
            )
        }
    }
}
