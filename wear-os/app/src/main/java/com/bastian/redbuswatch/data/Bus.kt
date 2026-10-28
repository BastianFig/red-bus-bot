package com.bastian.redbuswatch.data

data class BusResponse(
    val paradero: String,
    val buses: List<Bus>
)

data class NearbyResponse(
    val paraderos: List<ParaderoCercano>
)

data class ParaderoCercano(
    val codigo: String,
    val nombre: String,
    val distancia: Int,
    val recorridos: List<String> = emptyList()
)

data class Bus(
    val servicio: String? = "",
    val destino: String? = "",
    val horaprediccionbus1: String? = "",
    val horaprediccionbus2: String? = "",
    val codigorespuesta: String? = "",
    val respuestaServicio: String? = "",
    val color: String? = ""
)

// Identifica un servicio en su sentido; sirve de clave para animar la lista sin repetidos
fun Bus.clave() = "$servicio|$destino"

// Minutos estimados hasta que llega el primer bus. "Entre 22 Y 26 min" cuenta como 22.
// Un texto sin número ("Llegando", "Menos de 1 min") significa que ya está por llegar: 0.
// Sin ninguna predicción va al final de la lista.
fun Bus.minutosHastaLlegada(): Int {
    val texto = horaprediccionbus1.orEmpty()
    if (texto.isBlank()) return Int.MAX_VALUE
    return Regex("""\d+""").find(texto)?.value?.toIntOrNull() ?: 0
}
