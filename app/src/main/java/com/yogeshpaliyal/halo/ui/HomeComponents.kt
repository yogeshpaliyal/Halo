package com.yogeshpaliyal.halo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.HoldDelay
import com.yogeshpaliyal.halo.data.Prefs
import com.yogeshpaliyal.halo.data.TapAction
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberIcon(app: AppEntry): ImageBitmap =
    remember(app.key) { app.icon.toBitmap(128, 128).asImageBitmap() }

// --- Hero: on/off switch plus a live preview of the menu ---------------------------------

@Composable
fun HeroCard(
    running: Boolean,
    canOverlay: Boolean,
    favorites: List<AppEntry>,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 20.dp, bottom = 20.dp),
            ) {
                Text(
                    if (running) "Bubble is on" else "Bubble is off",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !canOverlay -> "Allow “Display over other apps” to start."
                        running -> "Long-press it on any screen to switch apps."
                        else -> "Turn on to show it on the edge of your screen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Switch(checked = running, onCheckedChange = onToggle)
            }
            RadialPreview(
                favorites = favorites.map { rememberIcon(it) },
                modifier = Modifier
                    .width(170.dp)
                    .height(250.dp),
            )
        }
    }
}

/** A miniature of the real menu: bubble on the right, favourites inside, recents outside. */
@Composable
fun RadialPreview(favorites: List<ImageBitmap>, modifier: Modifier = Modifier) {
    val band = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    val emptySlot = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val recent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val bubble = MaterialTheme.colorScheme.primary
    val bubbleRing = MaterialTheme.colorScheme.primaryContainer
    val bubbleDot = MaterialTheme.colorScheme.onPrimary

    Canvas(modifier) {
        val cx = size.width - 26.dp.toPx()
        val cy = size.height / 2f
        val inner = 64.dp.toPx()
        val outer = 108.dp.toPx()
        val icon = 26.dp.toPx()
        val span = 160f

        fun slot(radius: Float, i: Int, count: Int): Offset {
            val step = span / count
            val angle = Math.toRadians((180f + span / 2f - (i + 0.5f) * step).toDouble())
            return Offset(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
        }

        listOf(inner, outer).forEach { r ->
            drawArc(
                color = band,
                startAngle = 180f - span / 2f - 4f,
                sweepAngle = span + 8f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = icon + 12.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        repeat(Prefs.MAX_RECENTS) { i ->
            drawCircle(recent, radius = icon / 2f * 0.85f, center = slot(outer, i, Prefs.MAX_RECENTS))
        }
        repeat(Prefs.MAX_FAVORITES) { i ->
            val c = slot(inner, i, Prefs.MAX_FAVORITES)
            val image = favorites.getOrNull(i)
            if (image != null) {
                drawImage(
                    image,
                    dstOffset = IntOffset((c.x - icon / 2f).toInt(), (c.y - icon / 2f).toInt()),
                    dstSize = IntSize(icon.toInt(), icon.toInt()),
                )
            } else {
                drawCircle(emptySlot, radius = icon / 2f * 0.85f, center = c, style = Stroke(1.5.dp.toPx()))
            }
        }
        val b = 20.dp.toPx()
        drawCircle(bubble, radius = b, center = Offset(cx, cy))
        drawCircle(bubbleRing, radius = b * 0.58f, center = Offset(cx, cy), style = Stroke(b * 0.12f))
        drawCircle(bubbleDot, radius = b * 0.18f, center = Offset(cx, cy))
    }
}

// --- Setup checklist -----------------------------------------------------------------------

data class SetupStep(
    val title: String,
    val body: String,
    val done: Boolean,
    /** Shown under a step that isn't done yet, with a button that opens App info. */
    val restrictedHelp: (() -> Unit)? = null,
    val onGrant: () -> Unit,
)

@Composable
fun SetupCard(steps: List<SetupStep>) {
    val done = steps.count { it.done }
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "$done of ${steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / steps.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
            )
            steps.forEachIndexed { index, step ->
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepBadge(number = index + 1, done = step.done)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            step.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!step.done) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = step.onGrant) { Text("Allow") }
                    }
                }
                val openAppInfo = step.restrictedHelp
                if (!step.done && openAppInfo != null) {
                    RestrictedHint(onOpenAppInfo = openAppInfo)
                }
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int, done: Boolean) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text("$number", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Explains Android's "restricted setting" block for sideloaded apps and links to App info,
 * where the ⋮ menu has "Allow restricted settings".
 */
@Composable
private fun RestrictedHint(onOpenAppInfo: () -> Unit) {
    Column(
        Modifier
            .padding(start = 46.dp, top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
    ) {
        Text(
            "Switch greyed out or “Restricted setting”?",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Android blocks this for apps installed outside an app store. Open App info, tap ⋮ " +
                "in the top corner, choose “Allow restricted settings”, then come back and tap Allow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenAppInfo,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) { Text("Open App info") }
    }
}

// --- Favourites: six ordered slots, hold and drag to reorder ----------------------------

private const val COLUMNS = 3

@Composable
fun FavoritesCard(
    favorites: List<AppEntry>,
    onAdd: () -> Unit,
    onRemove: (AppEntry) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }
    var slotSize by remember { mutableStateOf(IntSize.Zero) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /** Slot the dragged favourite would land on, from how far it has moved in whole slots. */
    fun targetIndex(): Int {
        if (dragIndex < 0 || slotSize == IntSize.Zero) return -1
        val col = dragIndex % COLUMNS + (dragOffset.x / (slotSize.width + gapPx)).roundToInt()
        val row = dragIndex / COLUMNS + (dragOffset.y / (slotSize.height + gapPx)).roundToInt()
        return (row.coerceAtLeast(0) * COLUMNS + col.coerceIn(0, COLUMNS - 1))
            .coerceIn(0, favorites.lastIndex)
    }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Favourites", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Inner ring, top to bottom. Hold and drag to reorder, tap for options.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onAdd) { Text("Edit") }
            }
            Spacer(Modifier.height(14.dp))
            val target = targetIndex()
            (0 until Prefs.MAX_FAVORITES).chunked(COLUMNS).forEach { row ->
                // The dragged slot must draw above the other row too, so lift its whole row.
                Row(
                    Modifier.zIndex(if (dragIndex in row) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { index ->
                        val app = favorites.getOrNull(index)
                        val dragging = index == dragIndex
                        Box(
                            Modifier
                                .weight(1f)
                                .zIndex(if (dragging) 1f else 0f)
                                .onSizeChanged { slotSize = it }
                                .graphicsLayer {
                                    if (dragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.06f
                                        scaleY = 1.06f
                                        shadowElevation = 12.dp.toPx()
                                        shape = slotShape
                                        clip = true
                                    }
                                },
                        ) {
                            if (app != null) {
                                FilledSlot(
                                    app = app,
                                    position = index + 1,
                                    isDropTarget = dragIndex >= 0 && !dragging && index == target,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < favorites.lastIndex,
                                    onMove = { delta -> onMove(index, delta) },
                                    onRemove = { onRemove(app) },
                                    dragModifier = Modifier.pointerInput(app.key, index) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dragIndex = index
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                            },
                                            onDragEnd = {
                                                val to = targetIndex()
                                                if (to >= 0 && to != dragIndex) onMove(dragIndex, to - dragIndex)
                                                dragIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                                )
                            } else {
                                EmptySlot(onAdd)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private val slotShape = RoundedCornerShape(20.dp)

@Composable
private fun FilledSlot(
    app: AppEntry,
    position: Int,
    isDropTarget: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(slotShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 2.dp,
                    color = if (isDropTarget) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = slotShape,
                )
                .then(dragModifier)
                .clickable { menuOpen = true }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                rememberIcon(app),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                alpha = if (app.available) 1f else 0.4f,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (app.isWork) {
                Spacer(Modifier.height(4.dp))
                WorkTag(paused = !app.available)
            }
        }
        Text(
            "$position",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Move earlier") },
                enabled = canMoveUp,
                onClick = { menuOpen = false; onMove(-1) },
            )
            DropdownMenuItem(
                text = { Text("Move later") },
                enabled = canMoveDown,
                onClick = { menuOpen = false; onMove(1) },
            )
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

@Composable
private fun EmptySlot(onAdd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(slotShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, slotShape)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Add favourite",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Gestures: two fixed, three configurable --------------------------------------------

@Composable
fun GesturesCard(
    tapAction: TapAction,
    holdDelay: HoldDelay,
    haptics: Boolean,
    onTapAction: (TapAction) -> Unit,
    onHoldDelay: (HoldDelay) -> Unit,
    onHaptics: (Boolean) -> Unit,
    hideFullscreen: Boolean,
    onHideFullscreen: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Text(
                "Gestures",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            GestureRow(
                tag = "Hold",
                title = "Hold, slide, let go",
                body = "Opens the app under your finger. Let go near the bubble to cancel.",
            )
            GestureRow(tag = "Drag", title = "Drag", body = "Moves the bubble. It snaps to the nearest side.")

            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            ChoiceRow(
                tag = "Tap",
                title = "Tap",
                value = tapAction,
                options = TapAction.entries,
                label = { it.label },
                detail = { it.description },
                onSelect = onTapAction,
            )
            ChoiceRow(
                tag = "Hold",
                title = "Hold delay",
                value = holdDelay,
                options = HoldDelay.entries,
                label = { it.label },
                detail = { "${it.millis} ms" },
                onSelect = onHoldDelay,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onHaptics(!haptics) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag("Feel")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Haptics", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Vibrate on hold and on each app you slide over",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = haptics, onCheckedChange = onHaptics)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onHideFullscreen(!hideFullscreen) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag("Auto")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hide in full-screen apps", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Games, videos and OTT apps. Comes back when the status bar does.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = hideFullscreen, onCheckedChange = onHideFullscreen)
            }
        }
    }
}

/** A setting row that shows its current value and opens a small menu of options. */
@Composable
private fun <T> ChoiceRow(
    tag: String,
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    detail: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tag(tag)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                label(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(x = 180.dp, y = 0.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(option))
                            Text(
                                detail(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = {
                        if (option == value) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun GestureRow(tag: String, title: String, body: String) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag(tag)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier
            .width(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Outer ring settings: which apps never appear as recents. */
@Composable
fun RecentsCard(hiddenCount: Int, hasUsage: Boolean, onChooseHidden: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Recent apps", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasUsage) "Outer ring, newest first. Screens Android keeps out of Recents are skipped."
                else "Allow Usage access in setup to fill the outer ring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onChooseHidden)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hide from recents", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (hiddenCount) {
                            0 -> "No apps hidden"
                            1 -> "1 app never shows in the outer ring"
                            else -> "$hiddenCount apps never show in the outer ring"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChooseHidden) { Text("Choose") }
            }
        }
    }
}

/** Small "Action" chip for quick actions (flashlight, volume, play/pause) in the picker. */
@Composable
fun ActionTag() {
    Text(
        "Action",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Small "Work" / "Work · paused" chip shown next to work-profile apps. */
@Composable
fun WorkTag(paused: Boolean) {
    Text(
        if (paused) "Work · paused" else "Work",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun PrivateSpaceNote() {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Work apps are supported, but only as favourites: Android's recent-apps data covers your " +
                "personal profile only. Private space apps can't appear in Halo; Android only shows " +
                "them to the default home-screen app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
