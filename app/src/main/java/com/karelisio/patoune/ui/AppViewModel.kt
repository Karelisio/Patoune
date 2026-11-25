package com.karelisio.patoune.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.container
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.AppSettings
import com.karelisio.patoune.update.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Crée un ViewModel en lui passant le conteneur de l'appli. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

class MainViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val animals: StateFlow<List<Animal>> = c.db.animals().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Animal sélectionné (le premier par défaut). */
    val selected: StateFlow<Animal?> = combine(settings, animals) { s, list ->
        list.firstOrNull { it.id == s?.selectedAnimalId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val release = MutableStateFlow<Release?>(null)

    fun select(id: String) = viewModelScope.launch { c.settings.selectAnimal(id) }

    fun checkUpdate(force: Boolean = false) = viewModelScope.launch {
        c.updater.check(force)?.let { release.value = it }
    }
}
