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
package com.infomaniak.mail.ui.main.thread.encryption

import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetAttendeesBinding
import com.infomaniak.mail.ui.main.thread.actions.ActionsBottomSheetDialog
import com.infomaniak.mail.utils.UiUtils

class UnencryptableRecipientsBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetAttendeesBinding by safeBinding()
    private val navigationArgs: UnencryptableRecipientsBottomSheetDialogArgs by navArgs()
    override val mainViewModel = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetAttendeesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        val recipients = navigationArgs.unencryptableRecipients
        root.title = context.getString(R.string.encryptedRecipientRequiringPasswordTitle, recipients.count())
        attendeeRecyclerView.adapter = UnencryptableRecipientsAdapter(recipients)

        val margin = context.resources.getDimensionPixelSize(R.dimen.dividerHorizontalPadding)
        val divider = DividerItemDecorator(InsetDrawable(UiUtils.dividerDrawable(context), margin, 0, margin, 0))
        attendeeRecyclerView.addItemDecoration(divider)
    }
}
