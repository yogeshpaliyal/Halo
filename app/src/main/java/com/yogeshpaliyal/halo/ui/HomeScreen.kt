package com.yogeshpaliyal.halo.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.yogeshpaliyal.halo.data.Folder
import com.yogeshpaliyal.halo.data.Folders
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.overlay.NotificationBadges
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
    var appsLoaded by remember { mutableStateOf(false) }
    // Quick actions first, then every app; favourites can hold either. Only needed by the
    // pickers, so it loads in the background and the screen never waits on it.
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.IO) { repo.quickActions() + repo.launchableApps() }
        appsLoaded = true
    }
    var favorites by remember { mutableStateOf(Prefs.favorites(context)) }
    var foldersVersion by remember { mutableIntStateOf(0) }
    // Favourites are looked up one by one (a handful of apps), not from the full app list,
    // so the card fills in straight away.
    var favoriteCache by remember { mutableStateOf<Map<String, AppEntry>>(emptyMap()) }
    LaunchedEffect(favorites, foldersVersion, resumeTick) {
        val keys = favorites
        val loaded = withContext(Dispatchers.IO) { keys.mapNotNull { repo.entryFor(it) } }
        favoriteCache = favoriteCache + loaded.associateBy { it.key }
    }
    val favoriteEntries = remember(favorites, favoriteCache) { favorites.mapNotNull { favoriteCache[it] } }
    // Folder being created or edited, with its working name and apps.
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    // Folders are listed in the favourites picker like apps; editing one returns to it.
    var folderEntries by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(foldersVersion, resumeTick) {
        folderEntries = withContext(Dispatchers.IO) { Folders.all(context).mapNotNull { repo.entryFor(it.key) } }
    }
    var backToPicker by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var hiddenRecents by remember { mutableStateOf(Prefs.hiddenRecents(context)) }
    var showHiddenPicker by remember { mutableStateOf(false) }
    var tapAction by remember { mutableStateOf(Prefs.tapAction(context)) }
    var holdDelay by remember { mutableStateOf(Prefs.holdDelay(context)) }
    var haptics by remember { mutableStateOf(Prefs.haptics(context)) }
    var hideFullscreen by remember { mutableStateOf(Prefs.hideInFullscreen(context)) }
    var opacity by remember { mutableFloatStateOf(Prefs.bubbleOpacity(context)) }
    var bubbleSize by remember { mutableStateOf(Prefs.bubbleSize(context)) }
    var hideInApps by remember { mutableStateOf(Prefs.hideInApps(context)) }
    var showHideAppsPicker by remember { mutableStateOf(false) }
    var dots by remember { mutableStateOf(Prefs.notificationDots(context)) }
    val dotsAccess = remember(resumeTick) { NotificationBadges.hasAccess(context) }

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
                    // Removing a folder only frees its slot; it stays in the picker to add back.
                    onRemove = { favorites = Prefs.toggleFavorite(context, it.key) },
                    onMove = { index, delta -> favorites = Prefs.moveFavorite(context, index, delta) },
                    onEditFolder = { entry -> editingFolder = Folders.get(context, entry.key) },
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
            item {
                BubbleCard(
                    size = bubbleSize,
                    onSize = { bubbleSize = it; Prefs.setBubbleSize(context, it) },
                    opacity = opacity,
                    // Saved as you drag, so the real bubble updates live.
                    onOpacity = { opacity = it; Prefs.setBubbleOpacity(context, it) },
                    hideFullscreen = hideFullscreen,
                    onHideFullscreen = { hideFullscreen = it; Prefs.setHideInFullscreen(context, it) },
                    hiddenAppsCount = hideInApps.size,
                    hasUsage = hasUsage,
                    onChooseHiddenApps = { showHideAppsPicker = true },
                    dots = dots,
                    dotsAccess = dotsAccess,
                    onDots = { on ->
                        dots = on
                        Prefs.setNotificationDots(context, on)
                        if (on && !dotsAccess) {
                            runCatching { context.startActivity(NotificationBadges.settingsIntent(context)) }
                        }
                    },
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

    if (showHideAppsPicker) {
        // Usage events only cover the personal profile, so offer just those apps.
        AppPickerSheet(
            loading = !appsLoaded,
            apps = apps.filter { it.action == null && !it.isWork },
            selected = hideInApps.toList(),
            onToggle = { hideInApps = Prefs.toggleHideInApp(context, it.packageName) },
            onDismiss = { showHideAppsPicker = false },
            title = "Hide the bubble in",
            limit = null,
            showOrder = false,
            countLabel = { if (it == 0) "Tick apps where the bubble should hide" else "Hidden in $it" },
        )
    }

    if (showHiddenPicker) {
        // Recents only ever come from the personal profile, so offer just those apps.
        AppPickerSheet(
            loading = !appsLoaded,
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

    editingFolder?.let { folder ->
        val isNew = Folders.get(context, folder.key) == null
        AppPickerSheet(
            loading = !appsLoaded,
            apps = apps,
            selected = folder.apps,
            onToggle = { app ->
                val list = folder.apps.toMutableList()
                if (!list.remove(app.key) && list.size < Folders.MAX_APPS) list += app.key
                editingFolder = folder.copy(apps = list)
            },
            onDismiss = {
                // Saved on close. A folder left empty is dropped.
                if (folder.apps.isEmpty()) {
                    if (!isNew) Folders.delete(context, folder.key)
                } else if (!Folders.save(context, folder)) {
                    Toast.makeText(context, "Favourites are full. Remove one to add this folder.", Toast.LENGTH_LONG).show()
                }
                favorites = Prefs.favorites(context)
                foldersVersion++
                editingFolder = null
                if (backToPicker) {
                    backToPicker = false
                    showPicker = true
                }
            },
            title = if (isNew) "New folder" else "Edit folder",
            limit = Folders.MAX_APPS,
            countLabel = { "$it of ${Folders.MAX_APPS} apps. Rest on the folder in the dial to open it." },
            header = {
                OutlinedTextField(
                    value = folder.name,
                    onValueChange = { editingFolder = folder.copy(name = it.take(24)) },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. Social, Work, Travel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    TextButton(
                        onClick = {
                            Folders.delete(context, folder.key)
                            // Dismissing with no apps saves nothing.
                            editingFolder = folder.copy(apps = emptyList())
                            favorites = Prefs.favorites(context)
                            foldersVersion++
                            editingFolder = null
                            if (backToPicker) {
                                backToPicker = false
                                showPicker = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text("Delete folder", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    if (showPicker) {
        AppPickerSheet(
            loading = !appsLoaded,
            apps = folderEntries + apps,
            selected = favorites,
            onToggle = { favorites = Prefs.toggleFavorite(context, it.key) },
            onDismiss = { showPicker = false },
            leadingRow = {
                NewFolderRow(enabled = favorites.size < Prefs.MAX_FAVORITES) {
                    showPicker = false
                    backToPicker = true
                    editingFolder = Folders.newFolder()
                }
            },
            onEditFolder = { entry ->
                showPicker = false
                backToPicker = true
                editingFolder = Folders.get(context, entry.key)
            },
        )
    }
}
