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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
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
                else vm.errorCerca(ERROR_SIN_UBICACION)
            }
            .addOnFailureListener { vm.errorCerca(ERROR_SIN_UBICACION) }
    }

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos.values.any { it }) obtenerUbicacion()
        else vm.errorCerca(ERROR_SIN_PERMISO)
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

// Pantalla con un encabezado fijo arriba y una lista con scroll.
// El encabezado tiene: [inicio] [ícono] título [acción], y debajo un subtítulo y una línea extra.
// Las acciones van arriba para que no haya que bajar hasta el final de una lista larga.
@Composable
private fun Pantalla(
    titulo: String,
    icono: ImageVector? = null,
    inicio: (@Composable () -> Unit)? = null,
    accion: (@Composable () -> Unit)? = null,
    subtitulo: String? = null,
    subtituloLineas: Int = 1,
    extra: (@Composable () -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { padding ->
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
                            if (inicio != null) {
                                inicio()
                                Spacer(Modifier.width(2.dp))
                            }
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
                                maxLines = subtituloLineas,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (extra != null) extra()
                    }
                }
            }
            content(spec)
        }
    }
}

// Ancho completo, altura que se adapta al borde redondo y movimiento animado al reordenarse
private fun TransformingLazyColumnItemScope.tarjetaModifier(spec: TransformationSpec): Modifier =
    Modifier.fillMaxWidth().transformedHeight(this, spec).animateItem()

