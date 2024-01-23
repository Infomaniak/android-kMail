/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_ALL_NAME
import com.infomaniak.mail.MatomoMail.ACTION_REPLY_NAME
import com.infomaniak.mail.MatomoMail.trackEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.databinding.BottomSheetReplyBinding
import com.infomaniak.mail.utils.safeNavigateToNewMessageActivity
import com.infomaniak.mail.utils.setSystemBarsColors

open class ReplyBottomSheetDialog : ActionsBottomSheetDialog() {

    private var binding: BottomSheetReplyBinding by safeBinding()
    private val navigationArgs: ReplyBottomSheetDialogArgs by navArgs()

    private val currentClassName: String by lazy { ReplyBottomSheetDialog::class.java.name }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetReplyBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors(statusBarColor = null, navigationBarColor = R.color.backgroundColorSecondary)

        binding.mainActions.setClosingOnClickListener { id: Int ->
            val replyMode = if (id == R.id.actionReplyAll) DraftMode.REPLY_ALL else DraftMode.REPLY

            safeNavigateToNewMessageActivity(
                draftMode = replyMode,
                previousMessageUid = messageUid,
                currentClassName = currentClassName,
                shouldLoadDistantResources = navigationArgs.shouldLoadDistantResources,
            )

            trackEvent("replyBottomSheet", if (id == R.id.actionReply) ACTION_REPLY_NAME else ACTION_REPLY_ALL_NAME)
        }
    }
}
