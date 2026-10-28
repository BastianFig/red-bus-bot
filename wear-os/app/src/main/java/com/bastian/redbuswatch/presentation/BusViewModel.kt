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
import com.bastian.redbuswatch.data.clave
import com.bastian.redbuswatch.data.minutosHastaLlegada
import kotlinx.coroutines.launch

// Radio de búsqueda de "Cerca de mí", en metros
private const val RADIO_CERCA_M = 300

// Si pasó más tiempo que esto, al volver a la app se busca de nuevo con la ubicación actual
private const val CERCA_VIGENCIA_MS = 60_000L

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

    var actualizando by mutableStateOf(false)
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
        cargarBuses(codigo, silencioso = false)
    }

    // Actualiza los tiempos sin vaciar la lista, para que no parpadee
    fun refresh() {
        if (paradero.isNotEmpty()) cargarBuses(paradero, silencioso = true)
    }

    private fun cargarBuses(codigo: String, silencioso: Boolean) {
        if (silencioso) actualizando = true

        viewModelScope.launch {
            try {
                val response = BusApi.service.getBuses(codigo)
                if (codigo == paradero) {
                    buses = response.buses
                        .distinctBy { it.clave() }
                        .sortedBy { it.minutosHastaLlegada() }
                    error = null
                }
            } catch (e: Exception) {
                // En una actualización silenciosa se conservan los tiempos anteriores
                if (codigo == paradero && (!silencioso || buses.isEmpty())) {
                    error = "Sin conexión o paradero inválido"
                }
            } finally {
                if (codigo == paradero) {
                    isLoading = false
                    actualizando = false
                }
            }
        }
    }

    // --- Cerca de mí ---
    var cercanos by mutableStateOf<List<ParaderoCercano>>(emptyList())
        private set

    var cercaLoading by mutableStateOf(false)
        private set

    var cercaError by mutableStateOf<String?>(null)
        private set

    private var cercaActualizadaEn = 0L

    fun cercaDesactualizada() =
        !cercaLoading && System.currentTimeMillis() - cercaActualizadaEn > CERCA_VIGENCIA_MS

    // Mantiene la lista anterior mientras llega la nueva, para que no parpadee
    fun abrirCerca() {
        cercaLoading = true
        cercaError = null
    }

    fun errorCerca(mensaje: String) {
        cercaError = mensaje
        cercanos = emptyList()
        cercaActualizadaEn = System.currentTimeMillis()
        cercaLoading = false
    }

    fun cargarCercanos(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                cercanos = BusApi.service.getNearby(lat, lng, RADIO_CERCA_M).paraderos
                if (cercanos.isEmpty()) cercaError = "No hay paraderos a $RADIO_CERCA_M m"
            } catch (e: Exception) {
                cercanos = emptyList()
                cercaError = "Sin conexión"
            } finally {
                cercaActualizadaEn = System.currentTimeMillis()
                cercaLoading = false
            }
        }
    }

    fun cerrarBuses() {
        paradero = ""
        buses = emptyList()
        error = null
        isLoading = false
        actualizando = false
    }
}