@Composable
private fun MensajeEstado(texto: String) {
    Text(
        text = texto,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

// Pantalla vacía o con un problema: ícono, qué pasó y qué hacer
@Composable
private fun EstadoVista(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    esError: Boolean = false,
    guino: String? = null
) {
    val acento = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icono, contentDescription = null, tint = acento, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text = titulo, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(
            text = detalle,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (guino != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = guino, fontSize = 10.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = acento)
        }
    }
}

// Frases chilenas: se elige una al mostrarse la pantalla y se mantiene mientras siga visible
private val FRASES_CARGANDO = listOf("Ya po, despertando al servidor…", "Más lento que micro en hora punta…", "Cachando dónde anda tu micro…")
private val FRASES_SIN_BUSES = listOf("Ni una micro a la vista, compadre.", "Pa' variar, no viene ninguna.")

// Mientras carga; si tarda, explica que el servidor gratuito puede estar despertando
@Composable
private fun CargandoVista(lento: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        if (lento) {
            Spacer(Modifier.height(8.dp))
            val frase = remember { FRASES_CARGANDO.random() }
            Text(text = frase, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text(
                text = "La primera vez puede tardar hasta un minuto.",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BotonReintentar(onClick: () -> Unit, modifier: Modifier, transformation: SurfaceTransformation) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = modifier,
        transformation = transformation
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Reintentar", fontSize = 12.sp)
    }
}

// ---------- Páginas de inicio ----------

@Composable
private fun CercaPage(vm: BusViewModel, onReintentar: () -> Unit) {
    Pantalla(
        titulo = "Cerca de mí",
        icono = Icons.Filled.LocationOn,
        subtitulo = if (vm.cercaLoading) "Buscando…" else "A 300 m a la redonda",
        accion = {
            BotonActualizar(
                cargando = vm.cercaLoading,
                descripcion = "Actualizar ubicación",
                onClick = onReintentar
            )
        }
    ) { spec ->
        if (vm.cercaLoading && vm.cercanos.isEmpty()) {
            item { CargandoVista(lento = vm.servidorLento) }
        }

        vm.cercaError?.let { problema ->
            item { EstadoVista(Icons.Filled.Warning, problema.titulo, problema.detalle, esError = true) }
            item {
                BotonReintentar(onClick = onReintentar, modifier = tarjetaModifier(spec), transformation = SurfaceTransformation(spec))
            }
        }

        if (vm.cercaVacia && !vm.cercaLoading && vm.cercaError == null) {
            item {
                if (vm.fueraDeSantiago) {
                    EstadoVista(
                        Icons.Filled.LocationOn,
                        "Fuera de Santiago",
                        "MicroWatch solo muestra los buses de Santiago por ahora."
                    )
                } else {
                    EstadoVista(
                        Icons.Filled.LocationOn,
                        "Sin paraderos cerca",
                        "No hay paraderos a 300 m de ti. Prueba en otra calle."
                    )
                }
            }
        }

        items(vm.cercanos, key = { it.codigo }) { p ->
            ParaderoCard(
                paradero = p,
                onClick = { vm.buscar(p.codigo, p.nombre) },
                modifier = tarjetaModifier(spec),
                transformation = SurfaceTransformation(spec)
            )
        }
    }
}

@Composable
private fun FavoritosPage(vm: BusViewModel) {
    val haptic = LocalHapticFeedback.current
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
            item {
                EstadoVista(
                    Icons.Filled.Star,
                    "Tu lista está vacía",
                    "Guarda el paradero de tu casa o del trabajo con la estrella y lo tienes a un toque.",
                    guino = "Pa' la pega o pa' la casa."
                )
            }
        }
        items(vm.favorites, key = { it }) { fav ->
            val nombre = vm.nombreDe(fav)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = tarjetaModifier(spec)) {
                Card(
                    onClick = { vm.buscar(fav, nombre) },
                    // Card trae una altura mínima propia; una altura fija la reduce de verdad
                    modifier = Modifier.weight(1f).height(if (nombre != null) 50.dp else 38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Text(text = fav, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (nombre != null) {
                            Text(
                                text = nombre,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        vm.toggleFavorite(fav)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
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
        item {
            Text(
                text = "MicroWatch es un proyecto personal y no oficial. Solo cubre Santiago.",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

// Ícono de actualizar junto al título; gira mientras se busca y vibra suave al tocarlo
@Composable
private fun BotonActualizar(cargando: Boolean, descripcion: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        enabled = !cargando,
        modifier = Modifier.size(28.dp)
    ) {
        if (cargando) {
            IconoGirando(descripcion)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = descripcion, modifier = Modifier.size(16.dp))
        }
    }
}

// La animación solo existe mientras gira, así no gasta batería en reposo
@Composable
private fun IconoGirando(descripcion: String) {
    val giro by rememberInfiniteTransition(label = "giro").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angulo"
    )
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = descripcion,
        modifier = Modifier.size(16.dp).rotate(giro)
    )
}

// Estrella del encabezado: rellena si el paradero es favorito, vacía si no
@Composable
private fun BotonFavorito(esFavorito: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = if (esFavorito) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = if (esFavorito) "Quitar de favoritos" else "Guardar en favoritos",
            tint = if (esFavorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// "Actualizado hace 12 s", que se va actualizando solo mientras la app está a la vista
@Composable
private fun TextoActualizado(desde: Long, actualizando: Boolean) {
    val activity = LocalContext.current as ComponentActivity
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(desde) {
        while (true) {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                ahora = System.currentTimeMillis()
            }
            delay(1_000)
        }
    }

    val segundos = ((ahora - desde) / 1000).coerceAtLeast(0)
    val texto = when {
        actualizando -> "Actualizando…"
        desde == 0L -> ""
        segundos < 5 -> "Actualizado ahora"
        segundos < 60 -> "Actualizado hace $segundos s"
        else -> "Actualizado hace ${segundos / 60} min"
    }
    if (texto.isNotEmpty()) {
        Text(text = texto, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Detalle de un paradero ----------

@Composable
private fun BusesScreen(vm: BusViewModel) {
    val activity = LocalContext.current as ComponentActivity
    val haptic = LocalHapticFeedback.current

    // Actualiza los tiempos cada 30 s, solo mientras la app está a la vista
    LaunchedEffect(vm.paradero) {
        while (true) {
            delay(30_000)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.refresh()
        }
    }

    val esFav = vm.isFavorite(vm.paradero)

    // La estrella y el actualizar van en el encabezado: así siempre se ven, sin importar cuántos buses haya
    Pantalla(
        titulo = vm.paradero,
        inicio = {
            BotonFavorito(esFavorito = esFav) {
                haptic.performHapticFeedback(if (esFav) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
                vm.toggleFavorite(vm.paradero)
            }
        },
        accion = {
            BotonActualizar(
                cargando = vm.isLoading || vm.actualizando,
                descripcion = "Actualizar tiempos",
                onClick = { vm.refresh() }
            )
        },
        subtitulo = vm.nombreParadero,
        subtituloLineas = 2,
        extra = { TextoActualizado(desde = vm.actualizadoEn, actualizando = vm.actualizando) }
    ) { spec ->
        if (vm.isLoading) {
            item { CargandoVista(lento = vm.servidorLento) }
        }

        vm.error?.let { problema ->
            item { EstadoVista(Icons.Filled.Warning, problema.titulo, problema.detalle, esError = true) }
            item {
                BotonReintentar(
                    onClick = { vm.buscar(vm.paradero, vm.nombreParadero) },
                    modifier = tarjetaModifier(spec),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }

        if (!vm.isLoading && vm.error == null && vm.actualizadoEn > 0 && vm.buses.isEmpty()) {
            item {
                EstadoVista(
                    Icons.Filled.Info,
                    "Sin buses por ahora",
                    "No hay micros en camino a este paradero en este momento.",
                    guino = remember { FRASES_SIN_BUSES.random() }
                )
            }
        }

        // La clave mantiene la identidad de cada tarjeta, así se desliza en vez de saltar al reordenarse
        items(vm.buses, key = { it.clave() }) { bus ->
            BusCard(bus = bus, modifier = tarjetaModifier(spec), transformation = SurfaceTransformation(spec))
        }

        // Los servicios sin buses van aparte y en pequeño, para no llenar la lista de avisos rojos
        if (vm.sinBuses.isNotEmpty()) {
            item {
                Text(
                    text = "Sin buses ahora",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = tarjetaModifier(spec).padding(top = 6.dp)
                )
            }
            items(vm.sinBuses, key = { "sin-" + it.clave() }) { bus ->
                Text(
                    text = "${bus.servicio} · ${bus.destino}",
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = tarjetaModifier(spec).padding(horizontal = 20.dp)
                )
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

// Huevos de pascua: al tocar la tarjeta de estos servicios aparece la frase unos segundos
private val HUEVOS_DE_PASCUA = mapOf(
    "210" to "¡Brijidooo! 🚌"
)

// Con 7 toques seguidos en cualquier tarjeta sale una de estas; los toques deben ir a menos de 2 s uno de otro
private const val TOQUES_SECRETOS = 7
private const val PAUSA_MAX_ENTRE_TOQUES_MS = 2_000L
private val FRASES_FAMOSAS = listOf(
    "¡Chi, chi, chi, le, le, le!",
    "¡Echando a perder se aprende!",
    "Estamos en las pailas.",
    "Más chileno que el completo.",
    "Al tiro, cachai.",
    "Ni ahí, po.",
    "¡Qué bacán, po!",
    "Chao, pescao'.",
    "A pata pelá, si es necesario.",
    "Pásalo chancho, no más.",
    "Yo estoy bien, mi familia está bien.",
    "Si funciona, no lo toques."
)

// Es una Card para que se adapte al borde redondo igual que el resto; tocarla solo hace algo en los huevos de pascua
@Composable
private fun BusCard(bus: Bus, modifier: Modifier, transformation: SurfaceTransformation) {
    val (fondo, texto) = colorServicio(bus.color)
    val t1 = formatTiempo(bus.horaprediccionbus1)
    val t2 = formatTiempo(bus.horaprediccionbus2)

    // Frase fija del servicio (p. ej. la 210) o, tras 7 toques seguidos, una famosa al azar
    val fraseDelServicio = HUEVOS_DE_PASCUA[bus.servicio?.trim()?.lowercase()]
    var frase by remember { mutableStateOf<String?>(null) }
    var toques by remember { mutableIntStateOf(0) }
    var ultimoToque by remember { mutableLongStateOf(0L) }
    val mostrarFrase = frase != null
    LaunchedEffect(frase) {
        if (frase != null) {
            delay(3_500)
            frase = null
        }
    }

    Card(
        onClick = {
            val ahora = System.currentTimeMillis()
            toques = if (ahora - ultimoToque <= PAUSA_MAX_ENTRE_TOQUES_MS) toques + 1 else 1
            ultimoToque = ahora
            if (toques >= TOQUES_SECRETOS) {
                frase = FRASES_FAMOSAS.random()
                toques = 0
            } else if (fraseDelServicio != null) {
                frase = fraseDelServicio
            }
        },
        modifier = modifier,
        transformation = transformation
    ) {
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
                    text = frase ?: bus.destino ?: "",
                    fontSize = 10.sp,
                    fontWeight = if (mostrarFrase) FontWeight.Bold else null,
                    maxLines = if (mostrarFrase) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (mostrarFrase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = t1, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (t2.isNotEmpty()) {
                    Text(
                        text = "Luego $t2",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
