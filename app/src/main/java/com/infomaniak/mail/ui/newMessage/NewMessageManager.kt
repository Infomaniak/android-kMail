/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.newMessage

import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.infomaniak.mail.databinding.FragmentNewMessageBinding

abstract class NewMessageManager {

    private var _newMessageViewModel: NewMessageViewModel? = null
    protected val newMessageViewModel: NewMessageViewModel get() = _newMessageViewModel!!
    private var _binding: FragmentNewMessageBinding? = null
    protected val binding: FragmentNewMessageBinding get() = _binding!!
    private var _fragment: NewMessageFragment? = null
    protected val fragment: NewMessageFragment get() = _fragment!!

    val viewLifecycleOwner get() = fragment.viewLifecycleOwner
    val childFragmentManager get() = fragment.childFragmentManager
    val resources get() = fragment.resources
    val context get() = fragment.requireContext()

    protected fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        freeReferences: (() -> Unit)? = null,
    ) {
        _newMessageViewModel = newMessageViewModel
        _binding = binding
        _fragment = fragment

        onFreeReferences {
            freeReferences?.invoke()
            _newMessageViewModel = null
            _binding = null
            _fragment = null
        }
    }

    private fun onFreeReferences(setReferencesToNull: () -> Unit) {
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _: LifecycleOwner, event: Event ->
            if (event == Event.ON_DESTROY) setReferencesToNull()
        })
    }
}
