package com.yogeshpaliyal.halo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.overlay.HaloService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PRIVACY_POLICY_URL = "https://github.com/yogeshpaliyal/Halo/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val running by HaloService.running.collectAsStateWithLifecycle()

    // Re-check permissions every time the user returns from Settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val canOverlay = remember(resumeTick) { Permissions.canDrawOverlays(context) }
    val hasUsage = remember(resumeTick) { Permissions.hasUsageAccess(context) }
    var hasNotif by remember(resumeTick) { mutableStateOf(Permissions.hasNotifications(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasNotif = it
    }

    // Bring the bubble back if it was on before (e.g. after an app update).
    LaunchedEffect(canOverlay) {
        if (canOverlay && Prefs.enabled(context) && !running) HaloService.start(context)
    }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    // Quick actions first, then every app; favourites can hold either.
    LaunchedEffect(resumeTick) { apps = withContext(Dispatchers.IO) { repo.quickActions() + repo.launchableApps() } }
    var favorites by remember { mutableStateOf(Prefs.favorites(context)) }
    val favoriteEntries = remember(favorites, apps) {
        val byKey = apps.associateBy { it.key }
        favorites.mapNotNull { byKey[it] }
    }
    var showPicker by remember { mutableStateOf(false) }
    var hiddenRecents by remember { mutableStateOf(Prefs.hiddenRecents(context)) }
    var showHiddenPicker by remember { mutableStateOf(false) }
    var tapAction by remember { mutableStateOf(Prefs.tapAction(context)) }
    var holdDelay by remember { mutableStateOf(Prefs.holdDelay(context)) }
    var haptics by remember { mutableStateOf(Prefs.haptics(context)) }

    val steps = buildList {
        val restrictedHelp: (() -> Unit)? = if (Permissions.mayBeRestricted) {
            { context.startActivity(Permissions.appInfoIntent(context)) }
        } else {
            null
        }
        add(SetupStep("Display over other apps", "Lets Halo draw the bubble.", canOverlay, restrictedHelp) {
            context.startActivity(Permissions.overlaySettingsIntent(context))
        })
        add(SetupStep("Usage access", "Fills the outer ring with your recent apps.", hasUsage, restrictedHelp) {
            context.startActivity(Permissions.usageSettingsIntent())
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(SetupStep("Notifications", "A silent notification with a Stop button.", hasNotif) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text("Halo") }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    running = running,
                    canOverlay = canOverlay,
                    favorites = favoriteEntries,
                    onToggle = { on ->
                        when {
                            !canOverlay -> context.startActivity(Permissions.overlaySettingsIntent(context))
                            on -> HaloService.start(context)
                            else -> HaloService.stop(context)
                        }
                    },
                )
            }
            item {
                AnimatedVisibility(visible = steps.any { !it.done }) {
                    SetupCard(steps)
                }
            }
            item {
                FavoritesCard(
                    favorites = favoriteEntries,
                    onAdd = { showPicker = true },
                    onRemove = { favorites = Prefs.toggleFavorite(context, it.key) },
                    onMove = { index, delta -> favorites = Prefs.moveFavorite(context, index, delta) },
                )
            }
            item {
                RecentsCard(
                    hiddenCount = hiddenRecents.size,
                    hasUsage = hasUsage,
                    onChooseHidden = { showHiddenPicker = true },
                )
            }
            item {
                GesturesCard(
                    tapAction = tapAction,
                    holdDelay = holdDelay,
                    haptics = haptics,
                    onTapAction = { tapAction = it; Prefs.setTapAction(context, it) },
                    onHoldDelay = { holdDelay = it; Prefs.setHoldDelay(context, it) },
                    onHaptics = { haptics = it; Prefs.setHaptics(context, it) },
                )
            }
            item { PrivateSpaceNote() }
            item {
                // Play expects apps that use sensitive permissions to link their policy in-app.
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                    Text("Privacy policy")
                }
            }
        }
    }

    if (showHiddenPicker) {
        // Recents only ever come from the personal profile, so offer just those apps.
        AppPickerSheet(
            apps = apps.filter { it.action == null && !it.isWork },
            selected = hiddenRecents.toList(),
            onToggle = { hiddenRecents = Prefs.toggleHiddenRecent(context, it.packageName) },
            onDismiss = { showHiddenPicker = false },
            title = "Hide from recents",
            limit = null,
            showOrder = false,
            countLabel = { if (it == 0) "Tick apps to keep out of the outer ring" else "$it hidden" },
        )
    }

    if (showPicker) {
        AppPickerSheet(
            apps = apps,
            selected = favorites,
            onToggle = { favorites = Prefs.toggleFavorite(context, it.key) },
            onDismiss = { showPicker = false },
        )
    }
}
