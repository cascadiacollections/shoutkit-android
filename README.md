# Launcher icon previews

Review assets for the launcher icon PR (`feat/lighthouse-launcher-icon`). Not meant to be merged.

- `ios-vs-android.png`: the iOS Squan-AppIcon beacon (left) next to the Android redraw (right)
- `adaptive-masks.png`: square, circle and squircle masks, themed icon (dark and light), safe-zone overlay (red = 66dp, yellow = 72dp)
- `legacy-and-play.png`: legacy xxxhdpi and mdpi mipmaps (square and round) and the 512px Play/F-Droid icon
- `tv-banner-and-qs-tile.png`: the TV banner and the quick-settings tile glyph
- `gen_launcher_icon.py`: writes the vector drawables (`python3 gen_launcher_icon.py app/src/main/res`)
- `render_previews.sh`: renders these previews and the legacy webp/Play PNGs (rsvg-convert + ImageMagick)
