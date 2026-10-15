package com.bastian.redbuswatch.data

data class BusResponse(
    val paradero: String,
    val buses: List<Bus>
)

data class Bus(
    val servicio: String? = "",
    val destino: String? = "",
    val horaprediccionbus1: String? = "",
    val horaprediccionbus2: String? = "",
    val codigorespuesta: String? = "",
    val color: String? = ""
)
