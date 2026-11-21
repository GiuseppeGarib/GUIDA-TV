package it.guidatvitalia

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.guidatvitalia.data.EpgChannel
import it.guidatvitalia.data.EpgProgram
import it.guidatvitalia.data.EpgSourceRepository
import it.guidatvitalia.data.EpgData
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TvChannel(
    val number: Int, val id: String, val name: String, val category: String,
    val current: EpgProgram? = null, val next: EpgProgram? = null
)
private val lcn = mapOf(
    "rai 1" to 1, "rai 2" to 2, "rai 3" to 3, "rete 4" to 4,
    "canale 5" to 5, "italia 1" to 6, "la7" to 7, "tv8" to 8, "nove" to 9,
    "20" to 20, "rai 4" to 21, "rai news 24" to 24, "rai 5" to 23,
    "rai movie" to 24, "rai premium" to 25, "rai storia" to 54
)
private val demoChannels = listOf(
    TvChannel(1,"","Rai 1","Generalista"), TvChannel(2,"","Rai 2","Generalista"),
    TvChannel(3,"","Rai 3","Generalista"), TvChannel(4,"","Rete 4","Generalista"),
    TvChannel(5,"","Canale 5","Generalista"), TvChannel(6,"","Italia 1","Generalista"),
    TvChannel(7,"","La7","Generalista"), TvChannel(8,"","TV8","Generalista"),
    TvChannel(9,"","NOVE","Intrattenimento"), TvChannel(20,"","20","Cinema e serie")
)
private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(ms))
private fun normalize(s: String) = s.lowercase(Locale.ITALY).replace(Regex("[^a-z0-9]"), "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuidaTvApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuidaTvApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guida_tv", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<TvChannel?>(null) }
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var sourceUrl by remember { mutableStateOf(prefs.getString("epg_url", "") ?: "") }
    var sourceDraft by remember { mutableStateOf(sourceUrl) }
    var epg by remember { mutableStateOf<EpgData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Nessun palinsesto caricato") }
    val scheme = if (darkMode) darkColorScheme(
        primary = Color(0xFF80CBC4), secondary = Color(0xFFFFB74D),
        surface = Color(0xFF171A1D), background = Color(0xFF101316)
    ) else lightColorScheme(
        primary = Color(0xFF00695C), secondary = Color(0xFFEF6C00),
        surface = Color.White, background = Color(0xFFF3F5F6)
    )

    fun refreshEpg(url: String) {
        if (url.isBlank()) { status = "Inserisci prima un indirizzo XMLTV nelle impostazioni"; return }
        scope.launch {
            loading = true
            status = "Scaricamento palinsesto…"
            try {
                val data = EpgSourceRepository().download(url.trim())
                epg = data
                status = "Aggiornati ${data.channels.size} canali · ${data.programs.size} programmi"
            } catch (e: Exception) {
                status = "Aggiornamento non riuscito: ${e.message ?: "errore sconosciuto"}"
            } finally { loading = false }
        }
    }

    val channels = remember(epg) {
        val data = epg
        if (data == null) demoChannels else {
            val now = System.currentTimeMillis()
            data.channels.map { ch ->
                val programs = data.programs.filter { it.channelId == ch.id }
                val current = programs.firstOrNull { it.startMillis <= now && (it.stopMillis ?: Long.MAX_VALUE) > now }
                val next = programs.firstOrNull { it.startMillis > now }
                val normalized = normalize(ch.name)
                val number = lcn.entries.firstOrNull { normalize(it.key) == normalized }?.value ?: (1000 + ch.name.hashCode().and(0x3fff))
                TvChannel(number, ch.id, ch.name, current?.category ?: "Televisione", current, next)
            }.sortedBy { it.number }
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("GUIDA TV ITALIA", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY)),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshEpg(sourceUrl) }, enabled = !loading) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Aggiorna palinsesto")
                        }
                        IconButton(onClick = { sourceDraft = sourceUrl; showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Search, null) }, label = { Text("Guida") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Preferiti") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    placeholder = { Text("Cerca un canale o un programma") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                Text(status, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedTab == 0) "TUTTI I CANALI" else "I TUOI PREFERITI",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${channels.size} canali", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val filtered = channels.filter {
                    (selectedTab == 0 || it.number in favorites) &&
                    (it.name.contains(query, true) || it.current?.title?.contains(query, true) == true ||
                        it.next?.title?.contains(query, true) == true)
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(filtered, key = { it.id.ifBlank { it.number.toString() } }) { channel ->
                        ChannelRow(channel, channel.number in favorites,
                            onFavorite = {
                                favorites = if (channel.number in favorites) favorites - channel.number
                                else favorites + channel.number
                            },
                            onClick = { selectedChannel = channel })
                    }
                }
            }
        }

        selectedChannel?.let { channel ->
            val schedule = epg?.programs?.filter { it.channelId == channel.id }?.take(12).orEmpty()
            AlertDialog(onDismissRequest = { selectedChannel = null },
                title = { Text("${channel.number} · ${channel.name}") },
                text = {
                    if (schedule.isEmpty()) Text("Nessun programma disponibile per questo canale.")
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        schedule.forEach { p ->
                            Column {
                                Text("${formatTime(p.startMillis)}  ${p.title}", fontWeight = FontWeight.SemiBold)
                                p.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selectedChannel = null }) { Text("Chiudi") } })
        }

        if (showSettings) {
            AlertDialog(onDismissRequest = { showSettings = false },
                title = { Text("Impostazioni") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Aspetto", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tema scuro", Modifier.weight(1f))
                            Switch(checked = darkMode, onCheckedChange = {
                                darkMode = it; prefs.edit().putBoolean("dark", it).apply()
                            })
                        }
                        HorizontalDivider()
                        Text("Sorgente palinsesti XMLTV", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = sourceDraft, onValueChange = { sourceDraft = it },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://…/guida.xml o .xml.gz") },
                            singleLine = true)
                        Text("Usa una sorgente XMLTV HTTPS che sei autorizzato a consultare. La disponibilità pubblica dei dati non implica il diritto di redistribuirli.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        sourceUrl = sourceDraft.trim()
                        prefs.edit().putString("epg_url", sourceUrl).apply()
                        showSettings = false
                        if (sourceUrl.isNotBlank()) refreshEpg(sourceUrl)
                    }) { Text("Salva e aggiorna") }
                },
                dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Annulla") } })
        }
    }
}

@Composable
private fun ChannelRow(channel: TvChannel, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (channel.number < 1000) channel.number.toString().padStart(2, '0') else "·",
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val current = channel.current
                if (current != null) {
                    Text("${formatTime(current.startMillis)}  ${current.title}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    channel.next?.let {
                        Text("A seguire ${formatTime(it.startMillis)}  ${it.title}", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else Text("Palinsesto non disponibile", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(Icons.Default.Favorite, contentDescription = "Preferito",
                    tint = if (favorite) Color(0xFFE65B65) else MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}
