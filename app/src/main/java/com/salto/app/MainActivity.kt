package com.salto.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val resumeTick = mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SaltoTheme { Home() } }
    }
}

@Composable
fun SaltoTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF5FC2A5), secondary = Color(0xFFF0BD5A),
        background = Color(0xFF0D1512), surface = Color(0xFF17221E),
        onBackground = Color(0xFFE6EEE9), onSurface = Color(0xFFE6EEE9),
        onSurfaceVariant = Color(0xFF93A59D)
    ) else lightColorScheme(
        primary = Color(0xFF1F6F5C), secondary = Color(0xFFE8A93B),
        background = Color(0xFFEDF1EE), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF14201C), onSurface = Color(0xFF14201C),
        onSurfaceVariant = Color(0xFF66756E)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = remember { Store(ctx) }
    val apps by produceState(emptyList<AppItem>()) {
        value = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    val favs = remember { mutableStateListOf<String>().apply { addAll(store.favs()) } }
    val recents = remember { mutableStateListOf<String>().apply { addAll(store.recents()) } }
    var query by rememberSaveable { mutableStateOf("") }
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }

    val bubbleOn = bubbleRunning.value
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick.intValue) {
        if (pending && Settings.canDrawOverlays(ctx)) { pending = false; BubbleService.start(ctx) }
        else if (store.want() && !bubbleRunning.value && Settings.canDrawOverlays(ctx)) BubbleService.start(ctx)
    }
    fun toggleBubble(on: Boolean) {
        store.setWant(on)
        if (!on) BubbleService.stop(ctx)
        else if (Settings.canDrawOverlays(ctx)) BubbleService.start(ctx)
        else {
            pending = true
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
    }

    fun open(a: AppItem) {
        if (launch(ctx, a.pkg)) {
            recents.remove(a.pkg); recents.add(0, a.pkg)
            while (recents.size > 8) recents.removeAt(recents.lastIndex)
            store.saveRecents(recents)
        } else Toast.makeText(ctx, "No se puede abrir ${a.label}", Toast.LENGTH_SHORT).show()
    }
    fun pin(a: AppItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!favs.remove(a.pkg)) favs.add(a.pkg)
        store.saveFavs(favs)
    }

    val searching = query.isNotBlank()
    val results = if (searching) apps.filter { it.label.contains(query.trim(), ignoreCase = true) } else emptyList()
    val pinned = favs.mapNotNull { byPkg[it] }
    val recent = recents.mapNotNull { byPkg[it] }.filter { it.pkg !in favs }.take(4)
    var picking by remember { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }
    val onTap = remember { { a: AppItem -> if (picking) pin(a) else open(a) } }
    val onHold = remember { { a: AppItem -> pin(a) } }
    val tile: @Composable (AppItem) -> Unit = { a -> AppTile(a, a.pkg in favs, onTap, onHold) }

    if (screen == "order") {
        OrderScreen(pinned, { from, to ->
            val fi = favs.indexOf(from); val ti = favs.indexOf(to)
            if (fi >= 0 && ti >= 0) { favs.add(ti, favs.removeAt(fi)); store.saveFavs(favs) }
        }) { screen = "home" }
        return
    }
    if (screen == "style") { StyleScreen(store) { screen = "home" }; return }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(80.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(query, { query = it }) { results.firstOrNull()?.let { open(it) } }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { BubbleCard(bubbleOn) { toggleBubble(it) } }
        item(span = { GridItemSpan(maxLineSpan) }) { PickRow(picking, favs.size, { picking = !picking }, { screen = "order" }, { screen = "style" }) }
        if (searching) {
            if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Note("Ninguna app coincide con «${query.trim()}».")
            }
            items(results, key = { "r-" + it.pkg }) { tile(it) }
        } else {
            if (!picking) {
                section("Fijadas", pinned, "p-", "Pulsa «Elegir apps» para escoger las de la burbuja.", tile)
                section("Recientes", recent, "c-", null, tile)
            }
            section("Todas", apps, "a-", null, tile)
        }
    }
}

private fun LazyGridScope.section(
    title: String, list: List<AppItem>, prefix: String, empty: String?,
    tile: @Composable (AppItem) -> Unit
) {
    if (list.isEmpty() && empty == null) return
    item(span = { GridItemSpan(maxLineSpan) }, key = prefix + "h") {
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp)
        )
    }
    if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = prefix + "e") { Note(empty!!) }
    items(list, key = { prefix + it.pkg }) { tile(it) }
}

