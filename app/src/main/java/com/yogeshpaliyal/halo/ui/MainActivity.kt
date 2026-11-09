package com.yogeshpaliyal.halo.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Permissions
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.overlay.HaloService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HaloTheme { SettingsScreen() } }
    }
}

@Composable
private fun HaloTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val running by HaloService.running.collectAsStateWithLifecycle()

    // Re-read permission state every time the user comes back from Settings.
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

    // Bring the bubble back if it was on before (e.g. after the app was updated).
    LaunchedEffect(canOverlay) {
        if (canOverlay && Prefs.enabled(context) && !running) HaloService.start(context)
    }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { repo.launchableApps() } }
    var favorites by remember { mutableStateOf(Prefs.favorites(context)) }
    var query by remember { mutableStateOf("") }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Halo", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Long-press the bubble, slide to an app, let go. Tap it to jump back to your last app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                PermissionCard(
                    title = "Display over other apps",
                    body = "Needed to draw the bubble.",
                    granted = canOverlay,
                ) { context.startActivity(Permissions.overlaySettingsIntent(context)) }
            }
            item {
                PermissionCard(
                    title = "Usage access",
                    body = "Needed for the recent-apps ring. Without it you only get favourites.",
                    granted = hasUsage,
                ) { context.startActivity(Permissions.usageSettingsIntent()) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionCard(
                        title = "Notifications",
                        body = "Shows a quiet notification with a Stop button while the bubble is on.",
                        granted = hasNotif,
                    ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Floating bubble", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (canOverlay) "Shown on top of every app" else "Grant overlay permission first",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = running,
                            enabled = canOverlay,
                            onCheckedChange = { on ->
                                if (on) HaloService.start(context) else HaloService.stop(context)
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Favourites (${favorites.size}/${Prefs.MAX_FAVORITES})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Inner ring, top to bottom in the order you pick them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val filtered = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedBy { app -> favorites.indexOf(app.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in favorites
                val full = favorites.size >= Prefs.MAX_FAVORITES
                AppRow(
                    app = app,
                    checked = checked,
                    enabled = checked || !full,
                ) { favorites = Prefs.toggleFavorite(context, app.packageName) }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) Text("Granted", style = MaterialTheme.typography.labelLarge)
            else FilledTonalButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val bitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
