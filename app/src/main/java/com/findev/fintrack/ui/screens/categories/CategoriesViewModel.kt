package com.findev.fintrack.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.CategoryInUseException
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val expense: List<CategoryEntity> = emptyList(),
    val income: List<CategoryEntity> = emptyList(),
    val isLoaded: Boolean = false,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val deleteBlockedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits when a delete was refused because the category is still used. */
    val deleteBlocked: Flow<Unit> = deleteBlockedChannel.receiveAsFlow()

    val uiState: StateFlow<CategoriesUiState> = categoryRepository.observeAll()
        .map { categories ->
            CategoriesUiState(
                expense = categories.filter { it.type == CategoryType.EXPENSE },
                income = categories.filter { it.type == CategoryType.INCOME },
                isLoaded = true,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    fun onCreate(name: String, type: CategoryType, icon: String, color: Long) {
        viewModelScope.launch { categoryRepository.create(name, type, icon, color) }
    }

    fun onSaveEdit(id: String, name: String, icon: String, color: Long) {
        viewModelScope.launch { categoryRepository.update(id, name, icon, color) }
    }

    fun onArchiveToggle(id: String, archived: Boolean) {
        viewModelScope.launch { categoryRepository.setArchived(id, archived) }
    }

    fun onDelete(id: String) {
        viewModelScope.launch {
            try {
                categoryRepository.delete(id)
            } catch (_: CategoryInUseException) {
                deleteBlockedChannel.send(Unit)
            }
        }
    }
}
