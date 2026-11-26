package com.yogeshpaliyal.halo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.R

/**
 * Line icons for the settings rows and their menus. The app only ships the core Material icon
 * set, so these are drawn from 24-unit stroke paths instead of pulling in the extended set.
 */
object SettingIcons {
    private fun circle(x: Float, y: Float, r: Float) =
        "M${x - r} ${y}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Hold by lazy {
        icon("hold", "M9 11V5.5a1.5 1.5 0 0 1 3 0V10m0-1.5a1.5 1.5 0 0 1 3 0V11m0-1a1.5 1.5 0 0 1 3 0v4a6 6 0 0 1-6 6h-1a5 5 0 0 1-4-2l-3-4a1.5 1.5 0 0 1 2.3-1.9L9 14")
    }
    val Drag by lazy { icon("drag", "M12 3v18M3 12h18M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3") }
    val Tap by lazy { icon("tap", circle(12f, 12f, 2.5f) + "M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21") }
    val DoubleTap by lazy { icon("double_tap", circle(12f, 12f, 2.5f) + circle(12f, 12f, 6.5f) + "M12 2v1M12 21v1M2 12h1M21 12h1") }
    val Timer by lazy { icon("timer", circle(12f, 13f, 8f) + "M12 9v4l2.5 2M10 2h4") }
    val Haptics by lazy {
        icon("haptics", "M9 4h6a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM4 9v6M20 9v6M1.5 11v2M22.5 11v2")
    }
    val Brightness by lazy {
        icon("brightness", circle(12f, 12f, 4f) + "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    }
    val Volume by lazy { icon("volume", "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a3.5 3.5 0 0 1 0 6M18 6.5a7 7 0 0 1 0 11") }
    val Swap by lazy { icon("swap", "M7 20V4M3 8l4-4 4 4M17 4v16M13 16l4 4 4-4") }
    val Size by lazy { icon("size", circle(7.5f, 16.5f, 3.5f) + circle(15.5f, 9f, 6f)) }
    val Opacity by lazy { icon("opacity", "M12 3s6 6.4 6 11a6 6 0 0 1-12 0c0-4.6 6-11 6-11zM12 20a6 6 0 0 0 6-6") }
    val Edge by lazy { icon("edge", "M4 4h10M4 20h10M4 4v16M20 3v18" + circle(17f, 12f, 3.5f)) }
    val Fullscreen by lazy { icon("fullscreen", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5") }
    val Apps by lazy { icon("apps", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z") }
    val Dots by lazy { icon("dots", "M6 16v-5a6 6 0 0 1 9-5.2M18 11v5l2 2H4l2-2M10 21h4" + circle(18.5f, 5.5f, 2.5f)) }
    val Sparkle by lazy { icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z") }
    val Mood by lazy { icon("mood", circle(12f, 12f, 9f) + "M8.5 14.5a4 4 0 0 0 7 0M9 9.5v.5M15 9.5v.5") }
    val Previous by lazy { icon("previous", "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11") }
    val BeforeLast by lazy { icon("before_last", "M11 17l-5-5 5-5M18 17l-5-5 5-5") }
    val Menu by lazy { icon("menu", circle(12f, 12f, 8.5f) + circle(12f, 12f, 2.5f)) }
    val NoAction by lazy { icon("no_action", circle(12f, 12f, 9f) + "M5.6 5.6l12.8 12.8") }
    val Bolt by lazy { icon("bolt", "M13 2L4.5 13.5H11L10 22l8.5-11.5H12z") }
    val Hourglass by lazy { icon("hourglass", "M7 3h10M7 21h10M8 3v3.5l4 5.5-4 5.5V21M16 3v3.5L12 12l4 5.5V21") }
    val DotSmall by lazy { icon("dot_small", circle(12f, 12f, 3.5f)) }
    val DotMedium by lazy { icon("dot_medium", circle(12f, 12f, 6f)) }
    val DotLarge by lazy { icon("dot_large", circle(12f, 12f, 9f)) }
}

/** Start padding that lines content up with a row's text, past its icon. */
val RowTextIndent = 74.dp

/** One named group of settings: a small coloured header, then its rows, in a card of its own. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            )
            content()
        }
    }
}

/** The round tonal icon at the start of a setting row. */
@Composable
fun RowIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Icon, title and a line of detail, with [trailing] at the end. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** An on/off setting; the whole row toggles it. */
@Composable
fun SwitchRow(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        icon,
        title,
        body,
        Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A setting that opens a picker or another screen. */
@Composable
fun ActionRow(icon: ImageVector, title: String, body: String, enabled: Boolean = true, onClick: () -> Unit) {
    SettingRow(
        icon,
        title,
        body,
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/** A short how-to that isn't a setting, such as "Drag: moves the bubble". */
@Composable
fun HintCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A setting row that shows its current value in a pill and opens a rounded menu of options. */
@Composable
fun <T> ChoiceRow(
    icon: ImageVector,
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    detail: (T) -> String,
    optionIcon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            icon,
            title,
            detail(value),
            Modifier.clickable(role = Role.DropdownList) { open = true },
        ) {
            Row(
                Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    label(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (open) 180f else 0f),
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(x = 64.dp, y = 0.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            options.forEach { option ->
                val selected = option == value
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(option), style = MaterialTheme.typography.titleSmall)
                            Text(
                                detail(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            optionIcon(option),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                        ),
                )
            }
        }
    }
}
