package com.allworld.radio

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.compose.ui.graphics.luminance
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.allworld.radio.api.RadioClient
import com.allworld.radio.model.Station
import com.google.android.gms.ads.MobileAds
import com.google.common.util.concurrent.MoreExecutors
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.core.net.toUri
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.Locale
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import java.util.TimeZone
import androidx.compose.ui.text.style.TextAlign


// --- Вспомогательные классы ---
object LocaleHelper {
    fun applyLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

enum class AppTheme { LIGHT, DARK, AUTO }

sealed class Screen(val route: String, val icon: String) {
    object AllStations : Screen("all", "📻")
    object Favorites : Screen("fav", "❤️")
    object SleepTimer : Screen("timer", "⏳")
    object Settings : Screen("settings", "⚙️")
}

// --- ViewModels ---
class SettingsViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("radio_settings", Context.MODE_PRIVATE)

    var selectedGenre by mutableStateOf(prefs.getString("genre", "Все") ?: "Все")
    var selectedCountry by mutableStateOf(prefs.getString("country", "Все") ?: "Все")
    var iconSize by mutableFloatStateOf(prefs.getFloat("icon_size", 60f))
    var searchQuery by mutableStateOf("")
    var theme by mutableStateOf(AppTheme.valueOf(prefs.getString("theme", "AUTO") ?: "AUTO"))
    var bufferSizeMs by mutableFloatStateOf(prefs.getFloat("buffer_size", 15000f))
    var stopOnHeadsetDisconnect by mutableStateOf(prefs.getBoolean("stop_headset", true))
    var language by mutableStateOf(prefs.getString("app_lang", "ru") ?: "ru")

    // Настройки времени авто-темы
    var nightStartHour by mutableFloatStateOf(prefs.getFloat("night_start", 21f))
    var nightEndHour by mutableFloatStateOf(prefs.getFloat("night_end", 7f))

    var keepScreenOn by mutableStateOf(
        prefs.getBoolean("keep_screen_on", false)
    )
        private set
    fun toggleKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.edit { putBoolean("keep_screen_on", value) }
    }

    fun updateTheme(t: AppTheme) { theme = t; prefs.edit { putString("theme", t.name); apply() } }
    fun updateIconSize(s: Float) { iconSize = s; prefs.edit { putFloat("icon_size", s); apply() } }
    fun updateLanguage(l: String) { language = l; prefs.edit { putString("app_lang", l); apply() } }
    fun updateBuffer(b: Float) { bufferSizeMs = b; prefs.edit { putFloat("buffer_size", b); apply() } }
    fun toggleHeadset(s: Boolean) { stopOnHeadsetDisconnect = s; prefs.edit { putBoolean("stop_headset", s); apply() } }

    fun updateNightStart(h: Float) { nightStartHour = h; prefs.edit { putFloat("night_start", h); apply() } }
    fun updateNightEnd(h: Float) { nightEndHour = h; prefs.edit { putFloat("night_end", h); apply() } }

    fun isNightTime(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val start = nightStartHour.toInt()
        val end = nightEndHour.toInt()

        return if (start > end) {
            // Ночь через полночь (напр. с 21:00 до 07:00)
            currentHour >= start || currentHour < end
        } else if (start < end) {
            // Ночь в пределах одних суток (напр. с 00:00 до 05:00)
            currentHour in start until end
        } else {
            // Если старт и конец равны, тема не переключится (всегда день)
            false
        }
    }
}

class RadioViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("radio_favorites", Context.MODE_PRIVATE)
    var favoriteUrls = mutableStateListOf<String>()
    init { prefs.getStringSet("fav_list", emptySet())?.let { favoriteUrls.addAll(it) } }
    fun toggleFavorite(url: String) {
        if (favoriteUrls.contains(url)) favoriteUrls.remove(url) else favoriteUrls.add(url)
        prefs.edit { putStringSet("fav_list", favoriteUrls.toSet()) }
    }
}

class TimerViewModel : ViewModel() {
    var timeLeft by mutableLongStateOf(0L)
    var isRunning by mutableStateOf(false)
    private var timerJob: Job? = null

