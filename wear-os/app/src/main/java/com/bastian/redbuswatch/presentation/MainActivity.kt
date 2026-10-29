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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.bastian.redbuswatch.data.Bus
import com.bastian.redbuswatch.data.ParaderoCercano
import com.bastian.redbuswatch.data.clave
import com.bastian.redbuswatch.presentation.theme.BlueBus
import com.bastian.redbuswatch.presentation.theme.RedBusWatchTheme
import kotlinx.coroutines.delay

private const val KEY_PARADERO = "paradero"
private const val MAX_RECORRIDOS = 3

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

    val pagerState = rememberPagerState(pageCount = { 3 })

    // Cada vez que la app vuelve a la vista, y si la lista ya es vieja, busca con la ubicación actual
    val activity = context as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME && vm.paradero.isEmpty() && vm.cercaDesactualizada()) {
                abrirCerca()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = vm.paradero.isNotEmpty()) { vm.cerrarBuses() }

    AppScaffold {
        if (vm.paradero.isNotEmpty()) {
            BusesScreen(vm)
        } else {
            HorizontalPagerScaffold(pagerState = pagerState) {
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> CercaPage(vm, onReintentar = { abrirCerca() })
                        1 -> FavoritosPage(vm)
                        else -> BuscarPage(onBuscar = { lanzarInput() })
                    }
                }
            }
        }
    }
}

// ---------- Estructura común ----------

// Pantalla con título arriba y lista con scroll; el ícono, el subtítulo, la acción y el botón inferior son opcionales
@Composable
private fun Pantalla(
    titulo: String,
    icono: ImageVector? = null,
    subtitulo: String? = null,
    accion: (@Composable () -> Unit)? = null,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    val lista: @Composable (PaddingValues) -> Unit = { padding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = padding
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (icono != null) {
                                Icon(
                                    imageVector = icono,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(titulo)
                            if (accion != null) {
                                Spacer(Modifier.width(4.dp))
                                accion()
                            }
                        }
                        if (subtitulo != null) {
                            Text(
                                text = subtitulo,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            content(spec)
        }
    }

    // ScreenScaffold tiene una versión con botón inferior obligatorio y otra sin él
    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = edgeButton) { padding -> lista(padding) }
    } else {
        ScreenScaffold(scrollState = listState) { padding -> lista(padding) }
    }
}

// Ancho completo, altura que se adapta al borde redondo y movimiento animado al reordenarse
private fun TransformingLazyColumnItemScope.tarjetaModifier(spec: TransformationSpec): Modifier =
    Modifier.fillMaxWidth().transformedHeight(this, spec).animateItem()

