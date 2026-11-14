package com.yogeshpaliyal.halo.overlay

import androidx.annotation.StringRes
import com.yogeshpaliyal.halo.R

/** How the radial menu looks. */
enum class DialTheme(@StringRes val labelRes: Int) {
    CLASSIC(R.string.dial_classic),
    MATCH(R.string.dial_match),
    GLASS(R.string.dial_glass),
    NEON(R.string.dial_neon),
    DAYLIGHT(R.string.dial_daylight),
}

/**
 * The colours one theme resolves to. Shared by the real dial and the previews in settings.
 * [edgeOuter]/[edgeInner] are thin lines along the band's edges (0 = none); [edgeGlow] makes
 * them glow; [blur] asks Android to blur the screen behind the dial where supported.
 */
data class DialColors(
    val scrim: Int,
    val scrimAlpha: Int,
    val band: Int,
    val edgeOuter: Int = 0,
    val edgeInner: Int = 0,
    val edgeGlow: Boolean = false,
    val glow: Int,
    val pill: Int,
    val pillText: Int,
    val hintText: Int,
    val blur: Boolean = false,
) {
    companion object {
        fun of(theme: DialTheme, palette: BubblePalette): DialColors = when (theme) {
            DialTheme.CLASSIC -> DialColors(
                scrim = 0xFF000000.toInt(), scrimAlpha = 175,
                band = 0xE61E1B2E.toInt(),
                glow = 0x59B9A8FF,
                pill = 0xF2F1EDFF.toInt(), pillText = 0xFF14121F.toInt(), hintText = 0xFFFFFFFF.toInt(),
            )
            DialTheme.MATCH -> DialColors(
                scrim = 0xFF000000.toInt(), scrimAlpha = 160,
                // The bubble's own gradient ends, lightly see-through, so icons still read.
                band = withAlpha(palette.bottom, 0xEB),
                edgeOuter = withAlpha(palette.top, 0xCC),
                glow = withAlpha(palette.top, 0x80),
                pill = palette.top, pillText = palette.face, hintText = 0xFFFFFFFF.toInt(),
            )
            DialTheme.GLASS -> DialColors(
                scrim = 0xFF000000.toInt(), scrimAlpha = 70,
                band = 0x4DFFFFFF,
                edgeOuter = 0x99FFFFFF.toInt(), edgeInner = 0x66FFFFFF,
                glow = 0x66FFFFFF,
                pill = 0xEBFFFFFF.toInt(), pillText = 0xFF14121F.toInt(), hintText = 0xFFFFFFFF.toInt(),
                blur = true,
            )
            DialTheme.NEON -> DialColors(
                scrim = 0xFF05050A.toInt(), scrimAlpha = 215,
                band = 0xF00B0B14.toInt(),
                edgeOuter = 0xFF00F0FF.toInt(), edgeInner = 0xFFFF2BD6.toInt(), edgeGlow = true,
                glow = 0x6600F0FF,
                pill = 0xFF00F0FF.toInt(), pillText = 0xFF05050A.toInt(), hintText = 0xFF00F0FF.toInt(),
            )
            DialTheme.DAYLIGHT -> DialColors(
                scrim = 0xFFF4F2FA.toInt(), scrimAlpha = 150,
                band = 0xF7FFFFFF.toInt(),
                edgeOuter = 0x1F000000, edgeInner = 0x14000000,
                glow = 0x402D7FF9,
                pill = 0xFF1B1530.toInt(), pillText = 0xFFFFFFFF.toInt(), hintText = 0xFF1B1530.toInt(),
            )
        }

        private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
