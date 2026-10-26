# Halo — iOS-style launcher for every Android screen

One app for phones, the Galaxy Z Fold (cover + inner screen) and tablets, in portrait or landscape.

- **Phone layout** (phones, Fold cover screen): iPhone style, 4 × 6 grid, Search pill, dock along the bottom (right edge when held sideways)
- **Tablet layout** (tablets, Fold inner screen): iPad style, 5 × 6 grid, dots, dock with recent apps
- In landscape the grid turns sideways (5 × 6 → 6 × 5) so every page keeps the same apps
- Folding/unfolding switches layouts with the Apple "Duo" style frost morph
- Swipe gestures stay the system's; swipe down on the home screen opens notifications (changeable)

## Features
- Home pages with iOS-sized widgets: Clock (analog / digital), Calendar (S/M/L), Batteries
- Real Android widgets from any app (Weather, Google, Gmail…) in Small / Medium / Large
- Swipe down (or tap the Search pill) → Spotlight search: apps, web, Play Store
- Last page = App Library: auto-sorted folders (Suggestions, Recently Added, Social, Entertainment…)
- Long-press an icon → menu (Edit Home Screen, App Info, Remove, Delete App)
- Edit mode: icons jiggle, drag to rearrange, “−” to remove, “+” to add widgets, Wallpaper button
- Home button while on Home → closes overlays / leaves edit mode / jumps to page 1

## Build
Every push to `main` builds a signed APK with GitHub Actions and posts it under **Releases**.
Keep this repo **private** — it contains the signing key (`app/halo-release.jks`), which lets new builds install over old ones.

## Install on the Fold
1. Open the repo's **Releases** page on the phone and download the newest `Halo-N.apk`.
2. Open it and allow “Install unknown apps” for your browser when asked.
3. Settings → Apps → Choose default apps → Home app → **Halo** (or tap “Set” on the banner Halo shows).
