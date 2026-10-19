package com.bastian.redbuswatch.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.app.RemoteInput
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.bastian.redbuswatch.data.Bus
import com.bastian.redbuswatch.data.ParaderoCercano
import com.bastian.redbuswatch.presentation.theme.BlueBus
import com.bastian.redbuswatch.presentation.theme.RedBusWatchTheme

private const val KEY_PARADERO = "paradero"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RedBusWatchTheme {
                BusApp()
            }
        }
    }
}

@Composable
fun BusApp(vm: BusViewModel = viewModel()) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@rememberLauncherForActivityResult
            val results = RemoteInput.getResultsFromIntent(intent)
            val codigo = results?.getCharSequence(KEY_PARADERO)?.toString()
            if (!codigo.isNullOrBlank()) vm.buscar(codigo)
        }
    }

    fun lanzarInput() {
        val remoteInput = RemoteInput.Builder(KEY_PARADERO)
            .setLabel("Paradero (ej: PA433)")
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, arrayListOf(remoteInput))
        launcher.launch(intent)
    }

    val context = LocalContext.current

    @SuppressLint("MissingPermission")
    fun obtenerUbicacion() {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) vm.cargarCercanos(loc.latitude, loc.longitude)
                else vm.errorCerca("No se pudo obtener la ubicación")
            }
            .addOnFailureListener { vm.errorCerca("No se pudo obtener la ubicación") }
    }

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos.values.any { it }) obtenerUbicacion()
        else vm.errorCerca("Permiso de ubicación denegado")
    }

    fun abrirCerca() {
        vm.abrirCerca()
        val concedido =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (concedido) obtenerUbicacion()
        else permisoLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val listState = rememberScalingLazyListState()

    BackHandler(enabled = vm.vistaCerca) { vm.cerrarCerca() }

    if (vm.vistaCerca) {
        CercaScreen(vm = vm, onReintentar = { abrirCerca() })
        return
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp)
            ) {
                // Header
                item {
                    Text(
                        text = if (vm.paradero.isEmpty()) "🚌 Red Santiago" else "🚌 ${vm.paradero}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }

                // Loading
                if (vm.isLoading) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                }

                // Error
                vm.error?.let { err ->
                    item {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                }

                // Lista de buses
                items(vm.buses) { bus ->
                    BusItem(bus = bus)
                }

                // Botón favorito (solo si hay paradero activo)
                if (vm.paradero.isNotEmpty() && !vm.isLoading) {
                    item {
                        val esFav = vm.isFavorite(vm.paradero)
                        OutlinedButton(
                            onClick = { vm.toggleFavorite(vm.paradero) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (esFav) "★ Quitar favorito" else "☆ Guardar favorito",
                                fontSize = 11.sp
                            )
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { vm.refresh() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("↺ Actualizar", fontSize = 11.sp)
                        }
                    }
                }

                // Favoritos guardados
                if (vm.favorites.isNotEmpty()) {
                    item {
                        Text(
                            text = "Favoritos",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(vm.favorites) { fav ->
                        Button(
                            onClick = { vm.buscar(fav) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("★ $fav", fontSize = 12.sp)
                        }
                    }
                }

                // Botón paraderos cercanos
                item {
                    Button(
                        onClick = { abrirCerca() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("📍 Cerca de mí", fontSize = 12.sp)
                    }
                }

                // Botón buscar paradero
                item {
                    Button(
                        onClick = { lanzarInput() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (vm.paradero.isEmpty()) "Buscar paradero" else "Cambiar paradero",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CercaScreen(vm: BusViewModel, onReintentar: () -> Unit) {
    val listState = rememberScalingLazyListState()

    AppScaffold {
        ScreenScaffold(scrollState = listState) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp)
            ) {
                item {
                    Text(
                        text = "📍 Cerca de mí",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }

                if (vm.cercaLoading) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
                }

                vm.cercaError?.let { err ->
                    item {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                    item {
                        OutlinedButton(onClick = onReintentar, modifier = Modifier.fillMaxWidth()) {
                            Text("↺ Reintentar", fontSize = 11.sp)
                        }
                    }
                }

                items(vm.cercanos) { p ->
                    ParaderoItem(paradero = p, onClick = { vm.elegirParadero(p.codigo) })
                }
            }
        }
    }
}

@Composable
fun ParaderoItem(paradero: ParaderoCercano, onClick: () -> Unit) {
    val maxChips = 4
    val chips = paradero.recorridos.take(maxChips).joinToString(" · ")
    val resto = paradero.recorridos.size - maxChips

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(color = Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = paradero.codigo,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BlueBus
            )
            Spacer(Modifier.weight(1f))
            Text(text = "${paradero.distancia} m", fontSize = 11.sp, color = Color.White)
        }
        Text(
            text = paradero.nombre,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFFCCCCCC)
        )
        if (chips.isNotEmpty()) {
            Text(
                text = if (resto > 0) "$chips +$resto" else chips,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF999999)
            )
        }
    }
}

@Composable
fun BusItem(bus: Bus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(color = Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bus.servicio ?: "-",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BlueBus
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = bus.destino ?: "",
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFCCCCCC)
            )
        }

        // El código 00 trae dos buses y el 01 uno solo; ambos tienen predicción
        if (!bus.horaprediccionbus1.isNullOrEmpty()) {
            Text(text = "→ ${bus.horaprediccionbus1}", fontSize = 11.sp, color = Color.White)
            if (!bus.horaprediccionbus2.isNullOrEmpty()) {
                Text(text = "→ ${bus.horaprediccionbus2}", fontSize = 10.sp, color = Color(0xFF999999))
            }
        } else {
            Text(
                text = "⚠ ${bus.respuestaServicio ?: "Sin servicio"}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
