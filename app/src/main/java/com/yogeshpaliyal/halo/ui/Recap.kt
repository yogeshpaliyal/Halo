package com.yogeshpaliyal.halo.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.yogeshpaliyal.halo.R
import com.yogeshpaliyal.halo.data.AppEntry
import com.yogeshpaliyal.halo.data.AppRepository
import com.yogeshpaliyal.halo.data.Stats
import com.yogeshpaliyal.halo.overlay.Accessory
import com.yogeshpaliyal.halo.overlay.BubbleCharacter
import com.yogeshpaliyal.halo.overlay.BubblePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Bold gradient per card, white text on all of them. */
internal val RECAP_GRADIENTS = listOf(
    0xFF6A3DE8 to 0xFFE0459B,
    0xFFFF7A45 to 0xFFFF3D7F,
    0xFF00B4D8 to 0xFF3A0CA3,
    0xFF1A1446 to 0xFF3D2C8D,
    0xFF0F9B8E to 0xFF1B5E20,
    0xFF232526 to 0xFF414345,
)

/** Rhythm persona from the busiest hour. */
internal fun rhythmRes(hour: Int): Pair<Int, String> = when (hour) {
    in 5..10 -> R.string.recap_rhythm_early to "🐦"
    in 11..16 -> R.string.recap_rhythm_day to "☀️"
    in 17..21 -> R.string.recap_rhythm_evening to "🌆"
    else -> R.string.recap_rhythm_night to "🦉"
}

/** Style persona from the gesture used most. */
internal fun styleRes(kinds: IntArray): Pair<Int, String> = when (kinds.indices.maxByOrNull { kinds[it] }) {
    Stats.Kind.TAP.ordinal -> R.string.recap_style_tap to "⚡"
    Stats.Kind.DOUBLE.ordinal -> R.string.recap_style_double to "✌️"
    else -> R.string.recap_style_dial to "🎯"
}

internal fun hourLabel(context: Context, hour: Int): String =
    android.text.format.DateFormat.getTimeFormat(context).format(
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
        }.time,
    )

/**
 * Wrapped-style recap: swipeable cards for the last 7 or 30 days, then a share card.
 * Everything is read from the local tally in [Stats].
 */
