# How the Tesla UI prototype's design works

A study of [ryanzeeee.github.io/Tesla-UI-prototype](https://ryanzeeee.github.io/Tesla-UI-prototype/),
done with REA (Reverse Engineer Anything) 6.9.0, and what TLauncher takes from it.

The prototype has **no licence file**, so TLauncher reuses none of its code, icons or images.
It only measures timings, colours and layout, then implements its own versions in Compose.

## How the evidence was collected

| | |
|---|---|
| Target | `github.com/ryanzeeee/Tesla-UI-prototype`, commit `10c3bbd`. GitHub Pages builds the live site from this commit with `npm run build -- --base=/Tesla-UI-prototype/` (`.github/workflows`). |
| REA run | `rea analyze-javascript-application` on the app sources (`src/`, `index.html`, `package.json`), with `node_modules` left out. |
| REA Evidence | Parent `ev_f1b7530532b0fd67c7193c4456fe2b5201c49e90a1402e2b8a0df354e0180ad3`, summary `ev_825486ad…52a5c`. The input tree digest is `a445c785…6da`. |
| Coverage | 54 of 54 JS/TS files parsed (43,307 AST nodes, no parse failures). Graph coverage is *partial*: semantic unknowns remain, so a missing fact is not proof something is absent. |
| Not done | **No runtime capture.** This environment's proxy blocks the live site (HTTP 403), and running the prototype's own build was not allowed. Every finding below is static: it comes from source and CSS as written, not from watching the page run. |

The style sheets are not part of REA's JavaScript graph. REA found them as `static-import` edges,
such as `./index.css`, `../navigation/navigation.css`, `./settings.css`, `./seats.css`,
`./climate.css` and `../vehicle/vehicle.css`. The CSS values quoted below were then read from those files.

## Findings

### 1. No animation library: CSS for UI, a hand-written frame loop for layout

- **Observed (REA):** 108 import specifiers were recovered. None is `framer-motion`, even though
  `package.json` lists it as a dependency. The UI libraries actually imported are `react`,
  `react-dom`, `clsx`, `tailwind-merge` and `three` (plus `three/addons` for GLTF/Draco loading,
  `Reflector`, `UnrealBloomPass` and `EffectComposer`).
- **Observed (REA):** `requestAnimationFrame` loops appear at `src/App.tsx:88,91`,
  `src/components/Navigate.tsx:147`, `src/vehicle/createVehicleScene.ts:145`,
  `src/seats/createSeatScene.ts:124` and `src/climate/createCabinScene.ts:106`.
  Each has a matching `cancelAnimationFrame` cleanup.
- **Inference:** everything that isn't 3D uses CSS `transition` or `@keyframes`, timed by tokens.
  Layout motion is tweened in JavaScript, one frame at a time.

### 2. Design tokens (`src/tokens.css:3-57`)

| Token | Value |
|---|---|
| Surfaces | app `#1a1a1a`, sidebar `#0E0E0E`, cards `#161616`, settings `#090909`, control bar `#000`, page `#111` |
| Overlays | toast/map chrome `rgba(0,0,0,.9)`, open button `rgba(255,255,255,.2)` |
| Accents | switch-on blue `#007BE4`, vehicle green `#25CB55` |
| Text | white, muted `rgba(255,255,255,.4)`, secondary `#7f7f7f`, toolbar `rgba(255,255,255,.6)` |
| Type | Inter; 20 / 18 / 24 px; tabular figures on the dashboard (`dashboard.css:8`) |
| Radii | frame 28, inner 12, button 30, toast 24, nav item 10, small icon 6 |
| Durations | fast 150, normal 200, medium 300, slow 500, card 600 ms; easing `ease-out` |

### 3. Signature motion: the car panel resizes, and the map follows

`src/App.tsx:64-93` (the frame loop REA located):

- The car panel's width animates from 580 to 1920 px (`VIZ_NORMAL` → `VIZ_FULL`) over **500 ms**
  with `easeOutCubic(t) = 1 − (1 − t)³` (`App.tsx:20-25`).
- The map width runs the same tween, but **starts 200 ms later** (`mapDelay`), so the map trails the panel edge.
- Inside the panel (`Sidebar.tsx:24-29`), `progress = (w − 580) / 1340` drives everything.
  The speed and info blocks shift by `(w − 580) / 2` to stay centred, the bottom cards slide
  toward the centre, and the Navigate card fades in with `opacity: progress` (`Sidebar.tsx:102`).
- The 3D car follows the same `progress`: yaw goes from 180° to −48° and elevation from 74° to 3°
  (`createVehicleScene.ts:24-25,125-126`). After the user orbits it, a 5 s timer turns it back
  (`RESET_DELAY_MS`, `createVehicleScene.ts:28,164`).

### 4. Press and state feedback (`src/dashboard.css`)

| Element | Behaviour | Source |
|---|---|---|
| Any dashboard button | `scale(.9)` while pressed, 150 ms ease-out; disabled at 30 % opacity | `dashboard.css:21-24` |
| Dock button | `scale(.91)`, `#ffffff16` wash, `brightness(1.15)` on hover, 140 ms | `dashboard.css:194-196` |
| Dock active state | `#c5d8ee12` tint plus an 18 × 3 px `#b9cbdd` underline; heater and defrost use amber `#efa15f` | `dashboard.css:198-204` |
| Dock feedback toast | rises 6 px in 160 ms, `#252a30f2`, hides after 1.8 s | `dashboard.css:205-206`, `ControlBar.tsx:48` (REA timer) |
| Card carousel | `translateX(−i × 580)`, 600 ms ease-out; dots change colour over 300 ms | `Sidebar.tsx:73,76`, `dashboard.css:127-128` |
| Navigate card row | press: `#b4cce824` wash and content `scale(.96)`, 160 ms | `dashboard.css:95-99` |
| Settings toggle | 88 × 44 track, knob slides 44 px in 150 ms | `settings/settings.css:21-24` |

### 5. Panels and overlays

| Surface | Entrance | Source |
|---|---|---|
| Music drawer | `#22252b`, 20 px radius, rises 18 px and fades in, 220 ms; backdrop `#080b1066` | `dashboard.css:139-140,180` |
| Volume popover | `#252930`, rises 8 px, 180 ms | `dashboard.css:209,218` |
| Full music window | `#1d2128`, 24 px radius, rises 44 px, 220 ms | `dashboard.css:220,252` |
| App launcher toast | `translateY(20px)` → 0 plus fade, 150 ms | `AppToast.tsx:17-19` |
| Settings sheet | slides up from `translateY(100%)`, 200 ms | `Settings.tsx:41-42` |
| Navigation panel and turn card | drops in from −9 px at 98.5 % scale, 230 ms `cubic-bezier(.2,.75,.25,1)` | `navigation/navigation.css:48,104,136` |
| Map camera | `transform` 480 ms `cubic-bezier(.2,.72,.25,1)`; drags commit once per frame | `navigation.css:7`, `Navigate.tsx:140-151` |
| Route line | draws itself in by animating the stroke dash, 550 ms ease-out | `navigation.css:25,137` |

Every animated stylesheet has a `prefers-reduced-motion: reduce` block that turns the motion off
(for example `dashboard.css:129,182,207,253` and `settings.css:95`).

## What TLauncher already had

The earlier restyle (`21a2b85`) carried over the night palette, press scaling (90 % / 97 %),
panels that rise 18 dp in 220 ms, the dock's active tint and underline, the playing meter and the
map buttons. That is items 2, 4 and 5 above. Compose also follows Android's animation-scale
setting, which does the job of the prototype's reduced-motion blocks.

## What this change adds

1. **Panel edge sweep (finding 3).** Switching between car and car + map now animates the
   panel's edge over 500 ms with ease-out cubic (`TMotion.split()`). The map trails it by 200 ms
   (`TMotion.MAP_LAG_MS`). Unlike the prototype, TLauncher **does not resize anything per frame**:
   the layout still switches at once, and the map stays laid out at its split size. Only a
   draw-phase clip of the panel (background and content) moves. This avoids the head-unit jank
   that made the switch instant before. The speed readout and car still use their existing
   FLIP-style glide.
2. **Card pager dots (finding 4).** The dots fade between states over 300 ms, and tapping a dot
   slides to that card in 600 ms ease-out, like the prototype's pagination buttons.
3. **Motion tokens.** `TMotion.EaseOutCubic`, `CssEaseOut`, `split()`, `carousel()` and `dot()`
   hold the prototype's measured curves, so later work can reuse them.

The resting screens are unchanged, so the existing Paparazzi screenshots still apply.

## Not adopted, and why

- **The 3D three.js car, seats and cabin.** TLauncher shows photos of the real Avanza. A WebGL
  scene isn't worth its cost on a head unit.
- **The prototype's SVG icons and artwork.** They have no licence. TLauncher keeps its own icons (`TIcons.kt`).
- **Climate, seat and defrost controls.** The Avanza's climate isn't connected to the head unit,
  so there is nothing for these to control.
