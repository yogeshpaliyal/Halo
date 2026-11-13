package com.yogeshpaliyal.halo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette

/** Pages behind the settings list on the home screen. */
enum class Page { HOME, GESTURES, APPEARANCE, HIDING, RECENTS, ABOUT }

/** The fun part, front and centre: your bubble, live, with a way into Appearance. */
@Composable
fun StyleTeaserCard(character: BubbleCharacter, palette: BubblePalette, onCustomise: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.clickable(onClick = onCustomise),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                BubblePreview(character, palette, Modifier.size(54.dp), live = true)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.style_card_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.style_card_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onCustomise) { Text(stringResource(R.string.customise)) }
            }
        }
    }
}

/** One row of the settings list: title, current value, and a chevron or a switch. */
@Composable
fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    checked: Boolean? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = { onClick() })
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A titled group of [SettingsRow]s; put [GroupDivider] between rows. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp))

/** About page: what Halo can't do, privacy, source and version. */
@Composable
fun AboutContent(version: String) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrivateSpaceNote()
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SettingsRow(
                    title = stringResource(R.string.privacy_policy),
                    summary = stringResource(R.string.about_privacy_summary),
                    onClick = { uri.openUri(PRIVACY_POLICY_URL) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    title = stringResource(R.string.about_source),
                    summary = "github.com/yogeshpaliyal/Halo",
                    onClick = { uri.openUri(SOURCE_URL) },
                )
            }
        }
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Small links under the home list. */
@Composable
fun HomeFooter(onAbout: () -> Unit) {
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onAbout) { Text(stringResource(R.string.about_title)) }
        TextButton(onClick = { uri.openUri(PRIVACY_POLICY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
    }
}

const val SOURCE_URL = "https://github.com/yogeshpaliyal/Halo"
