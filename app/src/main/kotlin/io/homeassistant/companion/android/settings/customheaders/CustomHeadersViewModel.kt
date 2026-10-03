package io.homeassistant.companion.android.settings.customheaders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.customheaders.CustomHeader
import io.homeassistant.companion.android.common.data.customheaders.CustomHeader.Validation
import io.homeassistant.companion.android.common.data.customheaders.CustomHeadersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * One editable header line. [id] is only used to identify the row in the list, it is never persisted.
 * The value is masked by the UI and never displayed in clear text.
 */
internal data class HeaderRow(
    val id: Int,
    val name: String = "",
    val value: String = "",
    val validation: Validation = Validation.Valid,
) {
    // Avoid leaking the value in logs
    override fun toString(): String = "HeaderRow(id=$id, name=$name, value=HIDDEN)"
}

internal data class CustomHeadersUiState(
    val isLoading: Boolean = true,
    val rows: List<HeaderRow> = emptyList(),
    val hasDuplicatedNames: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && !hasDuplicatedNames && rows.all { it.validation == Validation.Valid }
}

/** One-shot events of the custom headers screen. */
internal sealed interface CustomHeadersEvent {
    data object Saved : CustomHeadersEvent
    data object SaveFailed : CustomHeadersEvent
}

@HiltViewModel(assistedFactory = CustomHeadersViewModelFactory::class)
internal class CustomHeadersViewModel @AssistedInject constructor(
    @Assisted private val serverId: Int,
    private val repository: CustomHeadersRepository,
) : ViewModel() {

    private var nextId = 0

    private val _uiState = MutableStateFlow(CustomHeadersUiState())
    val uiState: StateFlow<CustomHeadersUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CustomHeadersEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CustomHeadersEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val rows = repository.getHeaders(serverId).map { HeaderRow(nextId++, it.name, it.value) }
            _uiState.update { it.copy(isLoading = false, rows = rows) }
        }
    }

    fun onAddHeader() = updateRows { it + HeaderRow(nextId++) }

    fun onRemoveHeader(id: Int) = updateRows { rows -> rows.filterNot { it.id == id } }

    fun onNameChanged(id: Int, name: String) = updateRows { rows ->
        rows.map {
            if (it.id ==
                id
            ) {
                it.copy(name = name)
            } else {
                it
            }
        }
    }

    fun onValueChanged(id: Int, value: String) =
        updateRows { rows -> rows.map { if (it.id == id) it.copy(value = value) else it } }

    private fun updateRows(transform: (List<HeaderRow>) -> List<HeaderRow>) {
        _uiState.update { state ->
            val rows = transform(state.rows).map { it.copy(validation = it.toHeader().validateOrPending()) }
            state.copy(
                rows = rows,
                hasDuplicatedNames = rows.map { it.name.lowercase() }.let { names -> names.toSet().size != names.size },
            )
        }
    }

    /** Saves the headers, rows left completely empty are ignored. */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            try {
                repository.setHeaders(serverId, state.rows.filterNot { it.isBlank() }.map { it.toHeader() })
                _events.emit(CustomHeadersEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to save the custom headers of server $serverId")
                _events.emit(CustomHeadersEvent.SaveFailed)
            }
        }
    }

    private fun HeaderRow.isBlank() = name.isBlank() && value.isBlank()

    private fun HeaderRow.toHeader() = CustomHeader(name = name.trim(), value = value)

    // A row the user did not touch yet (both fields empty) must not display errors
    private fun CustomHeader.validateOrPending(): Validation =
        if (name.isEmpty() && value.isEmpty()) Validation.Valid else validate()
}

@AssistedFactory
internal interface CustomHeadersViewModelFactory {
    fun create(serverId: Int): CustomHeadersViewModel
}
