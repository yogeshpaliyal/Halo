package com.yogeshpaliyal.halo.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.TapAction
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import com.yogeshpaliyal.halo.overlay.BubbleRenderer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class TutorialStep { HOLD, SLIDE, DONE }

/** Fake apps on the practice dial; the starred one is the target. */
private val PRACTICE_APPS = listOf("💬", "📷", "⭐", "🎵", "🗺️")
private const val TARGET = 2
private const val HOLD_MS = 300L

/**
 * A 20-second practice run of the core gesture, inside the app: press and hold the bubble,
 * slide to the starred app, let go. Then a quick reminder of what tap and double tap do.
 * Drawn with the user's own bubble style.
 */
@Composable
fun TutorialDialog(
    character: BubbleCharacter,
    palette: BubblePalette,
    customImage: android.graphics.Bitmap?,
    tapAction: TapAction,
    doubleTapAction: TapAction,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(TutorialStep.HOLD) }
    var message by remember { mutableIntStateOf(R.string.tut_step1) }
    var selected by remember { mutableIntStateOf(-1) }
    var touchingBubble by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val renderer = remember { BubbleRenderer() }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val emoji = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    }

    FullScreenDialog(onDismiss = onDone, lightContent = !isLightTheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tut_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (step != TutorialStep.DONE) TextButton(onClick = onDone) { Text(stringResource(R.string.tut_skip)) }
            }

            if (step == TutorialStep.DONE) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BubblePreview(character, palette, Modifier.padding(8.dp).then(Modifier.height(96.dp).fillMaxWidth(0.3f)), live = true, customImage = customImage)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.tut_success), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tut_success_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    TipRow("👆", stringResource(R.string.tut_tip_tap, stringResource(tapAction.labelRes)))
                    TipRow("✌️", stringResource(R.string.tut_tip_double, stringResource(doubleTapAction.labelRes)))
                    TipRow("↕️", stringResource(R.string.tut_tip_drag))
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDone) { Text(stringResource(R.string.tut_done)) }
                }
                return@Column
            }

            AnimatedContent(targetState = message, label = "message") { m ->
                Text(
                    stringResource(m),
                    style = MaterialTheme.typography.titleMedium,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        val bubbleR = 30.dp.toPx()
                        val ringR = 130.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val bubble = Offset(bubbleR + 12.dp.toPx(), size.height / 2f)
                            if (hypot(down.position.x - bubble.x, down.position.y - bubble.y) > bubbleR * 1.6f) return@awaitEachGesture
                            touchingBubble = true
                            // Held long enough without lifting? Then the dial opens.
                            val lifted = withTimeoutOrNull(HOLD_MS) {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.changes.all { !it.pressed }) break
                                }
                                true
                            }
                            if (lifted == true) {
                                touchingBubble = false
                                message = R.string.tut_hold_longer
                                return@awaitEachGesture
                            }
                            step = TutorialStep.SLIDE
                            message = R.string.tut_step2
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            var pick = -1
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull() ?: break
                                val dx = ch.position.x - bubble.x
                                val dy = ch.position.y - bubble.y
                                val dist = hypot(dx, dy)
                                pick = if (dist < ringR * 0.45f) {
                                    -1
                                } else {
                                    val a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                    PRACTICE_APPS.indices.minByOrNull { kotlin.math.abs(slotAngle(it) - a) }
                                        ?.takeIf { kotlin.math.abs(slotAngle(it) - a) < 22f } ?: -1
                                }
                                if (pick != selected) {
                                    selected = pick
                                    if (pick >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (!ch.pressed) break
                            }
                            touchingBubble = false
                            selected = -1
                            when (pick) {
                                TARGET -> step = TutorialStep.DONE
                                -1 -> { step = TutorialStep.HOLD; message = R.string.tut_missed_none }
                                else -> { step = TutorialStep.HOLD; message = R.string.tut_missed }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val bubbleR = 30.dp.toPx()
                    val ringR = 130.dp.toPx()
                    val icon = 46.dp.toPx()
                    val c = Offset(bubbleR + 12.dp.toPx(), size.height / 2f)
                    if (step == TutorialStep.SLIDE) {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                        drawArc(
                            Color(0xE61E1B2E), -84f, 168f, useCenter = false,
                            topLeft = Offset(c.x - ringR, c.y - ringR),
                            size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                            style = Stroke(icon + 18.dp.toPx(), cap = StrokeCap.Round),
                        )
                        PRACTICE_APPS.forEachIndexed { i, e ->
                            val a = Math.toRadians(slotAngle(i).toDouble())
                            val p = Offset(c.x + ringR * cos(a).toFloat(), c.y + ringR * sin(a).toFloat())
                            val sel = i == selected
                            if (i == TARGET) {
                                // The one to aim for, pulsing.
                                drawCircle(primary.copy(alpha = 0.5f * (1f - pulse)), radius = icon * (0.6f + 0.5f * pulse), center = p)
                            }
                            if (sel) drawCircle(Color(0x59B9A8FF), radius = icon * 0.95f, center = p)
                            drawCircle(Color.White.copy(alpha = 0.92f), radius = icon * (if (sel) 0.62f else 0.5f), center = p)
                            emoji.textSize = icon * (if (sel) 0.72f else 0.58f)
                            drawIntoCanvas {
                                it.nativeCanvas.drawText(e, p.x, p.y - (emoji.descent() + emoji.ascent()) / 2f, emoji)
                            }
                        }
                    } else {
                        // Pulsing ring around the bubble: press here.
                        drawCircle(primary.copy(alpha = 0.45f * (1f - pulse)), radius = bubbleR * (1.1f + 0.8f * pulse), center = c)
                    }
                    renderer.character = character
                    renderer.palette = palette
                    renderer.customImage = customImage
                    renderer.mood = when {
                        step == TutorialStep.SLIDE -> BubbleRenderer.Mood.HAPPY
                        touchingBubble -> BubbleRenderer.Mood.PRESSED
                        else -> BubbleRenderer.Mood.NORMAL
                    }
                    drawIntoCanvas { renderer.draw(it.nativeCanvas, c.x, c.y, bubbleR) }
                }
            }
            Spacer(Modifier.height(with(density) { 8.dp }))
        }
    }
}

/** The practice dial faces right from the left edge, like the real one. */
private fun slotAngle(i: Int): Float {
    val span = 150f
    return -span / 2f + span * (i + 0.5f) / PRACTICE_APPS.size
}

@Composable
private fun TipRow(icon: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Home card shown until the tutorial has been done once. */
@Composable
fun TutorialCard(onStart: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.tut_card_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.tut_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart) { Text(stringResource(R.string.tut_card_start)) }
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tut_card_later)) }
            }
        }
    }
}

/** True when the app is showing its light colour scheme. */
@Composable
private fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f
