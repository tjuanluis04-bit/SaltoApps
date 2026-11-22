package com.salto.app

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
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
    val results = if (searching) apps.filter { it.label.contains(query.trim(), true) } else emptyList()
    val pinned = favs.mapNotNull { byPkg[it] }
    val recent = recents.mapNotNull { byPkg[it] }.filter { it.pkg !in favs }.take(4)
    val tile: @Composable (AppItem) -> Unit = { a -> AppTile(a, a.pkg in favs, { open(a) }, { pin(a) }) }

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
        if (searching) {
            if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Note("Ninguna app coincide con «${query.trim()}».")
            }
            items(results, key = { "r-" + it.pkg }) { tile(it) }
        } else {
            section("Fijadas", pinned, "p-", "Mantén pulsada una app para fijarla aquí.", tile)
            section("Recientes", recent, "c-", null, tile)
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
fun AppTile(a: AppItem, pinned: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
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