@Composable
private fun MensajeEstado(texto: String, esError: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (esError) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = texto,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Páginas de inicio ----------

@Composable
private fun CercaPage(vm: BusViewModel, onReintentar: () -> Unit) {
    Pantalla(
        titulo = "Cerca de mí",
        icono = Icons.Filled.LocationOn,
        subtitulo = if (vm.cercaLoading) "Buscando…" else "A 300 m a la redonda",
        accion = { BotonActualizar(cargando = vm.cercaLoading, onClick = onReintentar) }
    ) { spec ->
        if (vm.cercaLoading && vm.cercanos.isEmpty()) {
            item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
        }

        vm.cercaError?.let { err ->
            item { MensajeEstado(texto = err, esError = true) }
            item {
                Button(
                    onClick = onReintentar,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = tarjetaModifier(spec),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reintentar", fontSize = 12.sp)
                }
            }
        }

        items(vm.cercanos, key = { it.codigo }) { p ->
            ParaderoCard(
                paradero = p,
                onClick = { vm.buscar(p.codigo) },
                modifier = tarjetaModifier(spec),
                transformation = SurfaceTransformation(spec)
            )
        }
    }
}

@Composable
private fun FavoritosPage(vm: BusViewModel) {
    val cantidad = vm.favorites.size
    Pantalla(
        titulo = "Favoritos",
        icono = Icons.Filled.Star,
        subtitulo = when (cantidad) {
            0 -> null
            1 -> "1 paradero"
            else -> "$cantidad paraderos"
        }
    ) { spec ->
        if (vm.favorites.isEmpty()) {
            item { MensajeEstado("Aún no tienes favoritos. Abre un paradero y toca la estrella.") }
        }
        items(vm.favorites, key = { it }) { fav ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = tarjetaModifier(spec)) {
                Card(
                    onClick = { vm.buscar(fav) },
                    // Card trae una altura mínima propia; una altura fija la reduce de verdad
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = fav, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(2.dp))
                IconButton(onClick = { vm.toggleFavorite(fav) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Quitar favorito",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BuscarPage(onBuscar: () -> Unit) {
    Pantalla(titulo = "Buscar", icono = Icons.Filled.Search, subtitulo = "Ingresa un código") { spec ->
        item {
            Button(
                onClick = onBuscar,
                modifier = tarjetaModifier(spec),
                transformation = SurfaceTransformation(spec)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Por código", fontSize = 13.sp)
            }
        }
        item { MensajeEstado("Escribe el código del paradero, por ejemplo PA433.") }
    }
}

// Ícono de actualizar junto al título; gira mientras se busca
@Composable
private fun BotonActualizar(cargando: Boolean, onClick: () -> Unit) {
    val giro by rememberInfiniteTransition(label = "giro").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angulo"
    )
    IconButton(onClick = onClick, enabled = !cargando, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Actualizar ubicación",
            modifier = Modifier.size(16.dp).rotate(if (cargando) giro else 0f)
        )
    }
}

// ---------- Detalle de un paradero ----------

@Composable
private fun BusesScreen(vm: BusViewModel) {
    val activity = LocalContext.current as ComponentActivity

    // Actualiza los tiempos cada 30 s, solo mientras la app está a la vista
    LaunchedEffect(vm.paradero) {
        while (true) {
            delay(30_000)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.refresh()
        }
    }

    Pantalla(
        titulo = vm.paradero,
        edgeButton = {
            EdgeButton(
                onClick = { vm.refresh() },
                buttonSize = EdgeButtonSize.ExtraSmall,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", modifier = Modifier.size(20.dp))
            }
        }
    ) { spec ->
        if (vm.isLoading) {
            item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
        }

        if (vm.actualizando) {
            item {
                Text(
                    text = "Actualizando…",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        vm.error?.let { err ->
            item { MensajeEstado(texto = err, esError = true) }
        }

        // La clave mantiene la identidad de cada tarjeta, así se desliza en vez de saltar al reordenarse
        items(vm.buses, key = { it.clave() }) { bus ->
            BusCard(bus = bus, modifier = tarjetaModifier(spec), transformation = SurfaceTransformation(spec))
        }

        if (!vm.isLoading) {
            item {
                val esFav = vm.isFavorite(vm.paradero)
                Button(
                    onClick = { vm.toggleFavorite(vm.paradero) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = tarjetaModifier(spec),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (esFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = if (esFav) "Quitar favorito" else "Guardar favorito", fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- Tarjetas ----------

@Composable
private fun ParaderoCard(
    paradero: ParaderoCercano,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation
) {
    val resto = paradero.recorridos.size - MAX_RECORRIDOS

    Card(onClick = onClick, modifier = modifier, transformation = transformation) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(text = paradero.codigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${paradero.distancia} m",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = paradero.nombre,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (paradero.recorridos.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                paradero.recorridos.take(MAX_RECORRIDOS).forEach { RecorridoPill(it) }
                if (resto > 0) {
                    Text(
                        text = "+$resto",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecorridoPill(nombre: String) {
    Text(
        text = nombre,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(end = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

// Es una Card para que se adapte al borde redondo igual que el resto; tocarla no hace nada por ahora
@Composable
private fun BusCard(bus: Bus, modifier: Modifier, transformation: SurfaceTransformation) {
    val (fondo, texto) = colorServicio(bus.color)
    val t1 = formatTiempo(bus.horaprediccionbus1)
    val t2 = formatTiempo(bus.horaprediccionbus2)

    Card(onClick = {}, modifier = modifier, transformation = transformation) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bus.servicio ?: "-",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = texto,
                modifier = Modifier
                    .background(fondo, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bus.destino ?: "",
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (t1.isNotEmpty()) {
                    Text(text = t1, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (t2.isNotEmpty()) {
                        Text(
                            text = "Luego $t2",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = bus.respuestaServicio ?: "Sin servicio",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Color de fondo del servicio (lo manda red.cl) y color de texto con buen contraste
private fun colorServicio(hex: String?): Pair<Color, Color> {
    val fondo = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        return BlueBus to Color.White
    }
    return fondo to if (fondo.luminance() > 0.5f) Color.Black else Color.White
}

// "Entre 22 Y 26 min" -> "22–26 min"; "Menos de 1 min" -> "< 1 min"
private fun formatTiempo(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val rango = Regex("""(\d+)\D+(\d+)\s*min""").find(raw)
    if (rango != null) return "${rango.groupValues[1]}–${rango.groupValues[2]} min"
    if (raw.contains("menos", ignoreCase = true)) return "< 1 min"
    return raw
}
