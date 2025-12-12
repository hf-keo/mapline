# Mapline

A lightweight Android Compose app that overlays a customizable straight guide line on an OpenStreetMap view so you can see where you're heading. The app uses the free [osmdroid](https://github.com/osmdroid/osmdroid) library and OpenStreetMap tiles, so no API key is required.

It now also ships a browser-based option (Leaflet + OpenStreetMap) so you can open the map from any phone with location and orientation sensors—no APK install required.

## Features
- OpenStreetMap base map with pinch/zoom and compass.
- Location overlay with optional follow mode.
- Adjustable heading (0–360°), line length, and line color.
- One-tap refresh to redraw the guide line from your current location or the map center.
- Web map variant that reads device geolocation + orientation (when granted) to draw the same heading line in the browser.

## Building and installing
1. Ensure you have Android Studio (Ladybug or later) or Android command-line tools installed.
2. Open this folder in Android Studio, let it sync, and build the `app` module.
3. Connect your Android device with USB debugging enabled, then run **Run > Run 'app'** to install the debug APK.

To build from the command line (requires the Android SDK in your `PATH`):

```bash
gradle assembleDebug
```

The resulting APK will be in `app/build/outputs/apk/debug/`.

## Web version (no install)
1. Open `web/index.html` directly in a mobile browser **or** serve the repo locally:
   ```bash
   python -m http.server 8000
   ```
   then visit `http://localhost:8000/web/`.
2. Allow location access when prompted. If your device supports it, allow motion/orientation access for automatic heading.
3. Adjust heading, line length, color, and follow-mode with the on-page controls and tap **Refresh line** to snap to the latest position.
