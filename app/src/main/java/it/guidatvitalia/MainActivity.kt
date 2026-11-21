package it.guidatvitalia

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import it.guidatvitalia.data.*
import it.guidatvitalia.notifications.ReminderWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DEFAULT_EPG_URL = "https://epgshare01.online/epgshare01/epg_ripper_IT1.xml.gz"
private val channelNumbers = mapOf(
    "rai1" to 1, "rai2" to 2, "rai3" to 3, "rete4" to 4, "canale5" to 5,
    "italia1" to 6, "la7" to 7, "tv8" to 8, "nove" to 9, "20" to 20,
    "rai4" to 21, "iris" to 22, "rai5" to 23, "raimovie" to 24, "20mediaset" to 20, "twentyseven" to 27, "27twentyseven" to 27,
    "raipremium" to 25, "cielo" to 26, "27" to 27, "tv2000" to 28,
    "la7d" to 29, "la5" to 30, "realtime" to 31, "qvc" to 32,
    "foodnetwork" to 33, "cine34" to 34, "focus" to 35, "warnertv" to 37,
    "giallo" to 38, "topcrime" to 39, "boing" to 40, "k2" to 41,
    "frisbee" to 44, "cartoonito" to 46, "rainews24" to 48,
    "dmax" to 52, "mediasetextra" to 55, "hgtv" to 56, "motortrend" to 59,
    "sportitalia" to 60, "supertennis" to 64
)
private fun normalized(value: String) = value.lowercase(Locale.ITALY).filter { it.isLetterOrDigit() }
private fun channelNumber(name: String): Int = FreeToAirFilter.lcnFor(EpgChannel("", name))
private fun timeText(millis: Long): String = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(millis))
private fun dateText(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALY))
private fun dayBounds(date: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
private data class ChannelView(val channel: EpgChannel, val number: Int, val current: EpgProgram?, val next: EpgProgram?)

private enum class Screen(val label: String) {
    GUIDE("Guida"), TIMELINE("Timeline"), ON_AIR("Ora in onda"),
    EVENING("Serata"), SEARCH("Cerca"), FAVORITES("Preferiti"), SETTINGS("Impostazioni")
}

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
    var epg by remember { mutableStateOf<EpgData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf("Inizializzazione archivio…") }
    var screen by remember { mutableStateOf(Screen.GUIDE) }
    var search by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var selectedChannel by remember { mutableStateOf<EpgChannel?>(null) }
    var favoriteChannels by remember {
        mutableStateOf(prefs.getStringSet("favorite_channels", emptySet())?.toSet() ?: emptySet())
    }
    var favoritePrograms by remember {
        mutableStateOf(prefs.getStringSet("favorite_programs", emptySet())?.toSet() ?: emptySet())
    }
    var themeMode by remember {
        mutableStateOf(prefs.getString("theme_mode", null) ?: if (prefs.getBoolean("dark", false)) "dark" else "system")
    }
    var sourceUrl by remember { mutableStateOf((prefs.getString("epg_url", null) ?: DEFAULT_EPG_URL).ifBlank { DEFAULT_EPG_URL }) }
    var tmdbApiKey by remember { mutableStateOf(prefs.getString("tmdb_api_key", "").orEmpty()) }
    var sourceDraft by remember { mutableStateOf(sourceUrl) }
    var eveningTime by remember { mutableIntStateOf(prefs.getInt("evening_hour", 20)) }
    var refreshInterval by remember { mutableIntStateOf(prefs.getInt("refresh_hours", 12)) }

    fun refreshEpg(url: String) {
        if (url.isBlank()) { status = "Inserisci un indirizzo XMLTV nelle impostazioni."; return }
        scope.launch {
            loading = true
            status = "Download del palinsesto in corso…"
            try {
                val data = EpgSourceRepository().download(url.trim())
                withContext(Dispatchers.IO) { EpgCache(context).use { it.replace(data) } }
                epg = data
                prefs.edit().putLong("last_epg_update", System.currentTimeMillis()).apply()
                WorkScheduling.scheduleEpgRefresh(context, refreshInterval)
                status = "Aggiornati ${data.channels.size} canali in chiaro · ${data.programs.size} programmi"
            } catch (error: Exception) {
                status = "Aggiornamento non riuscito: ${error.message ?: "errore sconosciuto"}"
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) {
        while (true) { delay(30_000); clockNow = System.currentTimeMillis() }
    }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) { EpgCache(context).use { it.read() } }
        if (cached.channels.isNotEmpty()) {
            epg = cached
            status = "Archivio locale · ${cached.channels.size} canali in chiaro"
        } else status = "Nessun archivio locale: scarico il primo palinsesto…"
        prefs.edit().putString("epg_url", sourceUrl).apply()
        WorkScheduling.scheduleEpgRefresh(context)
        refreshEpg(sourceUrl)
    }

    val allChannels = remember(epg, clockNow / 60_000) {
        val data = epg ?: EpgData(emptyList(), emptyList())
        val now = System.currentTimeMillis()
        data.channels.map { channel ->
            val programs = data.programs.asSequence().filter { it.channelId == channel.id }.sortedBy { it.startMillis }.toList()
            ChannelView(channel, FreeToAirFilter.lcnFor(channel),
                programs.firstOrNull { it.startMillis <= now && (it.stopMillis ?: Long.MAX_VALUE) > now },
                programs.firstOrNull { it.startMillis > now })
        }.sortedWith(compareBy<ChannelView> { it.number }.thenBy { it.channel.name })
    }
    LaunchedEffect(allChannels) {
        if (allChannels.isNotEmpty() && !prefs.getBoolean("legacy_favorites_migrated", false)) {
            val legacyNumbers = prefs.getStringSet("favorites", emptySet()).orEmpty()
            val migrated = allChannels.filter { it.number.toString() in legacyNumbers }.map { it.channel.id }.toSet()
            if (migrated.isNotEmpty()) {
                favoriteChannels = favoriteChannels + migrated
                prefs.edit().putStringSet("favorite_channels", favoriteChannels).apply()
            }
            prefs.edit().putBoolean("legacy_favorites_migrated", true).apply()
        }
    }
    val channelsById = remember(epg) { epg?.channels?.associateBy { it.id }.orEmpty() }
    val now = clockNow
    val currentPrograms = remember(epg, now / 60_000) {
        epg?.programs.orEmpty().filter { it.startMillis <= now && (it.stopMillis ?: Long.MAX_VALUE) > now }
            .sortedBy { channelsById[it.channelId]?.let(FreeToAirFilter::lcnFor) ?: 9999 }
    }
    val selectedDayPrograms = remember(epg, selectedDate) {
        val start = dayBounds(selectedDate).first + TimeUnit.HOURS.toMillis(6)
        val end = start + TimeUnit.HOURS.toMillis(24)
        epg?.programs.orEmpty().filter { it.startMillis < end && (it.stopMillis ?: Long.MAX_VALUE) > start }
            .sortedBy { it.startMillis }
    }

    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF80CBC4), secondary = Color(0xFFFFB74D),
        background = Color(0xFF101316), surface = Color(0xFF171A1D)
    ) else lightColorScheme(
        primary = Color(0xFF00695C), secondary = Color(0xFFEF6C00),
        background = Color(0xFFF3F5F6), surface = Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("GUIDA TV ITALIA", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(dateText(LocalDate.now()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshEpg(sourceUrl) }, enabled = !loading) {
                            if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                        }
                        IconButton(onClick = { screen = Screen.SETTINGS; sourceDraft = sourceUrl }) {
                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val items = listOf(Screen.GUIDE, Screen.TIMELINE, Screen.ON_AIR, Screen.EVENING, Screen.SEARCH, Screen.FAVORITES)
                    items.forEach { item ->
                        val icon = when (item) {
                            Screen.GUIDE -> Icons.Default.LiveTv
                            Screen.TIMELINE -> Icons.Default.ViewWeek
                            Screen.ON_AIR -> Icons.Default.PlayCircle
                            Screen.EVENING -> Icons.Default.NightsStay
                            Screen.SEARCH -> Icons.Default.Search
                            else -> Icons.Default.Favorite
                        }
                        NavigationBarItem(
                            selected = screen == item, onClick = { screen = item },
                            icon = { Icon(icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 9.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                Text(status, Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (screen != Screen.SETTINGS) {
                    DateSelector(selectedDate, onDate = { selectedDate = it })
                }
                when (screen) {
                    Screen.GUIDE -> GuideScreen(allChannels, favoriteChannels,
                        onFavorite = { channel ->
                            favoriteChannels = favoriteChannels.toMutableSet().also {
                                if (!it.add(channel.id)) it.remove(channel.id)
                            }
                            prefs.edit().putStringSet("favorite_channels", favoriteChannels).apply()
                        }, onProgram = { selectedChannel = it.first; selectedProgram = it.second })
                    Screen.TIMELINE -> TimelineScreen(
                        channels = allChannels, programs = selectedDayPrograms,
                        date = selectedDate, onProgram = { channel, program ->
                            selectedChannel = channel; selectedProgram = program
                        }
                    )
                    Screen.ON_AIR -> ProgramListScreen(
                        title = "IN ONDA ADESSO", programs = currentPrograms,
                        channels = channelsById, now = now,
                        onProgram = { channel, program -> selectedChannel = channel; selectedProgram = program }
                    )
                    Screen.EVENING -> {
                        val (start, end) = dayBounds(selectedDate)
                        val eveningStart = start + TimeUnit.HOURS.toMillis(eveningTime.toLong())
                        val eveningEnd = start + TimeUnit.HOURS.toMillis((eveningTime + 3).toLong())
                        val eveningPrograms = epg?.programs.orEmpty().filter {
                            it.startMillis >= eveningStart && it.startMillis < eveningEnd
                        }.sortedBy { it.startMillis }
                        ProgramListScreen(
                            title = "PRIMA SERATA · ${eveningTime.toString().padStart(2, '0')}:00",
                            programs = eveningPrograms, channels = channelsById, now = now,
                            onProgram = { channel, program -> selectedChannel = channel; selectedProgram = program }
                        )
                    }
                    Screen.SEARCH -> SearchScreen(search, { search = it }, epg?.programs.orEmpty(), channelsById,
                        onProgram = { channel, program -> selectedChannel = channel; selectedProgram = program })
                    Screen.FAVORITES -> FavoritesScreen(allChannels, favoriteChannels, favoritePrograms,
                        epg?.programs.orEmpty(), channelsById,
                        onChannelFavorite = { channel ->
                            favoriteChannels = favoriteChannels.toMutableSet().also {
                                if (!it.add(channel.id)) it.remove(channel.id)
                            }
                            prefs.edit().putStringSet("favorite_channels", favoriteChannels).apply()
                        },
                        onProgramFavorite = { program ->
                            val key = programKey(program)
                            favoritePrograms = favoritePrograms.toMutableSet().also {
                                if (!it.add(key)) it.remove(key)
                            }
                            prefs.edit().putStringSet("favorite_programs", favoritePrograms).apply()
                        },
                        onProgram = { channel, program -> selectedChannel = channel; selectedProgram = program })
                    Screen.SETTINGS -> SettingsScreen(
                        sourceDraft = sourceDraft, onSource = { sourceDraft = it },
                        tmdbApiKey = tmdbApiKey, onTmdbApiKey = { tmdbApiKey = it; prefs.edit().putString("tmdb_api_key", it.trim()).apply() },
                        themeMode = themeMode, onTheme = {
                            themeMode = it; prefs.edit().putString("theme_mode", it).apply()
                        },
                        eveningTime = eveningTime, onEveningTime = {
                            eveningTime = it; prefs.edit().putInt("evening_hour", it).apply()
                        },
                        refreshInterval = refreshInterval, onRefreshInterval = {
                            refreshInterval = it; prefs.edit().putInt("refresh_hours", it).apply()
                        },
                        lastUpdate = prefs.getLong("last_epg_update", 0L),
                        onSave = {
                            sourceUrl = sourceDraft.trim()
                            prefs.edit().putString("epg_url", sourceUrl).apply()
                            WorkScheduling.scheduleEpgRefresh(context)
                            refreshEpg(sourceUrl)
                            screen = Screen.GUIDE
                        }
                    )
                }
            }
        }

        if (selectedProgram != null && selectedChannel != null) {
            ProgramDetailsDialog(
                program = selectedProgram!!, channel = selectedChannel!!,
                favorite = programKey(selectedProgram!!) in favoritePrograms,
                onFavorite = {
                    val key = programKey(selectedProgram!!)
                    favoritePrograms = favoritePrograms.toMutableSet().also {
                        if (!it.add(key)) it.remove(key)
                    }
                    prefs.edit().putStringSet("favorite_programs", favoritePrograms).apply()
                },
                onReminder = {
                    val program = selectedProgram!!
                    val channel = selectedChannel!!
                    val permitted = Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!permitted) {
                        (context as? Activity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 703)
                        status = "Autorizza le notifiche e imposta nuovamente il promemoria."
                    } else if (program.startMillis <= System.currentTimeMillis()) {
                        status = "Il programma è già iniziato: non è possibile impostare un promemoria."
                    } else {
                        val delay = (program.startMillis - System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)).coerceAtLeast(0)
                        val data = Data.Builder().putString("program_title", program.title)
                            .putString("channel_name", channel.name)
                            .putInt("notification_id", (program.channelId + program.startMillis).hashCode()).build()
                        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(data)
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
                        WorkManager.getInstance(context).enqueue(request)
                        status = "Promemoria impostato per ${program.title}."
                    }
                },
                onDismiss = { selectedProgram = null; selectedChannel = null }
            )
        }
    }
}

private fun programKey(program: EpgProgram) = "${program.channelId}|${program.startMillis}|${program.title}"

@Composable
private fun DateSelector(date: LocalDate, onDate: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (-7L..7L).forEach { offset ->
            val day = LocalDate.now().plusDays(offset)
            FilterChip(
                selected = day == date, onClick = { onDate(day) },
                label = { Text(if (offset == 0L) "Oggi" else if (offset == 1L) "Domani" else dateText(day)) }
            )
        }
    }
}

@Composable
private fun GuideScreen(
    channels: List<ChannelView>, favorites: Set<String>,
    onFavorite: (EpgChannel) -> Unit, onProgram: (Pair<EpgChannel, EpgProgram>) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 10.dp)) {
        item { SectionHeading("TUTTI I CANALI", "${channels.size} canali") }
        items(channels, key = { it.channel.id }) { item ->
            ChannelRow(item, item.channel.id in favorites, { onFavorite(item.channel) }) {
                val p = item.current ?: item.next
                if (p != null) onProgram(item.channel to p)
            }
        }
    }
}

