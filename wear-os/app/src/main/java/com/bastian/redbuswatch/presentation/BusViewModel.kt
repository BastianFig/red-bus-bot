package com.bastian.redbuswatch.presentation

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bastian.redbuswatch.data.Bus
import com.bastian.redbuswatch.data.BusApi
import com.bastian.redbuswatch.data.ParaderoCercano
import kotlinx.coroutines.launch

class BusViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("redbuswatch", Context.MODE_PRIVATE)

    var paradero by mutableStateOf("")
        private set

    var buses by mutableStateOf<List<Bus>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var favorites by mutableStateOf(loadFavorites())
        private set

    private fun loadFavorites(): List<String> =
        prefs.getStringSet("favorites", emptySet())?.sorted() ?: emptyList()

    fun toggleFavorite(paradero: String) {
        val current = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.contains(paradero)) current.remove(paradero) else current.add(paradero)
        prefs.edit().putStringSet("favorites", current).apply()
        favorites = current.sorted()
    }

    fun isFavorite(paradero: String) = favorites.contains(paradero)

    fun buscar(nuevoParedero: String) {
        val codigo = nuevoParedero.trim().uppercase()
        if (codigo.isEmpty()) return

        paradero = codigo
        isLoading = true
        error = null
        buses = emptyList()

        viewModelScope.launch {
            try {
                val response = BusApi.service.getBuses(codigo)
                buses = response.buses
            } catch (e: Exception) {
                error = "Sin conexión o paradero inválido"
            } finally {
                isLoading = false
            }
        }
    }

    // --- Cerca de mí ---
    var vistaCerca by mutableStateOf(false)
        private set

    var cercanos by mutableStateOf<List<ParaderoCercano>>(emptyList())
        private set

    var cercaLoading by mutableStateOf(false)
        private set

    var cercaError by mutableStateOf<String?>(null)
        private set

    fun abrirCerca() {
        vistaCerca = true
        cercaLoading = true
        cercaError = null
        cercanos = emptyList()
    }

    fun cerrarCerca() {
        vistaCerca = false
    }

    fun errorCerca(mensaje: String) {
        cercaError = mensaje
        cercaLoading = false
    }

    fun cargarCercanos(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                cercanos = BusApi.service.getNearby(lat, lng).paraderos
                if (cercanos.isEmpty()) cercaError = "No hay paraderos a 500 m"
            } catch (e: Exception) {
                cercaError = "Sin conexión"
            } finally {
                cercaLoading = false
            }
        }
    }

    fun elegirParadero(codigo: String) {
        vistaCerca = false
        buscar(codigo)
    }

    fun refresh() {
        if (paradero.isNotEmpty()) buscar(paradero)
    }
}
