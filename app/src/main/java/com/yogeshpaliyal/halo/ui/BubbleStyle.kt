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
import kotlinx.coroutines.delay

/**
 * The bubble as it looks on screen, drawn by the same renderer. With [live] it blinks and
 * does its character move every couple of seconds, like the real one.
 */
@Composable
fun BubblePreview(character: BubbleCharacter, palette: BubblePalette, modifier: Modifier, live: Boolean = false) {
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
                    .clickable { onCharacter(c) }
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
                    BubblePreview(c, palette, Modifier.size(44.dp), live = selected)
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
        BubblePalette.entries.forEach { p ->
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
