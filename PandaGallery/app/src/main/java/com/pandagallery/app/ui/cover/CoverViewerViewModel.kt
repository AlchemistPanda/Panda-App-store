package com.pandagallery.app.ui.cover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.dao.CoverDisplayDao
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.entity.CoverDisplayEntity
import com.pandagallery.app.data.local.entity.MediaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoverViewerViewModel @Inject constructor(
    private val coverDisplayDao: CoverDisplayDao,
    private val mediaDao: MediaDao,
) : ViewModel() {

    val coverState: StateFlow<CoverDisplayEntity?> = coverDisplayDao.observeLatestCoverState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val mediaList: StateFlow<List<MediaEntity>> = mediaDao.getAllMedia()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFavorite(media: MediaEntity) {
        viewModelScope.launch {
            mediaDao.update(media.copy(isFavorite = !media.isFavorite))
        }
    }

    fun setCoverActive(isActive: Boolean) {
        viewModelScope.launch {
            val current = coverDisplayDao.getLatestCoverState()
            if (current != null) {
                coverDisplayDao.updateCoverActive(current.id, isActive)
            } else {
                coverDisplayDao.insertOrUpdate(CoverDisplayEntity(isCoverActive = isActive))
            }
        }
    }
}
