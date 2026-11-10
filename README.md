<p align="center"><img src="docs/social-card.jpg" alt="Halo: the one-thumb app switcher for Android, shown with its home screen and the radial menu open" width="100%"></p>

# Halo

A floating bubble that sticks to the edge of your screen and lets you switch apps with one thumb.

- **Long-press** the bubble → a radial menu opens: your **favourites** on the inner ring, **recent apps** on the outer ring.
- **Slide** to an app (you feel a tick on each one), **let go** to open it. Let go near the bubble to cancel.
- **Tap** the bubble to jump back to your previous app (or set it to open the menu, or do nothing).
- **Drag** the bubble anywhere; it snaps to the nearest side and remembers its position.
- **Configurable**: tap action, hold delay (200 / 280 / 400 ms) and haptics.
- **Quick actions** as favourites: flashlight, volume panel and play/pause. No extra permissions.
- **Hide from recents**: keep any app out of the recent-apps ring.

- **Work profile** apps can be favourites (shown with the briefcase badge). Private space apps can't: Android only exposes them to the default home app.

No ads, no tracking, no internet permission.

## Why I built this

I switch between the same handful of apps all day, and the recents screen is a slow way to do it: a
full-screen detour every time. I wanted a small button on the edge of the screen that I could hold, slide
to an app, and let go.

Pixel phones don't have an edge panel, and the apps I tried for this were closed source, looked dated, or
asked for Accessibility access. That's a lot of trust to give an app I can't read. So I built Halo to do
this one thing: open source, no internet permission, nothing collected.

It's written in Kotlin and Jetpack Compose, with Claude as a pair programmer. I review the changes and use
it every day.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/menu.jpg" width="200" alt="Radial menu open over the home screen: favourites on the inner ring, recent apps on the outer ring"><br><sub>Hold, slide, let go</sub></td>
    <td align="center"><img src="docs/screenshots/bubble.jpg" width="200" alt="The bubble resting on the right edge of the home screen"><br><sub>Stays out of the way</sub></td>
    <td align="center"><img src="docs/screenshots/home.jpg" width="200" alt="Halo home screen with the on switch, a menu preview and six favourites"><br><sub>Pick up to six favourites</sub></td>
    <td align="center"><img src="docs/screenshots/settings.jpg" width="200" alt="Gesture settings: tap action, hold delay and haptics"><br><sub>Tune the gestures</sub></td>
  </tr>
</table>

## Download

Every push to the default branch builds a debug APK and publishes it to the
[**Latest master**](https://github.com/yogeshpaliyal/Halo/releases/tag/latest-master) release:

**[halo-debug.apk](https://github.com/yogeshpaliyal/Halo/releases/download/latest-master/halo-debug.apk)**

These debug builds install as **Halo Debug** (`com.yogeshpaliyal.halo.debug`, amber icon), next to the
Play Store version. They're signed with the committed `keystore/debug.keystore` (password `android`), so
each new build installs over the previous one.

## Releasing

Actions → **Release** → **Run workflow**, then pick `patch`, `minor` or `major`. The workflow bumps
`versionName` and `versionCode` in `app/build.gradle.kts`, builds the signed APK and AAB, and only if
the build succeeds commits the bump to `main`, tags it (`vX.Y.Z`) and publishes a GitHub release with
both files attached. Upload the `.aab` to Play Console.

## Stats

Halo has no analytics in the app. Download counts, stars and repo traffic are collected from
GitHub's APIs by the `Stats snapshot` workflow (daily, and before each `latest-master` rebuild) and
saved to the [`stats` branch](https://github.com/yogeshpaliyal/Halo/tree/stats).

Repo views, clones and referrers need one extra step, because the Actions token can't read
GitHub's traffic API: create a fine-grained token for this repo with **Administration: Read-only**
and add it as the `TRAFFIC_TOKEN` repository secret.

### Verify the APK

Every release APK, and the Play Store version, is signed with the same key. Its certificate SHA-256 is:

```
com.yogeshpaliyal.halo
4D:C1:4F:08:7E:D5:AB:99:63:D1:C1:EF:11:15:EC:3B:33:1E:4B:BD:A5:85:8A:0C:FF:7F:A2:66:73:4A:CD:E5
```

Check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier) (paste both lines) or
`apksigner verify --print-certs halo-vX.Y.Z.apk`. Each release also lists SHA-256 checksums of its files.
Debug builds from `latest-master` use a different, public debug key and will not match.

## Setup

1. Open the project in Android Studio (or run `./gradlew installDebug`), or install the APK above.
2. In the app, grant:
   - **Display over other apps**: draws the bubble.
   - **Usage access**: powers the recent-apps ring. Optional; without it you only get favourites.
   - **Notifications** (Android 13+): a silent notification with a Stop button.
3. Pick up to 6 favourites and turn on **Floating bubble**.

> **“Restricted setting” or a greyed-out switch?** Android 15+ blocks *Display over other apps* and
> *Usage access* for apps installed from a downloaded APK. Open **Settings → Apps → Halo**, tap **⋮**
> (top right), choose **Allow restricted settings**, confirm with your screen lock, then grant the
> permission again. Halo's setup card has an **Open App info** shortcut for this.

## How it works

| Piece | File |
|---|---|
| Foreground service that owns the overlay windows | `overlay/HaloService.kt` |
| Bubble: tap / drag / long-press gesture state machine | `overlay/BubbleView.kt` |
| Radial menu drawing and hit-testing | `overlay/RadialMenuView.kt` |
| Installed apps, favourites, recents (`UsageStatsManager` events), launching | `data/AppRepository.kt` |
| Settings screen (Compose) | `ui/MainActivity.kt` |

The long-press → slide → release gesture works because Android keeps delivering the whole touch stream to the window that got `ACTION_DOWN` (the small bubble window). The menu itself is a separate full-screen, **non-touchable** overlay that just draws what the bubble reports.

## Roadmap ideas

- Start on boot
- Hide automatically in full-screen apps / games
- Bubble size, opacity and long-press delay settings
- Reorder favourites by drag
- App shortcuts (long-press shortcuts) as a third ring

## Privacy

Halo collects no data and has no internet permission. Read the [privacy policy](PRIVACY.md).

## License

MIT
