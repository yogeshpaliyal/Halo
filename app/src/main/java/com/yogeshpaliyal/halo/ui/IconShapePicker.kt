package com.yogeshpaliyal.halo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.data.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** The icon shapes as tiles that show the shape itself, not only its name. */
@Composable
fun IconShapePicker(value: IconShape, onSelect: (IconShape) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconShape.entries.forEach { shape ->
            val selected = shape == value
            val tileShape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tileShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(shape) })
                    .padding(horizontal = 2.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShapePreview(
                    shape,
                    fill = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    glyph = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(shape.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** A filled icon-sized [shape]; "Phone default" gets a small phone drawn on it. */
@Composable
private fun ShapePreview(shape: IconShape, fill: Color, glyph: Color) {
    Canvas(Modifier.size(36.dp)) {
        val s = size.minDimension
        val rect = Rect(0f, 0f, s, s)
        val path = Path()
        when (shape) {
            IconShape.CIRCLE -> path.addOval(rect)
            IconShape.ROUNDED_SQUARE -> path.addRoundRect(RoundRect(rect, CornerRadius(s * 0.22f)))
            IconShape.TEARDROP -> {
                val big = CornerRadius(s / 2)
                path.addRoundRect(RoundRect(rect, big, big, CornerRadius(s * 0.12f), big))
            }
            IconShape.SQUIRCLE -> {
                // Same superellipse the dial uses: |x|^4 + |y|^4 = r^4.
                val r = s / 2
                for (i in 0..96) {
                    val t = 2.0 * Math.PI * i / 96
                    val c = cos(t)
                    val sn = sin(t)
                    val x = r + r * (sign(c) * abs(c).pow(0.5)).toFloat()
                    val y = r + r * (sign(sn) * abs(sn).pow(0.5)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            IconShape.SYSTEM -> path.addRoundRect(RoundRect(rect, CornerRadius(s * 0.3f)))
        }
        drawPath(path, fill)
        if (shape == IconShape.SYSTEM) {
            val w = s * 0.3f
            val h = s * 0.48f
            val stroke = Stroke(width = s * 0.06f, cap = StrokeCap.Round)
            drawRoundRect(
                glyph,
                topLeft = androidx.compose.ui.geometry.Offset((s - w) / 2, (s - h) / 2),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = CornerRadius(s * 0.05f),
                style = stroke,
            )
        }
    }
}