@Composable
private fun ChannelRow(item: ChannelView, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (item.number < 1000) item.number.toString().padStart(2, '0') else "·",
                Modifier.width(34.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(item.channel, Modifier.size(25.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.channel.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                (item.current ?: item.next)?.let { ProgramMetadataStrip(it, compact = true) }
                if (item.current != null) {
                    Text("${timeText(item.current.startMillis)}  ${item.current.title}",
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val duration = ((item.current.stopMillis ?: item.current.startMillis + 1) - item.current.startMillis).coerceAtLeast(1)
                    val progress = ((System.currentTimeMillis() - item.current.startMillis).toFloat() / duration).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp))
                    item.next?.let { Text("A seguire ${timeText(it.startMillis)} · ${it.title}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else Text("Programmazione non disponibile", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Favorite, null, tint = if (favorite) Color(0xFFE45B68) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(19.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

@Composable
private fun TimelineScreen(
    channels: List<ChannelView>, programs: List<EpgProgram>, date: LocalDate,
    onProgram: (EpgChannel, EpgProgram) -> Unit
) {
    val dayStart = dayBounds(date).first
    val timelineStart = dayStart + TimeUnit.HOURS.toMillis(6)
    val timelineEnd = dayStart + TimeUnit.HOURS.toMillis(30)
    val pixelsPerMinute = 1.35f
    val totalWidth = ((timelineEnd - timelineStart) / 60_000f * pixelsPerMinute).dp
    val now = System.currentTimeMillis()
    val sharedScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) {
        withFrameNanos { }
        if (date == LocalDate.now()) {
            val minutesFromStart = ((System.currentTimeMillis() - timelineStart) / 60_000f - 60f).coerceAtLeast(0f)
            val targetDp = minutesFromStart * pixelsPerMinute
            sharedScroll.scrollTo((targetDp * density.density).toInt())
        } else sharedScroll.scrollTo(0)
    }
    Column(Modifier.fillMaxSize()) {
        Text("PALINSESTO ORIZZONTALE · scorri per esplorare le fasce orarie",
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CANALE", Modifier.width(88.dp).padding(start = 8.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(sharedScroll)) {
                Row(Modifier.width(totalWidth).fillMaxHeight()) {
                    (6..29).forEach { hour ->
                        Box(Modifier.width((60 * pixelsPerMinute).dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                            Text("${(hour % 24).toString().padStart(2, '0')}:00", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        LazyColumn {
            items(channels, key = { it.channel.id }) { channel ->
                Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(88.dp).padding(start = 8.dp, end = 4.dp)) {
                        Text(if (channel.number < 1000) channel.number.toString() else "·",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(channel.channel, Modifier.size(20.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(channel.channel.name, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(sharedScroll)) {
                        Box(Modifier.width(totalWidth).fillMaxHeight()) {
                            val channelPrograms = programs.filter {
                                it.channelId == channel.channel.id && it.startMillis < timelineEnd &&
                                    (it.stopMillis ?: Long.MAX_VALUE) > timelineStart
                            }
                            channelPrograms.forEach { program ->
                                val startMillis = maxOf(program.startMillis, timelineStart)
                                val endMillis = minOf(program.stopMillis ?: startMillis + TimeUnit.MINUTES.toMillis(30), timelineEnd)
                                val left = ((startMillis - timelineStart) / 60_000f * pixelsPerMinute).dp
                                val width = (((endMillis - startMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(12)) / 60_000f) * pixelsPerMinute).dp
                                Box(
                                    Modifier.offset(x = left).width(width).fillMaxHeight().padding(end = 2.dp, top = 5.dp, bottom = 5.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (program.startMillis <= now && (program.stopMillis ?: Long.MAX_VALUE) > now)
                                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onProgram(channel.channel, program) }.padding(5.dp),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    Column {
                                        Text(program.title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(timeText(program.startMillis), fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (now >= timelineStart && now < timelineEnd) {
                                val x = ((now - timelineStart) / 60_000f * pixelsPerMinute).dp
                                Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(Color(0xFFE53935)))
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProgramListScreen(
    title: String, programs: List<EpgProgram>, channels: Map<String, EpgChannel>, now: Long,
    onProgram: (EpgChannel, EpgProgram) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        item { SectionHeading(title, "${programs.size} programmi") }
        if (programs.isEmpty()) item { EmptyMessage("Nessun programma disponibile per questa fascia oraria.") }
        items(programs, key = { programKey(it) }) { program ->
            val channel = channels[program.channelId]
            if (channel != null) ProgramRow(program, channel, now, onClick = { onProgram(channel, program) })
        }
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, programs: List<EpgProgram>,
    channels: Map<String, EpgChannel>, onProgram: (EpgChannel, EpgProgram) -> Unit
) {
    val results = remember(query, programs) {
        if (query.trim().length < 2) emptyList() else programs.asSequence().filter {
            it.title.contains(query.trim(), true) || it.description.orEmpty().contains(query.trim(), true)
        }.sortedBy { it.startMillis }.take(500).toList()
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            placeholder = { Text("Titolo, attore, descrizione…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, null) } },
            singleLine = true, shape = RoundedCornerShape(10.dp)
        )
        LazyColumn {
            item { SectionHeading("RISULTATI", if (query.length < 2) "inserisci almeno 2 caratteri" else "${results.size} trovati") }
            if (query.length >= 2 && results.isEmpty()) item { EmptyMessage("Nessun programma corrispondente alla ricerca.") }
            items(results, key = { programKey(it) }) { p ->
                channels[p.channelId]?.let { channel -> ProgramRow(p, channel, System.currentTimeMillis(), onClick = { onProgram(channel, p) }) }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    channels: List<ChannelView>, favoriteChannels: Set<String>, favoritePrograms: Set<String>,
    programs: List<EpgProgram>, channelMap: Map<String, EpgChannel>,
    onChannelFavorite: (EpgChannel) -> Unit, onProgramFavorite: (EpgProgram) -> Unit,
    onProgram: (EpgChannel, EpgProgram) -> Unit
) {
    LazyColumn {
        item { SectionHeading("CANALI PREFERITI", "${favoriteChannels.size}") }
        val selected = channels.filter { it.channel.id in favoriteChannels }
        if (selected.isEmpty()) item { EmptyMessage("Aggiungi un canale ai preferiti dalla Guida.") }
        items(selected, key = { "ch:${it.channel.id}" }) {
            ChannelRow(it, true, { onChannelFavorite(it.channel) }) {
                (it.current ?: it.next)?.let { p -> onProgram(it.channel, p) }
            }
        }
        item { SectionHeading("PROGRAMMI PREFERITI", "${favoritePrograms.size}") }
        val selectedPrograms = programs.filter { programKey(it) in favoritePrograms }.sortedBy { it.startMillis }
        if (selectedPrograms.isEmpty()) item { EmptyMessage("Salva i programmi dai dettagli con l'icona del cuore.") }
        items(selectedPrograms, key = { "pr:${programKey(it)}" }) { p ->
            channelMap[p.channelId]?.let { ch ->
                ProgramRow(p, ch, System.currentTimeMillis(), onClick = { onProgram(ch, p) },
                    onFavorite = { onProgramFavorite(p) }, favorite = true)
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: EpgProgram, channel: EpgChannel, now: Long,
    onClick: () -> Unit, onFavorite: (() -> Unit)? = null, favorite: Boolean = false
) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(68.dp)) {
                Text(timeText(program.startMillis), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(SimpleDateFormat("EEE d MMM", Locale.ITALY).format(Date(program.startMillis)), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(channel, Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(channel.name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Column(Modifier.weight(1f)) {
                ProgramMetadataStrip(program, compact = true)
                Text(program.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                program.category?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
                program.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (program.startMillis <= now && (program.stopMillis ?: Long.MAX_VALUE) > now) {
                    Text("IN ONDA", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (onFavorite != null) IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Favorite, null, tint = if (favorite) Color(0xFFE45B68) else MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

@Composable
private fun ProgramDetailsDialog(
    program: EpgProgram, channel: EpgChannel, favorite: Boolean,
    onFavorite: () -> Unit, onReminder: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(program.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(channel, Modifier.size(25.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${channel.name} · ${SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(Date(program.startMillis))} · ${timeText(program.startMillis)} – ${program.stopMillis?.let(::timeText) ?: "orario non disponibile"}",
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                ProgramMetadataStrip(program, compact = false, includeCast = true)
                program.category?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp) }
                program.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                if (program.description.isNullOrBlank()) Text("Descrizione non fornita dalla sorgente EPG.", fontSize = 12.sp)
                TextButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                    Spacer(Modifier.width(7.dp)); Text(if (favorite) "Rimuovi dai preferiti" else "Salva nei preferiti")
                }
                OutlinedButton(onClick = onReminder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.NotificationsActive, null); Spacer(Modifier.width(7.dp))
                    Text("Promemoria 5 minuti prima")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
private fun SettingsScreen(
    sourceDraft: String, onSource: (String) -> Unit,
    tmdbApiKey: String, onTmdbApiKey: (String) -> Unit,
    themeMode: String, onTheme: (String) -> Unit,
    eveningTime: Int, onEveningTime: (Int) -> Unit,
    refreshInterval: Int, onRefreshInterval: (Int) -> Unit,
    lastUpdate: Long, onSave: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("ASPETTO", Modifier.padding(top = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            listOf("system" to "Come il sistema", "light" to "Chiaro", "dark" to "Scuro").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { onTheme(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == value, onClick = { onTheme(value) })
                    Text(label, fontSize = 13.sp)
                }
            }
            HorizontalDivider()
            Text("LOCANDINE, VOTI E CAST", Modifier.padding(top = 12.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Inserisci la tua chiave API gratuita TMDB. L'app mostrerà il voto medio degli utenti e, quando disponibili, locandine e informazioni sul cast. Il voto è distinto da quello della critica professionale.",
                Modifier.padding(vertical = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = tmdbApiKey, onValueChange = onTmdbApiKey, modifier = Modifier.fillMaxWidth(),
                label = { Text("Chiave API TMDB") }, singleLine = true)
            Text("La chiave viene conservata nelle impostazioni locali del dispositivo e non viene pubblicata nel repository. Dati e immagini sono forniti da TMDB e Wikipedia. This product uses the TMDB API but is not endorsed or certified by TMDB.",
                Modifier.padding(vertical = 5.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("PALINSESTI XMLTV", Modifier.padding(top = 12.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Sorgente gratuita predefinita: EPGShare Italia. La guida applica un filtro ai canali italiani in chiaro riconosciuti; l'elenco può variare in base ai dati del feed.",
                Modifier.padding(vertical = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(sourceDraft, onSource, Modifier.fillMaxWidth(), label = { Text("Indirizzo HTTPS XMLTV") }, singleLine = true)
            Text("Feed di terze parti: disponibilità, copertura e diritti d'uso dipendono dal fornitore. Nessun dato viene inventato se il palinsesto non è disponibile.",
                Modifier.padding(vertical = 6.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ultimo aggiornamento: ${if (lastUpdate == 0L) "mai" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(lastUpdate))}",
                fontSize = 11.sp)
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("AGGIORNAMENTO AUTOMATICO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Intervallo: ogni ${refreshInterval} ore", fontSize = 12.sp)
            Slider(value = refreshInterval.toFloat(), onValueChange = { onRefreshInterval(it.toInt().coerceIn(6, 24)) },
                valueRange = 6f..24f, steps = 5)
            Text("Fascia serale", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Inizio: ${eveningTime.toString().padStart(2, '0')}:00", fontSize = 12.sp)
            Slider(value = eveningTime.toFloat(), onValueChange = { onEveningTime(it.toInt().coerceIn(18, 22)) },
                valueRange = 18f..22f, steps = 3)
            Button(onClick = onSave, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Salva impostazioni e aggiorna")
            }
        }
    }
}


@Composable
private fun ChannelLogo(channel: EpgChannel, modifier: Modifier = Modifier) {
    if (!channel.icon.isNullOrBlank()) {
        AsyncImage(model = channel.icon, contentDescription = "Logo ${channel.name}",
            modifier = modifier.clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Text(channel.name.take(2).uppercase(Locale.ITALY), fontSize = 8.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ProgramMetadataStrip(
    program: EpgProgram,
    compact: Boolean,
    includeCast: Boolean = false
) {
    val context = LocalContext.current
    val apiKey = remember {
        context.getSharedPreferences("guida_tv", Context.MODE_PRIVATE)
            .getString("tmdb_api_key", "").orEmpty()
    }
    var metadata by remember(program.title, apiKey, includeCast) {
        mutableStateOf<ProgramMetadata?>(null)
    }
    var loaded by remember(program.title, apiKey, includeCast) { mutableStateOf(false) }
    LaunchedEffect(program.title, apiKey, includeCast) {
        if (apiKey.isNotBlank()) {
            metadata = TmdbMetadataRepository.find(program.title, apiKey, includeCast)
        }
        loaded = true
    }
    if (metadata != null) {
        val data = metadata!!
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.posterUrl?.let { poster ->
                AsyncImage(model = poster, contentDescription = "Locandina ${program.title}",
                    modifier = Modifier.width(if (compact) 42.dp else 94.dp)
                        .height(if (compact) 60.dp else 138.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (data.rating != null) {
                    Text("★ ${"%.1f".format(Locale.ITALY, data.rating)} / 10 · ${data.voteCount} voti utenti TMDB",
                        fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("Voto utenti non disponibile", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (includeCast) {
                    Text("CAST PRINCIPALE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    if (data.cast.isEmpty()) {
                        Text("Cast non disponibile per questo titolo.", fontSize = 10.sp)
                    } else {
                        data.cast.forEach { actor ->
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(actor.name + actor.character.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                actor.biography?.let { Text(it, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                actor.wikipediaUrl?.let { link ->
                                    TextButton(onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                                    }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                                        Text("Scheda Wikipedia", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (includeCast && loaded && apiKey.isBlank()) {
        Text("Per locandine, voti e cast, inserisci la chiave API TMDB nelle Impostazioni.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (includeCast && loaded) {
        Text("Nessuna scheda TMDB disponibile per questo programma.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(22.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
