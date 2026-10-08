package com.pandagallery.app.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.cleanup.CleanupRepository
import com.pandagallery.app.domain.model.DuplicateKeepRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanupViewModel @Inject constructor(private val repository: CleanupRepository) : ViewModel() {
    val state = repository.state
    fun scan() = viewModelScope.launch { repository.scan() }

    fun autoResolveExactDuplicates(
        rule: DuplicateKeepRule = DuplicateKeepRule.LARGEST,
        retentionDays: Int = 30,
        onComplete: (Int) -> Unit = {},
    ) = viewModelScope.launch {
        val count = repository.autoResolveExactDuplicates(rule, retentionDays)
        repository.scan()
        onComplete(count)
    }
}
