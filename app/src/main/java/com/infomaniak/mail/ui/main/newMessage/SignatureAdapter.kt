/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.newMessage

import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.databinding.ItemSignatureBinding

class SignatureAdapter(
    private var signatures: List<Signature>
) : ListAdapter {

    private var selectedSignatureId: Long = -1

    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit

    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

    override fun getCount(): Int = signatures.count()

    override fun getItem(position: Int): Signature = signatures[position]

    override fun getItemId(position: Int): Long = getItem(position).id.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return convertView ?: ItemSignatureBinding.inflate(LayoutInflater.from(parent!!.context), parent, false).apply {
            val signature = getItem(position)
            fullNameAndName.text = "${signature.addressName} (${signature.name})"
            emailAddress.text = signature.senderIdn
            // TODO : Add a selected style to the selected item
        }.root
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun getViewTypeCount(): Int = 1

    override fun isEmpty(): Boolean = count == 0

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = getItemId(position) != selectedSignatureId

    // fun updateSignatures(newSignatures: List<Signature>) {
    //     signatures = newSignatures
    // }

    fun updateSelectedSignature(newSelectedSignatureId: Int) {
        selectedSignatureId = newSelectedSignatureId.toLong()
    }
}