@Composable
fun RecapDialog(
    character: BubbleCharacter,
    palette: BubblePalette,
    customImage: android.graphics.Bitmap?,
    accessory: Accessory,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(7) }
    var summary by remember { mutableStateOf<Stats.Summary?>(null) }
    var entries by remember { mutableStateOf<Map<String, AppEntry>>(emptyMap()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(days, reloadTick) {
        summary = null
        val (s, e) = withContext(Dispatchers.IO) {
            val s = Stats.summary(context, days)
            val repo = AppRepository(context)
            val keys = s.topApps.map { it.first } + listOfNotNull(s.topPair?.first, s.topPair?.second)
            s to keys.distinct().mapNotNull { k -> repo.entryFor(k)?.let { k to it } }.toMap()
        }
        entries = e
        summary = s
    }

    FullScreenDialog(onDismiss = onClose, lightContent = true) {
        val s = summary
        val pages = 6
        val pager = rememberPagerState { pages }
        val (g1, g2) = RECAP_GRADIENTS[pager.currentPage.coerceIn(0, RECAP_GRADIENTS.lastIndex)]
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(g1), Color(g2)))),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Progress bars like stories (only when there are cards to swipe), then range and close.
                val hasCards = s != null && s.total >= 3
                Row(Modifier.fillMaxWidth().height(23.dp).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hasCards) repeat(pages) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RangeChip(stringResource(R.string.recap_week), days == 7) { days = 7 }
                    Spacer(Modifier.width(8.dp))
                    RangeChip(stringResource(R.string.recap_month), days == 30) { days = 30 }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(stringResource(R.string.recap_close), color = Color.White) }
                }

                if (s == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                    return@Column
                }
                if (s.total < 3) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BubblePreview(character, palette, Modifier.size(120.dp), live = true, customImage = customImage, accessory = accessory)
                        Spacer(Modifier.height(20.dp))
                        Big(stringResource(R.string.recap_empty_title))
                        Spacer(Modifier.height(8.dp))
                        Small(stringResource(R.string.recap_empty_body))
                    }
                    return@Column
                }

                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (page) {
                            0 -> TotalPage(s, character, palette, customImage, accessory)
                            1 -> TopAppsPage(s, entries)
                            2 -> DuoPage(s, entries)
                            3 -> RhythmPage(s)
                            4 -> StylePage(s)
                            else -> SharePage(s, entries, character, palette, customImage, accessory) {
                                scope.launch {
                                    Stats.reset(context)
                                    reloadTick++
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun Big(text: String) = Text(text, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Small(text: String) = Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 22.sp)

@Composable
private fun TotalPage(s: Stats.Summary, character: BubbleCharacter, palette: BubblePalette, customImage: android.graphics.Bitmap?, accessory: Accessory) {
    val count by animateIntAsState(s.total, animationSpec = tween(1200), label = "count")
    BubblePreview(character, palette, Modifier.size(110.dp), live = true, customImage = customImage, accessory = accessory)
    Spacer(Modifier.height(24.dp))
    Small(stringResource(if (s.days == 7) R.string.recap_total_week else R.string.recap_total_month))
    Text("$count", color = Color.White, fontSize = 84.sp, lineHeight = 90.sp, fontWeight = FontWeight.Black)
    Big(stringResource(R.string.recap_total_unit))
    Spacer(Modifier.height(12.dp))
    Small(plural(R.plurals.recap_active_days, s.activeDays))
    Spacer(Modifier.height(24.dp))
    // Little bar per day.
    val max = (s.perDay.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val n = s.perDay.size
        val gap = size.width * 0.012f
        val w = (size.width - gap * (n - 1)) / n
        s.perDay.forEachIndexed { i, (_, v) ->
            val h = (size.height * v / max).coerceAtLeast(3f)
            drawRoundRect(
                Color.White.copy(alpha = if (v == max) 1f else 0.55f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3f),
            )
        }
    }
}

@Composable
private fun TopAppsPage(s: Stats.Summary, entries: Map<String, AppEntry>) {
    Big(stringResource(R.string.recap_top_title))
    Spacer(Modifier.height(24.dp))
    val max = (s.topApps.firstOrNull()?.second ?: 1).coerceAtLeast(1)
    s.topApps.forEachIndexed { i, (key, count) ->
        val app = entries[key] ?: return@forEachIndexed
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            AppIcon(app, if (i == 0) 52.dp else 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontSize = if (i == 0) 20.sp else 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(count / max.toFloat())
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("$count", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DuoPage(s: Stats.Summary, entries: Map<String, AppEntry>) {
    val pair = s.topPair
    val a = pair?.let { entries[it.first] }
    val b = pair?.let { entries[it.second] }
    Big(stringResource(R.string.recap_duo_title))
    Spacer(Modifier.height(32.dp))
    if (a == null || b == null) {
        Small(stringResource(R.string.recap_duo_none))
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a, 84.dp)
        Text("  ⇄  ", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        AppIcon(b, 84.dp)
    }
    Spacer(Modifier.height(20.dp))
    Text("${a.label} + ${b.label}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Small(plural(R.plurals.recap_duo_count, pair.third))
}

@Composable
private fun RhythmPage(s: Stats.Summary) {
    val context = LocalContext.current
    val (res, emoji) = rhythmRes(s.peakHour)
    Text(emoji, fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Big(stringResource(res))
    Spacer(Modifier.height(8.dp))
    Small(stringResource(R.string.recap_peak_hour, hourLabel(context, s.peakHour)))
    Spacer(Modifier.height(28.dp))
    val max = (s.hours.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = size.width * 0.008f
        val w = (size.width - gap * 23) / 24
        s.hours.forEachIndexed { i, v ->
            val h = (size.height * v / max).coerceAtLeast(3f)
            drawRoundRect(
                if (i == s.peakHour) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3f),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        listOf(0, 6, 12, 18).forEach { h ->
            Text(hourLabel(context, h), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StylePage(s: Stats.Summary) {
    val (res, emoji) = styleRes(s.kinds)
    Text(emoji, fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Big(stringResource(res))
    Spacer(Modifier.height(24.dp))
    val total = s.kinds.sum().coerceAtLeast(1)
    listOf(
        R.string.recap_kind_dial to s.kinds[0],
        R.string.recap_kind_tap to s.kinds[1],
        R.string.recap_kind_double to s.kinds[2],
    ).forEach { (label, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), color = Color.White, fontSize = 16.sp, modifier = Modifier.width(110.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(v / total.toFloat())
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            Text("  ${v * 100 / total}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (s.streak >= 2) {
        Spacer(Modifier.height(28.dp))
        Big("🔥 " + plural(R.plurals.recap_streak, s.streak))
    }
}

@Composable
private fun ColumnScope.SharePage(
    s: Stats.Summary,
    entries: Map<String, AppEntry>,
    character: BubbleCharacter,
    palette: BubblePalette,
    customImage: android.graphics.Bitmap?,
    accessory: Accessory,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val card = remember(s, entries) {
        RecapShareCard.render(context, s, entries, character, palette, customImage, accessory)
    }
    Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            card.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { share(context, card) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.recap_share), fontWeight = FontWeight.Bold) }
    TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.recap_reset), color = Color.White.copy(alpha = 0.7f))
    }
    Small(stringResource(R.string.recap_private))
}

/** Saves the card to Halo's cache and opens the share sheet. */
private fun share(context: Context, card: android.graphics.Bitmap) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "halo-recap.png")
        file.outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.recap_share_text))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
