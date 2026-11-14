package com.yogeshpaliyal.halo.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yogeshpaliyal.halo.R
import kotlin.math.roundToInt

/**
 * Full-screen crop step after picking a photo: the photo behind a round frame, pinch or the
 * slider to zoom, drag to move, a button to rotate. The frame is always fully covered by the
 * photo, so the result never has empty corners.
 *
 * [onDone] gets the chosen square in the (possibly rotated) bitmap's pixels.
 */
@Composable
fun AvatarCropper(
    source: Bitmap,
    onCancel: () -> Unit,
    onDone: (bitmap: Bitmap, left: Int, top: Int, side: Int) -> Unit,
) {
    var bitmap by remember(source) { mutableStateOf(source) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var view by remember { mutableStateOf(IntSize.Zero) }
    // Zoom: 1 = the photo just covers the frame. Offset: photo centre from frame centre, in px.
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    val frameR = minOf(view.width, view.height) * 0.4f
    val minScale = if (frameR > 0f) 2f * frameR / minOf(bitmap.width, bitmap.height) else 1f
    val scale = minScale * zoom

    /** Keeps the frame inside the photo. */
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (bitmap.width * s / 2f - frameR).coerceAtLeast(0f)
        val maxY = (bitmap.height * s / 2f - frameR).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.crop_title),
                color = Color.White,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.crop_hint),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { view = it }
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            offset = clamp(offset + pan, minScale * zoom)
                        }
                    },
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        // Offscreen layer so the frame can punch a clear hole in the dim.
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset((c.x + offset.x - w / 2f).roundToInt(), (c.y + offset.y - h / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                    drawRect(Color.Black.copy(alpha = 0.6f))
                    drawCircle(Color.Transparent, radius = frameR, center = c, blendMode = BlendMode.Clear)
                    drawCircle(Color.White, radius = frameR, center = c, style = Stroke(2.dp.toPx()))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        offset = clamp(offset, minScale * it)
                    },
                    valueRange = 1f..MAX_ZOOM,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    bitmap = rotate(bitmap)
                }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.crop_rotate),
                        tint = Color.White,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
                Button(
                    enabled = frameR > 0f,
                    onClick = {
                        // The frame's centre and size, back in the photo's own pixels.
                        val side = (2f * frameR / scale).roundToInt()
                        val cx = bitmap.width / 2f - offset.x / scale
                        val cy = bitmap.height / 2f - offset.y / scale
                        onDone(bitmap, (cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt(), side)
                    },
                ) { Text(stringResource(R.string.crop_use)) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private const val MAX_ZOOM = 5f

private fun rotate(b: Bitmap): Bitmap =
    Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f) }, true)
