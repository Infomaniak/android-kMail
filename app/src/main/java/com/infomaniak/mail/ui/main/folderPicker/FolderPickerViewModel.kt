/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
import com.infomaniak.mail.data.models.FolderUi
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.coroutineContext
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

    private val ioCoroutineContext = viewModelScope.coroutineContext(ioDispatcher)

    private var searchJob: Job? = null

    private val sourceFolder inline get() = savedStateHandle.get<String>(FolderPickerFragmentArgs::sourceFolderId.name)

    private var allFolderUis = emptyList<Any>()
    val sourceFolderIdLiveData = MutableLiveData<String>()
    val filterResults = MutableLiveData<Pair<List<Any>, Boolean>>()
    var hasAlreadyTrackedSearch = false

    init {
        viewModelScope.launch(ioCoroutineContext) {
            sourceFolderIdLiveData.postValue(sourceFolder)
        }
    }

    fun initFolders(folders: MainViewModel.DisplayedFolders, action: String) {
        allFolderUis = when (action) {
            FolderPickerFragment.SEARCH -> {
                folders.flattenAndAddDividerBeforeFirstCustomFolder(
                    dividerType = FolderPickerAdapter.Item.Divider,
                ).let { baseFolders ->
                    mutableListOf<Any>().apply {
                        add(FolderPickerAdapter.Item.AllFolders)
                        add(FolderPickerAdapter.Item.Divider)
                        addAll(baseFolders)
                    }
                }
            }
            else -> {
                folders.flattenAndAddDividerBeforeFirstCustomFolder(
                    dividerType = FolderPickerAdapter.Item.Divider,
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

    private fun searchFolders(query: CharSequence, shouldDebounce: Boolean) = viewModelScope.launch(ioCoroutineContext) {

        cancelSearch()

        searchJob = launch {

            if (shouldDebounce) {
                delay(FILTER_DEBOUNCE_DURATION)
                ensureActive()
            }

            // When dealing with nested role folders, there can be multiple FolderUi for the same Folder.id, that's why we make a
            // map so there's only one FolderUi per Folder.id
            val filteredFolders = buildMap {
                allFolderUis.forEach { folderUi ->
                    ensureActive()
                    if (folderUi !is FolderUi) return@forEach
                    val folderName = folderUi.folder.role?.folderNameRes?.let(appContext::getString) ?: folderUi.folder.name
                    val isFound = folderName.standardize().contains(query.standardize())
                    if (isFound) set(folderUi.folder.id, folderUi)
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
