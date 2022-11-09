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
package com.infomaniak.mail.ui.main.thread.actions

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.databinding.BottomSheetActionsMenuBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.getAttributeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.R as RMaterial

open class ActionsBottomSheetDialog : BottomSheetDialogFragment() {

    lateinit var binding: BottomSheetActionsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetActionsMenuBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    private fun computeUnreadStyle(isSeen: Boolean) = if (isSeen) {
        R.drawable.ic_envelope to R.string.actionMarkAsUnread
    } else {
        R.drawable.ic_envelope_open to R.string.actionMarkAsRead
    }

    private fun computeFavoriteStyle(context: Context, isFavorite: Boolean): Triple<Int, Int, Int> {
        return if (isFavorite) {
            Triple(R.drawable.ic_star_filled, context.getColor(R.color.favoriteYellow), R.string.actionUnstar)
        } else {
            Triple(R.drawable.ic_star, context.getAttributeColor(RMaterial.attr.colorPrimary), R.string.actionStar)
        }
    }

    fun setMarkAsReadUi(isSeen: Boolean) = with(binding.markAsRead) {
        val (readIconRes, readTextRes) = computeUnreadStyle(isSeen)
        setIconResource(readIconRes)
        setText(readTextRes)
    }

    fun setFavoriteUi(isFavorite: Boolean) = with(binding.favorite) {
        val (favoriteIconRes, favoriteTint, favoriteText) = computeFavoriteStyle(context, isFavorite)
        setIconResource(favoriteIconRes)
        setIconTint(favoriteTint)
        setText(favoriteText)
    }

    fun setSpamUi() = with(binding.spam) {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentFolderRole = MainViewModel.currentFolderId.value?.let(FolderController::getFolder)?.role
            val currentFolderIsSpam = currentFolderRole == FolderRole.SPAM
            withContext(Dispatchers.Main) { setText(if (currentFolderIsSpam) R.string.actionNonSpam else R.string.actionSpam) }
        }
    }

    fun ActionItemView.setClosingOnClickListener(callback: (() -> Unit)) {
        setOnClickListener {
            callback()
            findNavController().popBackStack()
        }
    }

    fun MainActionsView.setClosingOnClickListener(callback: ((Int) -> Unit)) {
        setOnItemClickListener { id ->
            callback(id)
            findNavController().popBackStack()
        }
    }
}
