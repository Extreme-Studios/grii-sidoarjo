package com.grii.sidoarjo.navigation

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import com.grii.sidoarjo.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grii.sidoarjo.data.local.PortalSessionStore
import com.grii.sidoarjo.data.local.PlaylistLocalStore
import com.grii.sidoarjo.data.model.PortalPost
import com.grii.sidoarjo.data.model.Song
import com.grii.sidoarjo.data.repository.ApiSongRepository
import com.grii.sidoarjo.data.repository.PortalRepository
import com.grii.sidoarjo.data.repository.PlaylistRepository
import com.grii.sidoarjo.ui.screens.*
import com.grii.sidoarjo.ui.screens.PortalPosterSubmissionScreen
import com.grii.sidoarjo.ui.components.BannerAd
import com.grii.sidoarjo.ui.components.AdminLoginDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AppScreen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppScreen("home", "Home", Icons.Default.Home)
    data object Library : AppScreen("library", "Library", Icons.AutoMirrored.Filled.List)
    data object Live : AppScreen("live", "Live", Icons.Default.PlayArrow)
    data object PortalKita : AppScreen("portal_kita", "Board Info", Icons.Default.Info)
    data object About : AppScreen("about", "Profile", Icons.Default.AccountCircle)
}

@Composable
fun GriiSidoarjoApp(repository: ApiSongRepository = ApiSongRepository()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playlistRepository = remember { PlaylistRepository(PlaylistLocalStore(context)) }
    val portalRepository = remember { PortalRepository() }
    val portalSessionStore = remember { PortalSessionStore(context) }
    val portalSession by portalSessionStore.sessionFlow.collectAsState(initial = null)

    val tabs = listOf(
        AppScreen.Home,
        AppScreen.Library,
        AppScreen.Live,
        AppScreen.PortalKita,
        AppScreen.About
    )
    var currentTab by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    val tabHistory = remember { mutableStateListOf<AppScreen>().apply { add(AppScreen.Home) } }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedMovie by remember { mutableStateOf<Song?>(null) }
    var playingMovie by remember { mutableStateOf<Song?>(null) }
    var selectedPortalPost by remember { mutableStateOf<PortalPost?>(null) }
    var showPortalSubmission by remember { mutableStateOf(false) }
    var showRegistrationFromAbout by remember { mutableStateOf(false) }
    var loginReturnTab by remember { mutableStateOf<AppScreen?>(null) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    var isRefreshingSongs by remember { mutableStateOf(false) }
    var songsError by remember { mutableStateOf<String?>(null) }
    val genres = remember(songs) { repository.getGenres(songs) }
    val artists = remember(songs) { repository.getArtists(songs) }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var portalPosts by remember { mutableStateOf<List<PortalPost>>(emptyList()) }
    var isLoadingPortal by remember { mutableStateOf(true) }
    var portalError by remember { mutableStateOf<String?>(null) }
    val portalCategories = remember { portalRepository.getCategories() }
    val isInitialLoading = isLoadingSongs || isLoadingPortal
    val configuration = LocalConfiguration.current
    val isPlayerLandscapeFullscreen = playingMovie != null && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var liveStreamState by remember { mutableStateOf(com.grii.sidoarjo.data.model.LiveStreamState()) }

    fun navigateTo(screen: AppScreen) {
        selectedArtist = null
        selectedAlbum = null
        selectedMovie = null
        playingMovie = null
        selectedPortalPost = null
        showPortalSubmission = false
        showRegistrationFromAbout = false
        if (currentTab != screen) {
            currentTab = screen
            tabHistory.add(screen)
        }
    }

    fun openSong(song: Song) {
        selectedMovie = song
    }

    fun refreshSongs(showBlocking: Boolean) {
        scope.launch {
            if (showBlocking) isLoadingSongs = true else isRefreshingSongs = true
            val activeLikeUserId = portalSession?.username

            // 1. Fetch current songs list immediately (loads within ~500ms)
            repository.fetchSongs(activeLikeUserId).onSuccess { apiSongs ->
                songs = apiSongs
                songsError = null
                isLoadingSongs = false
                isRefreshingSongs = false
            }.onFailure { err ->
                songsError = err.message ?: "Failed to load data."
                isLoadingSongs = false
                isRefreshingSongs = false
            }

            // 2. Fetch live stream state immediately
            repository.fetchLiveStreamState().onSuccess { state ->
                liveStreamState = state
            }.onFailure { err ->
                android.util.Log.w("LiveStream", err.message ?: "Failed to fetch live stream state.")
            }

            // 3. Perform the YouTube channel sync asynchronously in the background (only on pull-to-refresh)
            if (!showBlocking) {
                scope.launch {
                    repository.syncDefaultChannel().onSuccess {
                        // Refresh songs again in background once new sync completes
                        repository.fetchSongs(activeLikeUserId).onSuccess { apiSongs ->
                            songs = apiSongs
                            songsError = null
                        }
                    }.onFailure { err ->
                        android.util.Log.w("ChannelSync", err.message ?: "Channel sync failed.")
                    }
                }
            }
        }
    }

    fun updateLiveStream(isLive: Boolean, url: String, callback: (Result<Unit>) -> Unit) {
        val session = portalSession
        if (session == null) {
            callback(Result.failure(IllegalStateException("Admin session not found.")))
            return
        }
        scope.launch {
            portalRepository.updateLiveStreamState(session, isLive, url).onSuccess {
                liveStreamState = com.grii.sidoarjo.data.model.LiveStreamState(isLive, url)
                callback(Result.success(Unit))
            }.onFailure { err ->
                callback(Result.failure(err))
            }
        }
    }

    fun refreshPortalPosts(showBlocking: Boolean = true) {
        scope.launch {
            if (showBlocking) isLoadingPortal = true
            portalRepository.fetchActivePosts().onSuccess { posts ->
                portalPosts = posts
                portalError = null
            }.onFailure { error ->
                portalError = error.message ?: "Failed to load Board Info."
            }
            isLoadingPortal = false
        }
    }

    LaunchedEffect(Unit) {
        refreshSongs(showBlocking = true)
        refreshPortalPosts(showBlocking = true)

        // Trigger background sync after 3 seconds so it doesn't block the UI startup
        scope.launch {
            delay(3000)
            repository.syncDefaultChannel().onSuccess {
                val activeLikeUserId = portalSession?.username
                repository.fetchSongs(activeLikeUserId).onSuccess { apiSongs ->
                    songs = apiSongs
                    songsError = null
                }
            }.onFailure { err ->
                android.util.Log.w("ChannelSync", err.message ?: "Channel sync failed.")
            }
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == AppScreen.Live || currentTab == AppScreen.About) {
            repository.fetchLiveStreamState().onSuccess { state ->
                liveStreamState = state
            }.onFailure { err ->
                android.util.Log.w("LiveStream", err.message ?: "Failed to fetch live stream state.")
            }
        }
    }

    LaunchedEffect(portalSession) {
        val session = portalSession
        if (session != null && session.status.equals("ACTIVE", ignoreCase = true)) {
            suspend fun checkStatus(): Boolean {
                var shouldContinue = true
                portalRepository.checkAdminStatus(session.username, session.password)
                    .onSuccess { updatedSession ->
                        if (!updatedSession.status.equals("ACTIVE", ignoreCase = true)) {
                            android.util.Log.d("HubAuth", "Check status: Status is no longer ACTIVE. Logging out.")
                            portalSessionStore.clearSession()
                            shouldContinue = false
                        } else if (updatedSession != session) {
                            portalSessionStore.saveSession(updatedSession)
                        }
                    }
                    .onFailure { error ->
                        if (error is IllegalStateException &&
                            (error.message?.contains("tidak ditemukan", ignoreCase = true) == true ||
                             error.message?.contains("salah", ignoreCase = true) == true ||
                             error.message?.contains("aktif", ignoreCase = true) == true)) {
                            android.util.Log.d("HubAuth", "Check status: Credentials/status rejected: ${error.message}. Logging out.")
                            portalSessionStore.clearSession()
                            shouldContinue = false
                        }
                    }
                return shouldContinue
            }

            if (checkStatus()) {
                while (true) {
                    delay(30000)
                    if (!checkStatus()) break
                }
            }
        }
    }

    BackHandler {
        when {
            playingMovie != null -> playingMovie = null
            selectedMovie != null -> selectedMovie = null
            showPortalSubmission -> showPortalSubmission = false
            showRegistrationFromAbout -> showRegistrationFromAbout = false
            selectedPortalPost != null -> selectedPortalPost = null
            selectedAlbum != null -> selectedAlbum = null
            selectedArtist != null -> selectedArtist = null
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)
                currentTab = tabHistory.last()
            }
            else -> activity?.moveTaskToBack(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_church),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.08f
        )
        Scaffold(
            bottomBar = {
                if (!isPlayerLandscapeFullscreen) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        BannerAd(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 4.dp)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                        tabs.forEach { screen ->
                            NavigationBarItem(
                                selected = currentTab == screen,
                                onClick = { navigateTo(screen) },
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                    indicatorColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                    }
                }
            }
        ) { innerPadding ->
            val modifier = if (isPlayerLandscapeFullscreen) Modifier else Modifier.padding(innerPadding)

            when {
            playingMovie != null -> {
                val movie = playingMovie!!
                VideoPlayerScreen(
                    title = movie.title,
                    rawUrl = movie.youtubeUrl.ifBlank { movie.movieUrl },
                    year = movie.year,
                    description = movie.synopsis.ifBlank { movie.description },
                    modifier = modifier,
                    onBack = { playingMovie = null }
                )
            }
            selectedMovie != null -> {
                MovieDetailScreen(
                    movie = selectedMovie!!,
                    modifier = modifier,
                    canEditMetadata = portalSession?.status.equals("ACTIVE", ignoreCase = true),
                    onWatchFilm = { url ->
                        playingMovie = selectedMovie
                    },
                    onSaveMetadata = { title, year, deskripsi, coverBase64, coverFileName, coverMime ->
                        val session = portalSession
                        if (session == null) {
                            Result.failure(IllegalStateException("Admin login required."))
                        } else {
                            repository.updateFilmMetadata(
                                filmId = selectedMovie!!.id,
                                username = session.username,
                                password = session.password,
                                title = title,
                                year = year,
                                deskripsi = deskripsi,
                                coverImageBase64 = coverBase64,
                                coverImageFileName = coverFileName,
                                coverImageMimeType = coverMime
                            )
                        }
                    },
                    onMetadataSaved = { title, year, deskripsi, newThumbnailUrl ->
                        selectedMovie = selectedMovie?.copy(
                            title = title,
                            year = year,
                            synopsis = deskripsi,
                            description = deskripsi,
                            releaseYear = year,
                            thumbnailUrl = newThumbnailUrl ?: selectedMovie!!.thumbnailUrl,
                            coverUrl = newThumbnailUrl ?: selectedMovie!!.coverUrl
                        )
                        refreshSongs(false)
                    },
                    onDeleteFilm = {
                        val session = portalSession
                        if (session == null) {
                            Result.failure(IllegalStateException("Admin login required."))
                        } else {
                            repository.deleteFilm(
                                filmId = selectedMovie!!.id,
                                username = session.username,
                                password = session.password
                            )
                        }
                    },
                    onFilmDeleted = {
                        selectedMovie = null
                        refreshSongs(false)
                    }
                )
            }
            showPortalSubmission -> {
                PortalPosterSubmissionScreen(
                    modifier = modifier,
                    onBack = { showPortalSubmission = false },
                    onSubmitted = { showPortalSubmission = false; refreshPortalPosts(true) },
                    onLoginClick = {
                        showPortalSubmission = false
                        loginReturnTab = AppScreen.PortalKita
                        showAdminLoginDialog = true
                    }
                )
            }
            showRegistrationFromAbout -> {
                SubmitSongScreen(modifier = modifier, onBack = { showRegistrationFromAbout = false })
            }
            selectedPortalPost != null -> {
                PortalPostDetailScreen(post = selectedPortalPost!!, modifier = modifier, onBack = { selectedPortalPost = null })
            }
            selectedAlbum != null -> {
                AlbumDetailScreen(
                    artistName = selectedAlbum!!.first,
                    albumName = selectedAlbum!!.second,
                    songs = songs,
                    modifier = modifier,
                    onBack = { selectedAlbum = null },
                    onSongClick = { openSong(it) }
                )
            }
            selectedArtist != null -> {
                ArtistDetailScreen(
                    artistName = selectedArtist!!,
                    songs = songs,
                    errorMessage = songsError,
                    modifier = modifier,
                    onBack = { selectedArtist = null },
                    onSongClick = { openSong(it) },
                    onAlbumClick = { selectedAlbum = selectedArtist!! to it }
                )
            }
            else -> when (currentTab) {
                AppScreen.Home -> HomeScreen(
                    songs = songs,
                    artists = artists,
                    errorMessage = songsError,
                    isLoading = isLoadingSongs,
                    isRefreshing = isRefreshingSongs,
                    onRefresh = { refreshSongs(false) },
                    modifier = modifier,
                    onSongClick = { openSong(it) },
                    onArtistClick = { selectedArtist = it },
                    liveStreamState = liveStreamState,
                    onWatchLiveClick = { url ->
                        playingMovie = Song(
                            id = "live",
                            title = "Ibadah Live Streaming GRII Sidoarjo",
                            youtubeUrl = url,
                            synopsis = "Live streaming ibadah Reformed Injili Indonesia Sidoarjo"
                        )
                    }
                )
                AppScreen.Library -> LibraryScreen(
                    songs = songs,
                    genres = genres,
                    playlists = playlists,
                    errorMessage = songsError,
                    isLoading = isLoadingSongs,
                    isRefreshing = isRefreshingSongs,
                    onRefresh = { refreshSongs(false) },
                    modifier = modifier,
                    onSongClick = { openSong(it) },
                    onArtistClick = { selectedArtist = it },
                    onAddSongToPlaylist = { pId, sId -> scope.launch { playlistRepository.addSongToPlaylist(pId, sId) } }
                )
                AppScreen.Live -> LiveScreen(
                    state = liveStreamState,
                    onWatchClick = { url ->
                        playingMovie = Song(
                            id = "live",
                            title = "Ibadah Live Streaming GRII Sidoarjo",
                            youtubeUrl = url,
                            synopsis = "Live streaming ibadah Reformed Injili Indonesia Sidoarjo"
                        )
                    },
                    isAdminLoggedIn = portalSession?.status.equals("ACTIVE", ignoreCase = true),
                    onUpdateLiveStream = { isLive, url, callback ->
                        updateLiveStream(isLive, url, callback)
                    },
                    modifier = modifier
                )
                AppScreen.PortalKita -> PortalKitaScreen(
                    posts = portalPosts,
                    categories = portalCategories,
                    isLoading = isLoadingPortal,
                    errorMessage = portalError,
                    modifier = modifier,
                    onRefresh = { refreshPortalPosts(true) },
                    onPostPreview = { selectedPortalPost = it },
                    onOpenPosterSubmission = { showPortalSubmission = true },
                    onLoginClick = {
                        loginReturnTab = AppScreen.PortalKita
                        showAdminLoginDialog = true
                    }
                )
                AppScreen.About -> AboutScreen(
                    modifier = modifier,
                    onOpenRegistration = { showRegistrationFromAbout = true },
                    liveStreamState = liveStreamState,
                    onUpdateLiveStream = { isLive, url, callback ->
                        updateLiveStream(isLive, url, callback)
                    }
                )
            }
        }

        if (isInitialLoading) {
            InitialLoadingOverlay()
        }
        if (showAdminLoginDialog) {
            AdminLoginDialog(
                repository = portalRepository,
                sessionStore = portalSessionStore,
                onDismiss = {
                    showAdminLoginDialog = false
                    loginReturnTab = null
                },
                onSuccess = {
                    val targetTab = loginReturnTab
                    showAdminLoginDialog = false
                    loginReturnTab = null
                    if (targetTab != null) {
                        currentTab = targetTab
                        if (targetTab == AppScreen.PortalKita) refreshPortalPosts(true)
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun InitialLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FCF8), Color(0xFFF3F8F3), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Loading content...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Preparing videos, information, and profile data",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )
        }
    }
}