@Composable
fun Note(text: String) =
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Header(query: String, onQuery: (String) -> Unit, onGo: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column {
        Text(
            "¿A dónde saltas?", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 34.sp, lineHeight = 38.sp, color = c.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(c.surface).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = c.primary)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Escribe el nombre de una app", fontSize = 16.sp, color = c.onSurfaceVariant)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) Icon(
                Icons.Filled.Clear, "Borrar búsqueda", tint = c.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).clickable { onQuery("") }.padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(a: AppItem, pinned: Boolean, onOpen: (AppItem) -> Unit, onPin: (AppItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { onOpen(a) }, onLongClick = { onPin(a) })
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Image(a.icon, a.label, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            if (pinned) Box(
                Modifier.align(Alignment.TopEnd).offset(5.dp, (-5).dp).size(18.dp)
                    .clip(CircleShape).background(c.secondary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Star, "Fijada", Modifier.size(11.dp), tint = Color(0xFF3A2A00)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(a.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onBackground)
    }
}

@Composable
fun BubbleCard(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Burbuja flotante", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.onSurface)
            Text("Tus apps fijadas, a un toque desde cualquier app", fontSize = 13.sp, color = c.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
fun PickRow(picking: Boolean, count: Int, onPick: () -> Unit, onOrder: () -> Unit, onStyle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (picking) "Toca las apps para añadirlas o quitarlas ($count)" else "$count apps en la burbuja",
            fontSize = 13.sp, color = c.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (picking) "Listo" else "Elegir apps", picking, onPick)
            Pill("Orden", false, onOrder)
            Pill("Estilo", false, onStyle)
        }
    }
}

@Composable
fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (filled) c.primary else c.surface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) c.background else c.primary)
    }
}

@Composable
fun ScreenTitle(title: String, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = c.onBackground,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(8.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.onBackground)
    }
}

@Composable
fun OrderScreen(items: List<AppItem>, onMove: (String, String) -> Unit, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val cur by rememberUpdatedState(items)
    val rowH = with(LocalDensity.current) { 64.dp.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(16.dp)
    ) {
        ScreenTitle("Orden de la burbuja", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "Arrastra el icono de tres líneas hacia arriba o abajo. La primera app queda arriba en la burbuja.",
            fontSize = 13.sp, color = c.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) Note("Aún no hay apps en la burbuja. Usa «Elegir apps» en la pantalla principal.")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (a in items) key(a.pkg) {
                val isDrag = dragging == a.pkg
                Row(
                    Modifier.fillMaxWidth().height(64.dp).zIndex(if (isDrag) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDrag) offset else 0f
                            scaleX = if (isDrag) 1.02f else 1f
                            scaleY = if (isDrag) 1.02f else 1f
                        }
                        .padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (isDrag) lerp(c.surface, c.primary, 0.22f) else c.surface)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(a.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = c.onSurface, fontSize = 15.sp
                    )
                    Icon(
                        Icons.Filled.Menu, "Arrastrar", tint = c.onSurfaceVariant,
                        modifier = Modifier.size(44.dp).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = a.pkg; offset = 0f },
                                onDragEnd = { dragging = null; offset = 0f },
                                onDragCancel = { dragging = null; offset = 0f }
                            ) { change, d ->
                                change.consume()
                                offset += d.y
                                val i = cur.indexOfFirst { it.pkg == a.pkg }
                                if (offset > rowH / 2 && i in 0 until cur.lastIndex) {
                                    onMove(a.pkg, cur[i + 1].pkg); offset -= rowH
                                } else if (offset < -rowH / 2 && i > 0) {
                                    onMove(a.pkg, cur[i - 1].pkg); offset += rowH
                                }
                            }
                        }.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onChange: (Float) -> Unit, onDone: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    Column {
        Row {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.onBackground, modifier = Modifier.weight(1f))
            Text("${value.toInt()}$unit", color = c.onSurfaceVariant)
        }
        Slider(value, onChange, valueRange = range, onValueChangeFinished = onDone)
    }
}

@Composable
fun StyleScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var size by remember { mutableFloatStateOf(store.size().toFloat()) }
    var op by remember { mutableFloatStateOf(store.alpha().toFloat()) }
    var color by remember { mutableIntStateOf(store.colorIdx()) }
    var edge by remember { mutableStateOf(store.edge()) }
    var usageOk by remember { mutableStateOf(hasUsage(ctx)) }
    LaunchedEffect(resumeTick.intValue) { usageOk = hasUsage(ctx) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenTitle("Estilo de la burbuja", onBack)
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp)).background(c.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(size.dp).alpha(op / 100f).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(Palette.starts[color]), Color(Palette.ends[color])))),
                contentAlignment = Alignment.Center
            ) { Image(painterResource(R.drawable.ic_fg), null, Modifier.fillMaxSize().scale(1.1f)) }
        }
        LabeledSlider("Tamaño", size, 56f..88f, " dp", { size = it }) { store.setSize(size.toInt()) }
        LabeledSlider("Opacidad", op, 35f..100f, " %", { op = it }) { store.setAlpha(op.toInt()) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Color", fontWeight = FontWeight.SemiBold, color = c.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in Palette.names.indices) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(Palette.starts[i]), Color(Palette.ends[i]))))
                            .then(if (i == color) Modifier.border(3.dp, c.onBackground, CircleShape) else Modifier)
                            .clickable { color = i; store.setColor(i) }
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Esconderse en el borde", fontWeight = FontWeight.SemiBold, color = c.onSurface)
                Text("Se asoma a medias cuando no la usas", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            Switch(checked = edge, onCheckedChange = { edge = it; store.setEdge(it) })
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Botón de app anterior", fontWeight = FontWeight.SemiBold, color = c.onSurface)
            Text(
                if (usageOk) "Activo: la burbuja sabe en qué app estabas."
                else "Para saber en qué app estabas, Salto necesita «Acceso de uso». Sin él, el botón solo recuerda las apps que abres desde la burbuja.",
                fontSize = 13.sp, color = c.onSurfaceVariant
            )
            if (!usageOk) Pill("Dar acceso de uso", true) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }
}
