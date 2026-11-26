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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.data.IconShape
import com.yogeshpaliyal.halo.data.IconShapes

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
        // The same outlines the dial clips icons to; Phone default uses this phone's own mask.
        val path = IconShapes.previewPath(shape, s)
        if (path != null) drawPath(path.asComposePath(), fill) else drawCircle(fill)
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
