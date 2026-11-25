package com.etoile.cctv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.sqrt

val Navy = Color(0xFF1F3A4F)
val NavyDeep = Color(0xFF16293A)
val Gold = Color(0xFFC29F5C)
val Cream = Color(0xFFFFFDF0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold, onPrimary = Navy, background = Navy,
                    surface = NavyDeep, onSurface = Cream, onBackground = Cream
                )
            ) { Surface(Modifier.fillMaxSize(), color = Navy) { App() } }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var config by remember { mutableStateOf(ConfigStore.load(ctx)) }
    var splash by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(1800); splash = false }

    when {
        splash -> Splash()
        settings || !config.isReady -> SettingsScreen(config) {
            ConfigStore.save(ctx, it); config = it; settings = false
        }
        else -> GridScreen(config, onSettings = { settings = true })
    }
}

@Composable
fun Splash() = Box(Modifier.fillMaxSize().background(Navy), Alignment.Center) {
    Image(
        painterResource(R.drawable.logo), "Etoile Patisserie",
        Modifier.fillMaxHeight(0.8f).aspectRatio(1f), contentScale = ContentScale.Fit
    )
}

@Composable
fun SettingsScreen(initial: NvrConfig, onSave: (NvrConfig) -> Unit) {
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.pass) }
    var channels by remember { mutableStateOf(initial.channels.toString()) }
    var perPage by remember { mutableStateOf(initial.perPage.toString()) }
    var rotate by remember { mutableStateOf(initial.autoRotateSec.toString()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.height(120.dp).align(Alignment.CenterHorizontally))
        Text("Recorder connection", fontSize = 20.sp, color = Gold)
        @Composable fun field(v: String, l: String, set: (String) -> Unit) =
            OutlinedTextField(v, set, label = { Text(l) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        field(host, "NVR / DVR address", { host = it.trim() })
        field(port, "RTSP port", { port = it.filter(Char::isDigit) })
        field(user, "Username", { user = it })
        field(pass, "Password", { pass = it })
        field(channels, "Number of cameras (up to 64)", { channels = it.filter(Char::isDigit) })
        field(perPage, "Cameras per page (4, 9, 16, 25)", { perPage = it.filter(Char::isDigit) })
        field(rotate, "Auto-switch pages every N seconds (0 = off)", { rotate = it.filter(Char::isDigit) })
        Button(
            onClick = {
                onSave(
                    NvrConfig(
                        host, port.ifBlank { "554" }, user, pass,
                        (channels.toIntOrNull() ?: 64).coerceIn(1, 64),
                        (perPage.toIntOrNull() ?: 16).coerceIn(1, 36),
                        rotate.toIntOrNull() ?: 0
                    )
                )
            },
            enabled = host.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save and connect") }
    }
}

@Composable
fun GridScreen(cfg: NvrConfig, onSettings: () -> Unit) {
    val pages = ceil(cfg.channels / cfg.perPage.toFloat()).toInt()
    var page by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf<Int?>(null) }

    BackHandler(focused != null) { focused = null }

    LaunchedEffect(cfg.autoRotateSec, pages, focused) {
        if (cfg.autoRotateSec > 0 && pages > 1 && focused == null) {
            while (true) { delay(cfg.autoRotateSec * 1000L); page = (page + 1) % pages }
        }
    }

    Column(Modifier.fillMaxSize().background(NavyDeep)) {
        Row(
            Modifier.fillMaxWidth().background(Navy).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text("Etoile CCTV", color = Cream, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (focused == null) {
                TextButton({ page = (page - 1 + pages) % pages }, enabled = pages > 1) { Text("Prev") }
                Text("${page + 1} / $pages", color = Gold)
                TextButton({ page = (page + 1) % pages }, enabled = pages > 1) { Text("Next") }
            }
            TextButton(onSettings) { Text("Settings") }
        }

        val f = focused
        if (f != null) {
            // كاميرا واحدة بالـ main stream
            CameraTile(cfg.rtsp(f, 1), "CH $f", Modifier.fillMaxSize().padding(2.dp), fit = true)
        } else {
            val cols = ceil(sqrt(cfg.perPage.toDouble())).toInt()
            val rows = ceil(cfg.perPage / cols.toFloat()).toInt()
            // key(page) => لما تغيّر الصفحة كل الـ players القديمة بتتقفل قبل الجديدة
            key(page) {
                Column(Modifier.fillMaxSize()) {
                    for (r in 0 until rows) Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            val ch = page * cfg.perPage + r * cols + c + 1
                            Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                                if (ch <= cfg.channels && r * cols + c < cfg.perPage)
                                    CameraTile(cfg.rtsp(ch, 2), "CH $ch",
                                        Modifier.fillMaxSize().clickable { focused = ch })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraTile(url: String, label: String, modifier: Modifier, fit: Boolean = false) {
    val ctx = LocalContext.current
    var error by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }

    val player = remember(url, retry) {
        ExoPlayer.Builder(ctx).build().apply {
            val src = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(src)
            volume = 0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = true }
                override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) error = false }
            })
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    // إعادة محاولة تلقائية لو الكاميرا فصلت
    LaunchedEffect(error) { if (error) { delay(5000); retry++ } }

    Box(modifier.background(Color.Black).border(1.dp, NavyDeep)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT
                    else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            label, color = Cream, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopStart).background(Navy.copy(alpha = .7f)).padding(horizontal = 4.dp)
        )
        if (error) Text("No signal", color = Gold, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center))
    }
}