    fun startTimer(minutes: Int, controller: MediaController?) {
        if (minutes <= 0) return
        timerJob?.cancel()
        timeLeft = minutes * 60L
        isRunning = true
        // Используем viewModelScope - это стандарт для Android
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            controller?.pause()
            isRunning = false
        }
    }
    fun stopTimer() {
        timerJob?.cancel()
        timeLeft = 0
        isRunning = false
    }
}
@OptIn(ExperimentalMaterial3Api::class)

class MainActivity : ComponentActivity() {

    private var controller: MediaController? = null
    private var currentUrl by mutableStateOf<String?>(null)
    private var isPlaying by mutableStateOf(false)
    private var isBuffering by mutableStateOf(false)
    private var currentTitle by mutableStateOf<String?>(null)
    private var currentArtist by mutableStateOf<String?>(null)

    // Ссылка на ViewModel для доступа из onResume
    private lateinit var settingsViewModel: SettingsViewModel

    private var stationsState by mutableStateOf<List<Station>>(emptyList())

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("radio_settings", MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "ru") ?: "ru"
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, lang))
    }

    // Лаунчер для запроса разрешения на уведомления
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Разрешение получено, уведомления будут работать
        } else {
            // Пользователь отказал, уведомления плеера могут не появиться
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        MobileAds.initialize(this) {}

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        val testDeviceIds = listOf("8A2A7230967FF0F4DE96345DD1D1F524")
        val adConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(adConfig)

        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
                override fun onPlaybackStateChanged(s: Int) { isBuffering = (s == Player.STATE_BUFFERING) }
                override fun onMediaMetadataChanged(m: MediaMetadata) {
                    currentTitle = m.title?.toString()
                    currentArtist = m.artist?.toString()
                }
                override fun onMediaItemTransition(mi: MediaItem?, r: Int) {
                    currentUrl = mi?.requestMetadata?.mediaUri?.toString()
                }
            })
        }, MoreExecutors.directExecutor())

        setContent {
            val context = LocalContext.current

            // Инициализируем ViewModel так, чтобы она была доступна везде
            settingsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context.applicationContext) as T
            })
            val radioVM: RadioViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T = RadioViewModel(context.applicationContext) as T
            })
            val timerVM: TimerViewModel = viewModel()

            LaunchedEffect(Unit) {
                try {
                    val stations = withContext(Dispatchers.IO) {
                        RadioClient.service.getLocalStations()
                    }
                    stationsState = stations
                } catch (e: Exception) {
                    Log.e("RADIO_DEBUG", "Ошибка загрузки: ${e.localizedMessage}")
                }
            }

            val autoThemeTick by produceState(
                initialValue = System.currentTimeMillis(),
                key1 = settingsViewModel.theme,
                key2 = settingsViewModel.nightStartHour,
                key3 = settingsViewModel.nightEndHour
            ) {
                if (settingsViewModel.theme != AppTheme.AUTO) return@produceState

                while (true) {
                    val now = Calendar.getInstance(TimeZone.getDefault())
                    val hour = now.get(Calendar.HOUR_OF_DAY)
                    val minute = now.get(Calendar.MINUTE)

                    val start = settingsViewModel.nightStartHour.toInt()
                    val end = settingsViewModel.nightEndHour.toInt()

                    val minutesToBoundary = when {
                        start > end -> { // ночь через полночь
                            when {
                                hour < end -> (end - hour) * 60 - minute
                                hour < start -> (start - hour) * 60 - minute
                                else -> ((24 - hour) + end) * 60 - minute
                            }
                        }
                        else -> {
                            if (hour < start) (start - hour) * 60 - minute
                            else (end - hour) * 60 - minute
                        }
                    }

                    val delayMs = when {
                        minutesToBoundary > 15 -> 15 * 60_000L
                        minutesToBoundary > 2 -> 60_000L
                        else -> 1_000L
                    }

                    delay(delayMs)
                    value = System.currentTimeMillis()
                }
            }

            // ИСПРАВЛЕНО: Правильное обращение к settingsViewModel
            val isDark = when (settingsViewModel.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.AUTO -> {
                    autoThemeTick   // 👈 ключевой момент
                    settingsViewModel.isNightTime()
                }
            }

            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                // --- Не отключать экран при работе приложения ---
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        val systemUiController = WindowCompat.getInsetsController(window, view)
                        window.statusBarColor = colorScheme.surface.toArgb()
                        window.navigationBarColor = colorScheme.surface.toArgb()
                        systemUiController.isAppearanceLightStatusBars = !isDark
                        systemUiController.isAppearanceLightNavigationBars = !isDark
                    }

                    val keepScreenOn = settingsViewModel.keepScreenOn

                    DisposableEffect(keepScreenOn) {
                        val window = (view.context as Activity).window

                        if (keepScreenOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }

                        onDispose {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    // Передаем созданную settingsViewModel
                    MainAppLayout(
                        settingsVM = settingsViewModel,
                        radioVM = radioVM,
                        timerVM = timerVM,
                        onRefreshStations = { refreshStations() }
                    )
                }
            }
        }
    }

    private suspend fun refreshStations() {
        try {
            val stations = withContext(Dispatchers.IO) {
                RadioClient.service.getLocalStations()
            }
            stationsState = stations
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Ошибка обновления: ${e.localizedMessage}")
        }
    }



    private fun handleStationClick(station: Station) {
        val player = controller ?: return
        if (currentUrl == station.streamUrl) {
            if (isPlaying) player.pause() else player.play()
        } else {
            val mediaItem = MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(station.name)
                    // Оставляем пустую строку вместо жанра,
                    // чтобы не показывать технические ключи системы
                    .setArtist("")
                    .setArtworkUri(station.imageUrl.toUri())
                    .build())
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            currentUrl = station.streamUrl
        }
    }

    private fun playNextStation(currentList: List<Station>) {
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst { it.streamUrl == currentUrl }
        // Если станция найдена, берем следующую, иначе — первую из списка
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % currentList.size else 0
        handleStationClick(currentList[nextIndex])
    }

    private fun playPreviousStation(currentList: List<Station>) {
        if (currentList.isEmpty()) return
        val currentIndex = currentList.indexOfFirst { it.streamUrl == currentUrl }
        val prevIndex = if (currentIndex != -1) {
            if (currentIndex - 1 < 0) currentList.size - 1 else currentIndex - 1
        } else 0
        handleStationClick(currentList[prevIndex])
    }

    @Composable
    fun MainAppLayout(
        settingsVM: SettingsViewModel,
        radioVM: RadioViewModel,
        timerVM: TimerViewModel,
        onRefreshStations: suspend () -> Unit
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // 🔹 авто-refresh при возврате на экран "Все станции"
        LaunchedEffect(currentRoute) {
            if (currentRoute == Screen.AllStations.route) {
                onRefreshStations()
            }
        }

        val activeList = remember(
            currentRoute,
            stationsState,
            settingsVM.selectedGenre,
            settingsVM.selectedCountry,
            radioVM.favoriteUrls.size
        ) {
            when (currentRoute) {
                Screen.Favorites.route ->
                    stationsState.filter { radioVM.favoriteUrls.contains(it.streamUrl) }

                Screen.AllStations.route ->
                    stationsState.filter { station ->
                        (settingsVM.selectedGenre == "Все" || station.genre == settingsVM.selectedGenre) &&
                                (settingsVM.selectedCountry == "Все" || station.country == settingsVM.selectedCountry)
                    }

                else -> stationsState
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            topBar = { AdMobBanner() },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = currentUrl != null) {
                        BottomPlayerBar(
                            title = currentTitle ?: "Radio",
                            artist = currentArtist ?: "",
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onTogglePlay = { if (isPlaying) controller?.pause() else controller?.play() },
                            onNext = { playNextStation(activeList) },
                            onPrevious = { playPreviousStation(activeList) }
                        )
                    }
                    NavigationBar {
                        listOf(
                            Screen.AllStations,
                            Screen.Favorites,
                            Screen.SleepTimer,
                            Screen.Settings
                        ).forEach { screen ->
                            NavigationBarItem(
                                icon = { Text(screen.icon, fontSize = 22.sp) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (screen) {
                                                Screen.AllStations -> R.string.nav_radio
                                                Screen.Favorites -> R.string.nav_fav
                                                Screen.SleepTimer -> R.string.nav_timer
                                                Screen.Settings -> R.string.nav_settings
                                            }
                                        )
                                    )
                                },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController, startDestination = Screen.AllStations.route) {

                    composable(Screen.AllStations.route) {
                        val refreshState = rememberPullToRefreshState()

                        if (refreshState.isRefreshing) {
                            LaunchedEffect(Unit) {
                                onRefreshStations()
                                refreshState.endRefresh()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(refreshState.nestedScrollConnection)
                        ) {
                            StationGridScreen(
                                stations = stationsState,
                                settings = settingsVM,
                                radioVM = radioVM,
                                currentUrl = currentUrl,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                title = currentTitle,
                                artist = currentArtist,
                                onClick = { handleStationClick(it) },
                                modifier = Modifier.fillMaxSize()
                            )

                            PullToRefreshContainer(
                                state = refreshState,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }

                    composable(Screen.Favorites.route) {
                        val favs = stationsState.filter {
                            radioVM.favoriteUrls.contains(it.streamUrl)
                        }
                        if (favs.isEmpty()) {
                            EmptyFavoritesView()
                        } else {
                            StationGridScreen(
                                stations = favs,
                                settings = settingsVM,
                                radioVM = radioVM,
                                currentUrl = currentUrl,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                title = currentTitle,
                                artist = currentArtist,
                                onClick = { handleStationClick(it) }
                            )
                        }
                    }

                    composable(Screen.SleepTimer.route) {
                        SleepTimerPage(controller, timerVM)
                    }

                    composable(Screen.Settings.route) {
                        SettingsPage(settingsVM, stationsState)
                    }
                }
            }
        }
    }

    @Composable
    fun BottomPlayerBar(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isBuffering: Boolean,
        onTogglePlay: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit
    ) {
        val configuration = LocalConfiguration.current
        val isLargeScreen = configuration.screenWidthDp > 600
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

        // Фон и текст синхронизированы со StationListRow
        val playerBackgroundColor = if (isDarkTheme) {
            Color(0xFF3A3A3A)
        } else {
            Color(0xFFF0F0F0)
        }

        val playerTextColor = if (isDarkTheme) {
            Color.White
        } else {
            Color.Black
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = playerBackgroundColor,
            tonalElevation = 0.dp
        ) {
            Column {
                // Еле заметная линия раздела
                HorizontalDivider(
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.05f)
                    } else {
                        Color.Black.copy(alpha = 0.05f)
                    },
                    thickness = 0.5.dp
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = if (isLargeScreen) 12.dp else 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Информация о текущем эфире
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (isLargeScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                            color = playerTextColor
                        )

                        val displayArtist = if (artist.contains("genre_") || artist.contains("country_")) "" else artist

                        if (displayArtist.isNotEmpty()) {
                            Text(
                                text = displayArtist,
                                fontSize = if (isLargeScreen) 16.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = playerTextColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Кнопки управления (⏮️, ▶️, ⏭️)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isLargeScreen) 12.dp else 4.dp)
                    ) {
                        IconButton(onClick = onPrevious) {
                            Text(
                                text = "⏮️",
                                fontSize = if (isLargeScreen) 32.sp else 22.sp,
                                color = Color.Unspecified.copy(alpha = 0.8f)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(if (isLargeScreen) 70.dp else 48.dp)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(if (isLargeScreen) 36.dp else 26.dp),
                                    color = playerTextColor,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                IconButton(onClick = onTogglePlay, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = if (isPlaying) "⏸️" else "▶️",
                                        fontSize = if (isLargeScreen) 42.sp else 28.sp,
                                        color = Color.Unspecified.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onNext) {
                            Text(
                                text = "⏭️",
                                fontSize = if (isLargeScreen) 32.sp else 22.sp,
                                color = Color.Unspecified.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyFavoritesView() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center, // Центровка по вертикали
            horizontalAlignment = Alignment.CenterHorizontally // Центровка по горизонтали
        ) {
            Text(
                text = "❤️",
                fontSize = 64.sp,
                modifier = Modifier.alpha(0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.fav_empty),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center // Теперь ссылка будет работать
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.fav_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }


    @Composable
    fun AdMobBanner() {
        // Контейнер для баннера, чтобы зарезервировать место 50dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Это создаст пустую зону там, где часы и заряд
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Твой AdMob Banner
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        // Используем официальный ТЕСТОВЫЙ ID баннера от Google
                        setAdUnitId("ca-app-pub-2737733949699094/6399441011")
                        setAdSize(AdSize.BANNER)
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }

    }}

