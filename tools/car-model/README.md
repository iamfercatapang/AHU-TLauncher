# Car model build

Builds the 3D car shown in the car panel, `app/src/main/assets/models/avanza.glb`, from the original model.
Only needed to change the model (for example a different car, more detail, or other floors); the app uses the
committed `.glb` and doesn't need this to build.

## Source model

"2015 Toyota Avanza G 1.3" by Raysh,
<https://sketchfab.com/3d-models/2015-toyota-avanza-g-13-711adccbd846427b9dbca71e27fee50b>,
licensed CC BY 4.0 (see `licenses/Avanza-3D-model-CC-BY-4.0.txt`). Download the **glTF binary (.glb)** version
and put it in `input/` as `2015_toyota_avanza_g_1.3.glb`. The `input/` folder isn't committed.

The wheels are "Volk Racing CE28 Rims" by MrDogeSuper,
<https://sketchfab.com/3d-models/volk-racing-ce28-rims-a24d5298b8cc44a3aae37fe2864ae2b5>,
licensed CC BY 4.0 (see `licenses/CE28-wheel-3D-model-CC-BY-4.0.txt`). Download its **glTF binary (.glb)** version
into `input/` too, as `volk_racing_ce28_rims.glb`.

The tyres come from "Car wheels and tire" by Márcio Meireles,
<https://sketchfab.com/3d-models/car-wheels-and-tire-ad9408b064944521975c9934cf184408>,
licensed CC BY 4.0 (see `licenses/Tyre-3D-model-CC-BY-4.0.txt`). Download its **glTF binary (.glb)** version
into `input/` as `car_wheels_and_tire.glb`.

## Build

Needs Node.js 20 or newer.

```sh
cd tools/car-model
npm install
npm run build          # or: node process.mjs [input.glb] [output.glb] [ratio]
```

It overwrites `app/src/main/assets/models/avanza.glb`. With the same input and these pinned versions the
output is byte-for-byte the committed file, so `git status` shows no change unless something really changed.

## What it does

`process.mjs` builds the model; `floor.mjs` draws the floor textures; `outline.mjs` prints the outline
points for `CarFraming.kt`. `process.mjs`, in order:

1. **Paint.** The body paint material (`Material.069` in the download) is renamed `paint` and gets a clear
   coat; the app recolours it at runtime (Settings → Display → Car → Paint colour). The brakes get their own
   materials (`BRAKES`): `brake_disc` dark silver metal, `caliper` medium dark red.
2. **Size and place.** Scaled to 4.7 m long (the Tesla UI prototype's car size, so its camera values fit),
   wheels on the floor (y = 0), centred, front facing +Z.
3. **Smooth body.** The paint's own normals ripple, which the glossy clear coat shows as dents, so its
   corners are welded and smooth normals recomputed. The download's body is also crumpled on both rear
   quarter panels just above the wheel arch; those spots (`DENTS`, in metres) are smoothed before the normals
   are computed, leaving the panel gaps and the arch lip in place.
   **Rear bumper**, to match the facelifted Avanza: the openings of the download's rectangular reflectors are
   closed and blended into the bumper, and each bumper corner gets a round, flat-bottomed pocket (`POCKETS`):
   the area is split into smaller triangles that follow the surface's curve, then pressed in.
4. **Lighter geometry.** Every part except the paint is simplified (`ratio`, default 0.4). The paint keeps
   all its triangles: simplifying it bends the large panels.
5. **Wheels.** The stock rims and their centre caps are removed, and a CE28 rim (material `rim`, light matte silver) is
   fitted at each corner: same centre and diameter as the stock rim, outer face flush. The stock tyres are
   replaced too, by the low-poly tyre of "Car wheels and tire" (material `tyre`): its radius is remapped to run
   from the rim to the stock tyre's outside, its width matched, and its textures scaled down to 512 pixels
   (`TYRE_TEX`); its roughness map (0.1, mirror-like) is dropped for matte rubber (roughness 0.85).
   Then the body is lowered `LOWER` (3 cm) over its wheels: everything but the rims, tyres and brakes moves down. Brake discs and calipers stay.
6. **Lamps.** Parts get their own materials so the app can light them with the headlights:
   `headlight`, `foglight`, `taillight` (only the high brake light and the round lamps in the rear clusters)
   and `taillight_off` (the rest of the red plastic, never lit). These are found by position, in metres.
   The rectangular bumper reflectors are cut out, and a round red reflector (9 cm, `taillight_off`, so it never
   lights up) sits in each bumper pocket.
7. **Reflection.** A lighter, mirrored copy of the opaque parts under the floor (`mirror`).
8. **Floors.** Three unlit, blended floor squares with generated textures (`floor.mjs`): `floor_dark` (night
   satin floor with soft shadow), `floor_light` (day) and `floor_beams` (headlight beams, shown only with the
   lights on).
9. **Lane lines.** Two soft blue strips either side of the car (a 3.5 m lane), from 5 m behind it to 45 m
   ahead, fading at both ends (`lanes`, texture from `floor.mjs`). The app shows them in the driving view while
   navigating.

The app looks these parts up by name (`paint`, `headlight`, `foglight`, `taillight`, `mirror`, `floor_dark`,
`floor_light`, `floor_beams`, `lanes`, and the `rim` / `tyre` nodes it turns while driving; see
`ui/home/Car3d.kt`), so keep the names when changing the script.

## After changing the model

- `ui/home/CarFraming.kt` holds the car's outline points (`OUTLINE`, used to fit the car on screen while it
  is orbited). If the car's shape or size changes, run `node outline.mjs` and paste its output into `OUTLINE`.
- Run the app's tests (`./gradlew verifyPaparazziDebug`) and check the car in both views on a device.
