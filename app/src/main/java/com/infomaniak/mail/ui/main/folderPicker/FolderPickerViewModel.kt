/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2026 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.folderPicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.extensions.appContext
import com.infomaniak.mail.utils.extensions.standardize
import com.infomaniak.mail.utils.flattenAndAddDividerBeforeFirstCustomFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private var searchJob: Job? = null

    private val sourceFolderId inline get() = savedStateHandle.get<String>(FolderPickerFragmentArgs::sourceFolderId.name)

    private var allFolderUis = emptyList<FolderPickerItem>()
    val sourceFolderIdLiveData = MutableLiveData<String>()
    val filterResults = MutableLiveData<Pair<List<FolderPickerItem>, Boolean>>()
    var hasAlreadyTrackedSearch = false

    init {
        viewModelScope.launch(ioDispatcher) {
            sourceFolderIdLiveData.postValue(sourceFolderId)
        }
    }

    fun initFolders(folders: MainViewModel.DisplayedFolders, action: FolderPickerAction) {
        allFolderUis = when (action) {
            FolderPickerAction.SEARCH -> {
                val baseFolders = folders.flattenAndAddDividerBeforeFirstCustomFolder(
                    dividerType = FolderPickerItem.Divider,
                )
                mutableListOf<FolderPickerItem>().apply {
                    add(FolderPickerItem.AllFolders)
                    add(FolderPickerItem.Divider)
                    addAll(baseFolders)
                }
            }
            else -> {
                folders.flattenAndAddDividerBeforeFirstCustomFolder(
                    dividerType = FolderPickerItem.Divider,
                    excludedFolderRoles = setOf(FolderRole.SNOOZED, FolderRole.SCHEDULED_DRAFTS, FolderRole.DRAFT),
                )
            }
        }.also { folders ->
            filterResults.postValue(folders to true)
        }
    }

    fun filterFolders(query: CharSequence?, shouldDebounce: Boolean) {
        if (query?.isNotBlank() == true) {
            searchFolders(query, shouldDebounce)
        } else {
            cancelSearch()
            filterResults.value = allFolderUis to true
        }
    }

    private fun searchFolders(query: CharSequence, shouldDebounce: Boolean) = viewModelScope.launch(ioDispatcher) {

        cancelSearch()

        searchJob = launch {

            if (shouldDebounce) {
                delay(FILTER_DEBOUNCE_DURATION)
                ensureActive()
            }

            // When dealing with nested role folders, there can be multiple FolderUi for the same Folder.id, that's why we make a
            // map so there's only one FolderUi per Folder.id
            val filteredFolders = buildMap {
                allFolderUis.forEach { folderItem ->
                    ensureActive()
                    if (folderItem !is FolderPickerItem.Folder) return@forEach
                    val folder = folderItem.folderUi.folder
                    val folderName = folder.role?.folderNameRes?.let(appContext::getString) ?: folder.name
                    val isFound = folderName.standardize().contains(query.standardize())
                    if (isFound) set(folder.id, folderItem)
                }
            }

            filterResults.postValue(filteredFolders.values.toList() to false)
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
    }

    override fun onCleared() {
        cancelSearch()
        super.onCleared()
    }

    companion object {
        private const val FILTER_DEBOUNCE_DURATION = 300L
    }
}
