# bitMaps

bitMaps. For bitDroid.

Maps and turn-by-turn navigation for bitDroid. Kotlin + Jetpack Compose, dark and gradient, and **free forever**:
no API keys, no billing, no Google Play Services.

## Features

- 🗺️ Dark map (MapLibre + OpenFreeMap vector tiles, recolored to bitDroid's palette)
- 🔎 Search places and addresses, quick categories (☕ Coffee, 🍽️ Food, ⛽ Gas, 🅿️ Parking, 🛒 Groceries, 🔌 Charging)
- 📍 Long-press the map to drop a pin; opens `geo:` links from other apps
- 🚗🚲🚶 Routes for driving, biking and walking, with alternatives
- 🗣️ Spoken turn-by-turn directions with the phone's own text-to-speech (works offline, ducks your music)
- 🔁 Automatic rerouting when you miss a turn
- 🎵 Now playing (song, artist, art, controls) built into the turn card, from any music app
- ⚙️ Settings: voice, keep screen on, 3D view, avoid highways/tolls/ferries, music toggles, units, your own servers

## Free services it uses

| Part | Service |
|---|---|
| Map tiles | [OpenFreeMap](https://openfreemap.org) |
| Search | [Photon](https://photon.komoot.io) (OpenStreetMap) |
| Routing | [Valhalla](https://github.com/valhalla/valhalla) FOSSGIS community server |
| Voice | Android text-to-speech, on the phone |

Search and routing servers can be changed in **Settings → Servers** (both are open source and self-hostable).
Map data © OpenStreetMap contributors.

## Install

Grab `bitMaps-x.y.z-release.apk` from [Releases](../../releases), install it, allow location.
For the music card, allow **notification access** in Settings → Music (bitMaps only reads media sessions, never your notifications).

## Build

CI builds every push; a tag like `v0.1.0` publishes a release with `bitMaps-0.1.0-release.apk`.
The tag must match `versionName` in `app/build.gradle.kts`.

Release signing (optional) uses these repo secrets, otherwise the release APK is debug-signed:
`BITMAPS_KEYSTORE_B64`, `BITMAPS_KEYSTORE_PASSWORD`, `BITMAPS_KEY_ALIAS`, `BITMAPS_KEY_PASSWORD`.

## Shipping in bitDroid

See `rom/`: an `Android.bp` prebuilt module, default location permissions, and an overlay that grants
notification access on first boot.

Made by Alex Desrosier.
