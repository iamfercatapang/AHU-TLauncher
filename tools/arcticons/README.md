# Minimal app icons

Picks the [Arcticons](https://github.com/Arcticons-Team/Arcticons) line icons for common in-car apps and turns
them into Android vector drawables, for Settings → Home screen → App icons → Minimal. The app uses the committed
output and doesn't need this to build.

The icons are CC BY-SA 4.0 (see `licenses/Arcticons-CC-BY-SA-4.0.txt`); the converted icons are shared under the
same license.

## Run

Needs Node.js 20 or newer and internet access (the first time; downloads are kept in `cache/`, not committed).

```sh
cd tools/arcticons
node build.mjs
```

It writes:

- `app/src/main/res/drawable/arc_<icon>.xml`: one vector per icon (white, tinted by the app), and removes old ones;
- `app/src/main/java/com/ahu/tlauncher/launcher/ArcticonsMap.kt`: which icon each app uses, by component and by
  package.

## Changing it

- **Which apps:** `PACKAGES` (exact package names) and `PREFIXES` (head-unit firmware apps) in `build.mjs`.
- **Arcticons version:** `COMMIT`. Delete `cache/` to download again.
- **Line width:** `STROKE` (Arcticons' own is 1).

Apps without an icon here are shown in one colour: the monochrome layer of their adaptive icon (Android 13+), else
its foreground in one colour, else their icon in grey (`AppRepository.minimalIcon`).
