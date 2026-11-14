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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette

/** Pages behind the settings list on the home screen. */
enum class Page { HOME, APPEARANCE }

/** The fun part, front and centre: your bubble, live, with a way into Appearance. */
@Composable
fun StyleTeaserCard(
    character: BubbleCharacter,
    palette: BubblePalette,
    customImage: android.graphics.Bitmap?,
    accessory: com.yogeshpaliyal.halo.overlay.Accessory,
    onCustomise: () -> Unit,
) {
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
                BubblePreview(character, palette, Modifier.size(54.dp), live = true, customImage = customImage, accessory = accessory)
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

/** Home card leading to the recap. */
@Composable
fun RecapCard(switches: Int, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFF6A3DE8), androidx.compose.ui.graphics.Color(0xFFE0459B)),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.recap_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        plural(R.plurals.recap_card_body, switches),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    )
                }
                Text("✨", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

/** A labelled group of options on the home screen: a small title, then its card. */
@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        content()
    }
}

/** Privacy policy and source links, and the version. */
@Composable
fun HomeFooter(version: String, onReplayTutorial: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { uri.openUri(PRIVACY_POLICY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
            TextButton(onClick = { uri.openUri(SOURCE_URL) }) { Text(stringResource(R.string.about_source)) }
            TextButton(onClick = { uri.openUri(COMMUNITY_URL) }) { Text(stringResource(R.string.community)) }
        }
        TextButton(onClick = onReplayTutorial) { Text(stringResource(R.string.tut_replay)) }
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

const val SOURCE_URL = "https://github.com/yogeshpaliyal/Halo"
const val COMMUNITY_URL = "https://github.com/yogeshpaliyal/Halo/discussions"
