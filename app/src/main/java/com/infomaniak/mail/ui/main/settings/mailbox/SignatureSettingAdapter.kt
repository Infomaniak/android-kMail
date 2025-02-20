/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings.mailbox

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.ItemSettingsSignatureBinding
import com.infomaniak.mail.ui.main.settings.mailbox.SignatureSettingAdapter.SettingsSignatureViewHolder

class SignatureSettingAdapter(
    private val canManageSignature: Boolean,
    private val isFreeMailbox: Boolean,
    private val onSignatureSelected: (Signature, Boolean) -> Unit,
) : Adapter<SettingsSignatureViewHolder>() {

    private var signatures: List<Signature> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsSignatureViewHolder {
        return SettingsSignatureViewHolder(
            ItemSettingsSignatureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SettingsSignatureViewHolder, position: Int): Unit = with(holder.binding.root) {
        val signature = signatures[position]
        setText(signature.name)
        if (signature.isDefault) check() else uncheck()
        isEnabled = canManageSignature

        val shouldBlockSignature = signature.isDummy && !signature.isDefault && isFreeMailbox
        setMyKSuiteChipVisibility(shouldBlockSignature)

        setOnClickListener { onSignatureSelected(signature, shouldBlockSignature) }
    }

    override fun getItemCount(): Int = signatures.count()

    @SuppressLint("NotifyDataSetChanged")
    fun setSignatures(context: Context, newSignatures: List<Signature>) {
        val hasDefaultSignature = newSignatures.none { it.isDefault }
        val dummySignature = Signature.getDummySignature(context, isDefault = hasDefaultSignature)
        signatures = newSignatures.toMutableList().apply { add(0, dummySignature) }
        notifyDataSetChanged()
    }

    class SettingsSignatureViewHolder(val binding: ItemSettingsSignatureBinding) : ViewHolder(binding.root)
}
