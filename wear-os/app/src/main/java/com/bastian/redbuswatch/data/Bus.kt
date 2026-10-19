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
