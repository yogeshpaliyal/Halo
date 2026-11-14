package com.yogeshpaliyal.halo.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import com.yogeshpaliyal.halo.overlay.BubbleRenderer
import androidx.compose.ui.draw.blur
import com.yogeshpaliyal.halo.overlay.DialColors
import com.yogeshpaliyal.halo.overlay.DialTheme
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * The bubble as it looks on screen, drawn by the same renderer. With [live] it blinks and
 * does its character move every couple of seconds, like the real one.
 */
@Composable
fun BubblePreview(
    character: BubbleCharacter,
    palette: BubblePalette,
    modifier: Modifier,
    live: Boolean = false,
    customImage: android.graphics.Bitmap? = null,
    accessory: com.yogeshpaliyal.halo.overlay.Accessory = com.yogeshpaliyal.halo.overlay.Accessory.NONE,
) {
    val renderer = remember { BubbleRenderer() }
    var blink by remember { mutableFloatStateOf(0f) }
    var wiggle by remember { mutableFloatStateOf(0f) }
    var look by remember { mutableFloatStateOf(0f) }
    if (live && character != BubbleCharacter.CLASSIC) {
        LaunchedEffect(character) {
            while (true) {
                delay(1400)
                animate(0f, 1f, animationSpec = tween(90)) { v, _ -> blink = v }
                animate(1f, 0f, animationSpec = tween(90)) { v, _ -> blink = v }
                delay(900)
                animate(0f, 1f, animationSpec = tween(700)) { v, _ ->
                    wiggle = kotlin.math.sin(v * Math.PI.toFloat() * 3f) * (1f - v)
                }
                delay(700)
                animate(0f, 1f, animationSpec = tween(1000)) { v, _ ->
                    look = when {
                        v < 0.2f -> v / 0.2f
                        v > 0.8f -> (1f - v) / 0.2f
                        else -> 1f
                    } * 0.7f
                }
            }
        }
    }
    Canvas(modifier) {
        renderer.character = character
        renderer.palette = palette
        renderer.customImage = customImage
        renderer.accessory = accessory
        renderer.blink = blink
        renderer.wiggle = wiggle
        renderer.lookX = look
        drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width / 2f, size.height / 2f, size.minDimension / 2f) }
    }
}

/** Character picker: a scrolling row of live previews. */
@Composable
fun CharacterRow(
    character: BubbleCharacter,
    palette: BubblePalette,
    onCharacter: (BubbleCharacter) -> Unit,
    customImage: android.graphics.Bitmap?,
    onPickPhoto: () -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BubbleCharacter.entries.forEach { c ->
            val selected = c == character
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        // "Your photo" always opens the photo picker, to pick a new one or re-crop.
                        if (c == BubbleCharacter.CUSTOM) onPickPhoto() else onCharacter(c)
                    }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c == BubbleCharacter.CUSTOM && customImage == null) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        BubblePreview(c, palette, Modifier.size(44.dp), live = selected, customImage = customImage)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(c.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Colour picker: gradient swatches. */
@Composable
fun PaletteRow(palette: BubblePalette, onPalette: (BubblePalette) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BubblePalette.available().forEach { p ->
            val selected = p == palette
            val label = stringResource(p.labelRes)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .padding(if (selected) 5.dp else 1.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(p.top), Color(p.bottom))))
                        .clickable(onClickLabel = label) { onPalette(p) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A tiny dial in the given theme over a stand-in wallpaper, so the scrim, glass and glow
 * read the way they will on screen.
 */
@Composable
fun DialPreview(theme: DialTheme, palette: BubblePalette, modifier: Modifier) {
    val c = DialColors.of(theme, palette)
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        // Stand-in wallpaper, blurred for glass like the real thing.
        Box(
            Modifier
                .matchParentSize()
                .then(if (c.blur) Modifier.blur(6.dp) else Modifier)
                .background(Brush.linearGradient(listOf(Color(0xFF6A8DFF), Color(0xFFFF8FB1), Color(0xFFFFD27A)))),
        )
        Canvas(Modifier.matchParentSize()) {
            drawRect(Color(c.scrim).copy(alpha = c.scrimAlpha / 255f))
            val cx = size.width * 0.06f
            val cy = size.height / 2f
            val r = size.height * 0.36f
            val bandW = size.height * 0.22f
            val topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r)
            val arcSize = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            drawArc(
                Color(c.band), -80f, 160f, useCenter = false, topLeft = topLeft, size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(bandW, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            for ((col, rr) in listOf(c.edgeOuter to r + bandW / 2, c.edgeInner to r - bandW / 2)) {
                if (col == 0) continue
                drawArc(
                    Color(col), -80f, 160f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - rr, cy - rr),
                    size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(size.height * 0.02f),
                )
            }
            // Four "apps", the second one highlighted.
            val dots = listOf(Color(0xFF4CAF50), Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFFB300))
            dots.forEachIndexed { i, col ->
                val a = Math.toRadians((-60 + i * 40).toDouble())
                val x = cx + r * kotlin.math.cos(a).toFloat()
                val y = cy + r * kotlin.math.sin(a).toFloat()
                if (i == 1) drawCircle(Color(c.glow), radius = bandW * 0.62f, center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(col, radius = bandW * (if (i == 1) 0.4f else 0.32f), center = androidx.compose.ui.geometry.Offset(x, y))
            }
            // Label pill.
            val a = Math.toRadians(-20.0)
            val px = cx + r * kotlin.math.cos(a).toFloat() - bandW * 1.9f
            val py = cy + r * kotlin.math.sin(a).toFloat() + bandW * 0.55f
            drawRoundRect(
                Color(c.pill),
                topLeft = androidx.compose.ui.geometry.Offset(px, py),
                size = androidx.compose.ui.geometry.Size(bandW * 2.2f, bandW * 0.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandW * 0.3f),
            )
        }
    }
}

/** Dial theme picker: mini dials in each theme. */
@Composable
fun DialThemeRow(theme: DialTheme, palette: BubblePalette, onTheme: (DialTheme) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DialTheme.entries.forEach { t ->
            val selected = t == theme
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTheme(t) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialPreview(
                    t, palette,
                    Modifier
                        .size(width = 84.dp, height = 64.dp)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            },
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(t.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The dial theme card on the Customise screen. */
@Composable
fun DialThemeCard(theme: DialTheme, palette: BubblePalette, onTheme: (DialTheme) -> Unit) {
    androidx.compose.material3.Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                stringResource(com.yogeshpaliyal.halo.R.string.dial_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            DialThemeRow(theme, palette, onTheme)
        }
    }
}

/** Accessory picker: previews on your character; locked ones show how many switches to go. */
@Composable
fun AccessoryRow(
    character: BubbleCharacter,
    palette: BubblePalette,
    customImage: android.graphics.Bitmap?,
    accessory: com.yogeshpaliyal.halo.overlay.Accessory,
    switches: Int,
    onAccessory: (com.yogeshpaliyal.halo.overlay.Accessory) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        com.yogeshpaliyal.halo.overlay.Accessory.entries.forEach { a ->
            val unlocked = switches >= a.unlockAt
            val selected = a == accessory
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = unlocked) { onAccessory(a) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BubblePreview(
                        character, palette,
                        Modifier
                            .size(44.dp)
                            .then(if (unlocked) Modifier else Modifier.graphicsLayer(alpha = 0.35f)),
                        customImage = customImage,
                        accessory = a,
                    )
                    if (!unlocked) Text("🔒", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (unlocked) stringResource(a.labelRes) else stringResource(com.yogeshpaliyal.halo.R.string.accessory_locked, a.unlockAt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
