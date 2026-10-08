package com.pandagallery.app.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.vault.PrivateMediaItem
import com.pandagallery.app.data.vault.PrivateFolder
import com.pandagallery.app.data.vault.PrivateVaultRepository
import com.pandagallery.app.data.vault.RestoreDestination
import com.pandagallery.app.data.local.PreferencesDataSource.PinLock
import com.pandagallery.app.data.security.PinLockManager
import com.pandagallery.app.data.security.PinVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PrivateVaultViewModel @Inject constructor(
    private val vault: PrivateVaultRepository,
    private val pinLocks: PinLockManager,
    preferences: PreferencesDataSource,
) : ViewModel() {
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val folderError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PrivateVaultUiState> = combine(
        vault.observeItems(),
        vault.observeFolders(),
        preferences.userPreferencesFlow,
        selectedIds,
        folderError,
    ) { items, folders, prefs, selection, currentFolderError ->
        val existingSelection = selection intersect items.mapTo(mutableSetOf()) { it.id }
        PrivateVaultUiState(
            items = items,
            folders = folders,
            hasPin = prefs.hasPrivateVaultPin,
            biometricsEnabled = prefs.privateVaultBiometricsEnabled,
            allowScreenshots = prefs.allowPrivateAlbumScreenshots,
            autoPlayVideos = prefs.autoPlayVideos,
            preferencesLoaded = true,
            selectedIds = existingSelection,
            folderError = currentFolderError,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrivateVaultUiState())

    /**
     * Checks the Private folder's PIN. Wrong guesses are counted and slowed down by
     * [PinLockManager]; the screen only learns the verdict.
     */
    suspend fun verifyPin(pin: String): PinVerification = pinLocks.verify(PinLock.PRIVATE_VAULT, pin)

    /** Sets the Private folder's PIN on first entry, when none exists yet. */
    suspend fun setPin(pin: String) = pinLocks.setPin(PinLock.PRIVATE_VAULT, pin)

    suspend fun lockoutRemainingMillis(): Long = pinLocks.lockoutRemainingMillis(PinLock.PRIVATE_VAULT)

    suspend fun preview(item: PrivateMediaItem): File = vault.previewFile(item)

    fun toggleSelection(item: PrivateMediaItem) {
        selectedIds.update { selected ->
            if (item.id in selected) selected - item.id else selected + item.id
        }
    }

    fun selectAll(items: List<PrivateMediaItem>) {
        selectedIds.value = items.mapTo(mutableSetOf()) { it.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun setSelectedIds(ids: Set<String>) {
        selectedIds.value = ids
    }

    fun restore(items: List<PrivateMediaItem>, destination: RestoreDestination) = viewModelScope.launch {
        vault.restore(items, destination)
        clearSelection()
    }

    fun restoreSelected(destination: RestoreDestination) {
        val selected = uiState.value.items.filter { it.id in selectedIds.value }
        if (selected.isNotEmpty()) restore(selected, destination)
    }

    fun delete(item: PrivateMediaItem) = viewModelScope.launch { vault.delete(listOf(item.id)) }

    fun deleteSelected() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isNotEmpty()) vault.delete(ids)
        clearSelection()
    }

    fun createFolder(name: String) = viewModelScope.launch {
        runCatching { vault.createFolder(name) }
            .onSuccess { folderError.value = null }
            .onFailure { folderError.value = it.message ?: "Unable to create folder" }
    }

    fun clearFolderError() {
        folderError.value = null
    }

    fun moveSelected(folderId: String?) = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isNotEmpty()) vault.moveToFolder(ids, folderId)
        clearSelection()
    }

    fun deleteFolder(folderId: String) = viewModelScope.launch {
        runCatching { vault.deleteFolder(folderId) }
            .onFailure { folderError.value = it.message ?: "Unable to delete folder" }
    }

    fun clearPreviews() = vault.clearPreviews()
}

data class PrivateVaultUiState(
    val items: List<PrivateMediaItem> = emptyList(),
    val folders: List<PrivateFolder> = emptyList(),
    /** False until the Private folder's own PIN has been chosen — see [PrivateVaultScreen]. */
    val hasPin: Boolean = false,
    /** Whether a fingerprint may stand in for that PIN. */
    val biometricsEnabled: Boolean = false,
    /** Mirrors [com.pandagallery.app.domain.model.UserPreferences.allowPrivateAlbumScreenshots]. */
    val allowScreenshots: Boolean = false,
    /** Mirrors [com.pandagallery.app.domain.model.UserPreferences.autoPlayVideos]. */
    val autoPlayVideos: Boolean = true,
    val preferencesLoaded: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val folderError: String? = null,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
