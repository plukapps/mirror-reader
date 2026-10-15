package com.pluk.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.onboarding.firstName
import com.pluk.reader.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** "Ya estás adentro" del alta corta, B3 (GST-021): el nombre y cuántos libros pasan a la cuenta (GST-012). */
@HiltViewModel
class ShortAllSetViewModel @Inject constructor(
    accounts: AccountRepository,
    library: LibraryRepository,
) : ViewModel() {

    data class UiState(val name: String? = null, val bookCount: Int = 0)

    val state: StateFlow<UiState> = combine(accounts.user, library.books) { user, books ->
        UiState(firstName(user?.displayName), books.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
