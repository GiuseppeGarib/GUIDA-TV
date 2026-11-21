package it.guidatvitalia

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class TvChannel(val number: Int, val name: String, val category: String)
private val demoChannels = listOf(
    TvChannel(1, "Rai 1", "Generalista"), TvChannel(2, "Rai 2", "Generalista"),
    TvChannel(3, "Rai 3", "Generalista"), TvChannel(4, "Rete 4", "Generalista"),
    TvChannel(5, "Canale 5", "Generalista"), TvChannel(6, "Italia 1", "Generalista"),
    TvChannel(7, "La7", "Generalista"), TvChannel(8, "TV8", "Generalista"),
    TvChannel(9, "NOVE", "Intrattenimento"), TvChannel(20, "20", "Cinema e serie"),
    TvChannel(21, "Rai 4", "Cinema e serie"), TvChannel(24, "Rai News 24", "News"),
    TvChannel(25, "Rai 5", "Cultura"), TvChannel(26, "Rai Movie", "Cinema e serie")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuidaTvApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuidaTvApp() {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<TvChannel?>(null) }
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var darkMode by remember { mutableStateOf(false) }
    val scheme = if (darkMode) darkColorScheme(
        primary = Color(0xFF80CBC4), secondary = Color(0xFFFFB74D),
        surface = Color(0xFF171A1D), background = Color(0xFF101316)
    ) else lightColorScheme(
        primary = Color(0xFF00695C), secondary = Color(0xFFEF6C00),
        surface = Color(0xFFFFFFFF), background = Color(0xFFF3F5F6)
    )

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("GUIDA TV ITALIA", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Cerca un canale") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedTab == 0) "TUTTI I CANALI" else "I TUOI PREFERITI",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Palinsesto EPG non ancora caricato", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val channels = demoChannels.filter {
                    (selectedTab == 0 || it.number in favorites) &&
                    (it.name.contains(query, true) || it.category.contains(query, true))
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(channels, key = { it.number }) { channel ->
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
            AlertDialog(onDismissRequest = { selectedChannel = null },
                title = { Text("${channel.number} · ${channel.name}") },
                text = { Text("Il palinsesto di questo canale apparirà qui dopo la configurazione di una sorgente EPG XMLTV.") },
                confirmButton = { TextButton(onClick = { selectedChannel = null }) { Text("Chiudi") } })
        }

        if (showSettings) {
            AlertDialog(onDismissRequest = { showSettings = false },
                title = { Text("Impostazioni") },
                text = {
                    Column {
                        Text("Aspetto", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tema scuro", Modifier.weight(1f))
                            Switch(checked = darkMode, onCheckedChange = { darkMode = it })
                        }
                        Divider()
                        Text("Sorgente palinsesti", fontWeight = FontWeight.SemiBold)
                        Text("La configurazione della sorgente XMLTV e l'aggiornamento automatico saranno aggiunti nella fase EPG.")
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("OK") } })
        }
    }
}

@Composable
private fun ChannelRow(channel: TvChannel, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(channel.number.toString().padStart(2, '0'), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(channel.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Palinsesto in attesa della sorgente EPG", fontSize = 11.sp,
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
