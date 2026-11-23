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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

// Radio de búsqueda de "Cerca de mí", en metros
private const val RADIO_CERCA_M = 300

// Si pasó más tiempo que esto, al volver a la app se busca de nuevo con la ubicación actual
private const val CERCA_VIGENCIA_MS = 60_000L

// Si una consulta tarda más que esto, se avisa que el servidor gratuito puede estar despertando
private const val SERVIDOR_LENTO_MS = 6_000L

// Zona que cubren los paraderos (GTFS de Santiago) más un margen de unos 10 km
private const val SANTIAGO_LAT_MIN = -33.80
private const val SANTIAGO_LAT_MAX = -33.20
private const val SANTIAGO_LNG_MIN = -70.99
private const val SANTIAGO_LNG_MAX = -70.39

// Un problema explicado en dos líneas: qué pasó y qué hacer
data class ErrorVista(val titulo: String, val detalle: String)

val ERROR_SIN_PERMISO = ErrorVista(
    "Necesito tu ubicación",
    "Dale permiso de ubicación a la app para ver los paraderos cercanos."
)
val ERROR_SIN_UBICACION = ErrorVista(
    "No pude ubicarte",
    "Activa el GPS del reloj y vuelve a intentar."
)

// Traduce una excepción técnica a un mensaje que entienda cualquiera
private fun errorAmable(e: Exception): ErrorVista = when {
    e is SocketTimeoutException ->
        ErrorVista("Tardó demasiado", "El servidor está despertando. Intenta de nuevo en unos segundos.")
    e is IOException ->
        ErrorVista("Sin conexión", "Revisa el internet del reloj y vuelve a intentar.")
    e is HttpException && e.code() == 429 ->
        ErrorVista("Muchas consultas", "Espera un minuto y vuelve a intentar.")
    e is HttpException && (e.code() == 400 || e.code() == 404) ->
        ErrorVista("Paradero no encontrado", "Revisa el código, por ejemplo PA433.")
    e is HttpException ->
        ErrorVista("No pudimos consultar", "Los tiempos no están disponibles ahora. Intenta de nuevo.")
    else ->
        ErrorVista("Algo salió mal", "Intenta de nuevo en un momento.")
}

class BusViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("redbuswatch", Context.MODE_PRIVATE)

    var paradero by mutableStateOf("")
        private set

    var nombreParadero by mutableStateOf<String?>(null)
        private set

    // Servicios con al menos una predicción, ordenados por llegada
    var buses by mutableStateOf<List<Bus>>(emptyList())
        private set

    // Servicios del paradero que ahora no tienen buses en camino
    var sinBuses by mutableStateOf<List<Bus>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<ErrorVista?>(null)
        private set

    var actualizando by mutableStateOf(false)
        private set

    // Momento de la última respuesta correcta (0 = todavía ninguna)
    var actualizadoEn by mutableStateOf(0L)
        private set

    // La consulta lleva varios segundos: el servidor gratuito puede estar despertando
    var servidorLento by mutableStateOf(false)
        private set

    var favorites by mutableStateOf(loadFavorites())
        private set

    private fun loadFavorites(): List<String> =
        prefs.getStringSet("favorites", emptySet())?.sorted() ?: emptyList()

    private fun claveNombre(codigo: String) = "nombre_$codigo"

    // El nombre de un favorito se guarda aparte; los favoritos antiguos sin nombre siguen funcionando
    fun nombreDe(codigo: String): String? = prefs.getString(claveNombre(codigo), null)

    fun toggleFavorite(codigo: String) {
        val current = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        val editor = prefs.edit()
        if (current.remove(codigo)) {
            editor.remove(claveNombre(codigo))
        } else {
            current.add(codigo)
            if (codigo == paradero) nombreParadero?.let { editor.putString(claveNombre(codigo), it) }
        }
        editor.putStringSet("favorites", current).apply()
        favorites = current.sorted()
    }

    fun isFavorite(paradero: String) = favorites.contains(paradero)

    // El nombre es opcional: desde "Cerca de mí" ya se conoce, y si no, llega con la respuesta
    fun buscar(nuevoParedero: String, nombre: String? = null) {
        val codigo = nuevoParedero.trim().uppercase()
        if (codigo.isEmpty()) return

        paradero = codigo
        nombreParadero = nombre ?: nombreDe(codigo)
        isLoading = true
        error = null
        buses = emptyList()
        sinBuses = emptyList()
        actualizadoEn = 0L
        servidorLento = false
        cargarBuses(codigo, silencioso = false)
    }

    // Actualiza los tiempos sin vaciar la lista, para que no parpadee
    fun refresh() {
        if (paradero.isNotEmpty()) cargarBuses(paradero, silencioso = true)
    }

    // Pasados unos segundos sin respuesta, la pantalla explica por qué tarda
    private fun vigilarLentitud(): Job = viewModelScope.launch {
        delay(SERVIDOR_LENTO_MS)
        servidorLento = true
    }

    private fun cargarBuses(codigo: String, silencioso: Boolean) {
        if (silencioso) actualizando = true
        val vigia = if (silencioso) null else vigilarLentitud()

        viewModelScope.launch {
            try {
                val response = BusApi.service.getBuses(codigo)
                if (codigo == paradero) {
                    val todos = response.buses.distinctBy { it.clave() }
                    val (conPrediccion, sinPrediccion) = todos.partition { it.minutosHastaLlegada() != Int.MAX_VALUE }
                    buses = conPrediccion.sortedBy { it.minutosHastaLlegada() }
                    sinBuses = sinPrediccion
                    error = null
                    actualizadoEn = System.currentTimeMillis()
                    response.nombre?.let {
                        nombreParadero = it
                        if (isFavorite(codigo)) prefs.edit().putString(claveNombre(codigo), it).apply()
                    }
                }
            } catch (e: Exception) {
                // En una actualización silenciosa se conservan los tiempos anteriores
                if (codigo == paradero && (!silencioso || (buses.isEmpty() && sinBuses.isEmpty()))) {
                    error = errorAmable(e)
                }
            } finally {
                vigia?.cancel()
                if (codigo == paradero) {
                    isLoading = false
                    actualizando = false
                    servidorLento = false
                }
            }
        }
    }

    // --- Cerca de mí ---
    var cercanos by mutableStateOf<List<ParaderoCercano>>(emptyList())
        private set

    var cercaLoading by mutableStateOf(false)
        private set

    var cercaError by mutableStateOf<ErrorVista?>(null)
        private set

    // La búsqueda salió bien pero no hay paraderos a ese radio
    var cercaVacia by mutableStateOf(false)
        private set

    // La ubicación está fuera de la zona que cubren los datos
    var fueraDeSantiago by mutableStateOf(false)
        private set

    private var cercaActualizadaEn = 0L

    fun cercaDesactualizada() =
        !cercaLoading && System.currentTimeMillis() - cercaActualizadaEn > CERCA_VIGENCIA_MS

    // Mantiene la lista anterior mientras llega la nueva, para que no parpadee
    fun abrirCerca() {
        cercaLoading = true
        cercaError = null
    }

    fun errorCerca(problema: ErrorVista) {
        cercaError = problema
        cercanos = emptyList()
        cercaVacia = false
        cercaActualizadaEn = System.currentTimeMillis()
        cercaLoading = false
    }

    fun cargarCercanos(lat: Double, lng: Double) {
        val vigia = vigilarLentitud()

        viewModelScope.launch {
            try {
                cercanos = BusApi.service.getNearby(lat, lng, RADIO_CERCA_M).paraderos
                cercaError = null
                cercaVacia = cercanos.isEmpty()
                fueraDeSantiago = lat !in SANTIAGO_LAT_MIN..SANTIAGO_LAT_MAX ||
                    lng !in SANTIAGO_LNG_MIN..SANTIAGO_LNG_MAX
            } catch (e: Exception) {
                cercanos = emptyList()
                cercaVacia = false
                cercaError = errorAmable(e)
            } finally {
                vigia.cancel()
                servidorLento = false
                cercaActualizadaEn = System.currentTimeMillis()
                cercaLoading = false
            }
        }
    }

    fun cerrarBuses() {
        paradero = ""
        nombreParadero = null
        buses = emptyList()
        sinBuses = emptyList()
        error = null
        isLoading = false
        actualizando = false
        actualizadoEn = 0L
        servidorLento = false
    }
}
