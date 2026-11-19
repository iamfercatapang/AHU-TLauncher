import { NodeIO, getBounds } from '@gltf-transform/core';
import { ALL_EXTENSIONS, KHRMaterialsClearcoat, KHRMaterialsUnlit } from '@gltf-transform/extensions';
import { dedup, weld, simplifyPrimitive, prune, flatten } from '@gltf-transform/functions';
import { MeshoptSimplifier } from 'meshoptimizer';
import { floorPixels, png, FLOOR_SIZE } from './floor.mjs';

// Builds app/src/main/assets/models/avanza.glb from the original Sketchfab download. See README.md.
//   node process.mjs [input.glb] [output.glb] [ratio]
// ratio: share of triangles kept for the parts other than the body paint (default 0.4).
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
const here = dirname(fileURLToPath(import.meta.url));
const [, , inp = resolve(here, 'input/2015_toyota_avanza_g_1.3.glb'),
  out = resolve(here, '../../app/src/main/assets/models/avanza.glb'), ratioArg] = process.argv;
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);
const doc = await io.read(inp);
const root = doc.getRoot();
const scene = root.listScenes()[0];

// 1. Body paint: stable name and a clear coat, so the app can recolour it like real car paint.
const paint = root.listMaterials().find((m) => m.getName() === 'Material.069');
if (!paint) throw new Error('paint material not found');
paint.setName('paint');
const cc = doc.createExtension(KHRMaterialsClearcoat);
paint.setExtension('KHR_materials_clearcoat', cc.createClearcoat().setClearcoatFactor(1).setClearcoatRoughnessFactor(0.03));

// 2. Same size as the prototype's car (4.7 m long), wheels on the floor (y = 0), centred, front facing +Z.
const b0 = getBounds(scene);
const k = 4.7 / (b0.max[2] - b0.min[2]);
const wrap = doc.createNode('avanza');
for (const n of scene.listChildren()) { scene.removeChild(n); wrap.addChild(n); }
scene.addChild(wrap);
wrap.setScale([k, k, k]).setRotation([0, 1, 0, 0]);
let b = getBounds(scene);
wrap.setTranslation([-(b.min[0] + b.max[0]) / 2, -b.min[1], -(b.min[2] + b.max[2]) / 2]);
await doc.transform(flatten());

// 2b. Smooth body: the model's own normals ripple, which the glossy clear coat shows as crumpled panels.
// Only the paint keeps nothing but positions, gets welded into one surface and has its normals recomputed;
// wheels, trim and glass keep their sharp edges.
for (const mesh of root.listMeshes()) for (const prim of mesh.listPrimitives()) {
  if (prim.getMaterial() !== paint) continue;
  for (const sem of prim.listSemantics()) if (sem !== 'POSITION') prim.setAttribute(sem, null);
  // Its triangles don't share corners (each has its own three, a hair apart), so shading can't flow across
  // them: snap corners onto a fine grid (1/100 000 of the part's size) so neighbours become one vertex.
  const pos = prim.getAttribute('POSITION');
  const arr = pos.getArray().slice();
  const mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < arr.length; i++) { mn[i % 3] = Math.min(mn[i % 3], arr[i]); mx[i % 3] = Math.max(mx[i % 3], arr[i]); }
  const grid = Math.hypot(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]) * 1e-5;
  for (let i = 0; i < arr.length; i++) arr[i] = Math.round(arr[i] / grid) * grid;
  prim.setAttribute('POSITION', pos.clone().setArray(arr));
}
await doc.transform(weld());
// Smooth normals: each corner gets the area-weighted average of its triangles' normals.
for (const mesh of root.listMeshes()) for (const prim of mesh.listPrimitives()) {
  if (prim.getMaterial() !== paint) continue;
  const p = prim.getAttribute('POSITION').getArray();
  const idx = prim.getIndices().getArray();
  const n = new Float32Array(p.length);
  for (let t = 0; t < idx.length; t += 3) {
    const [a, b, c] = [idx[t] * 3, idx[t + 1] * 3, idx[t + 2] * 3];
    const ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
    const vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
    const fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx; // length = 2 × area
    for (const k of [a, b, c]) { n[k] += fx; n[k + 1] += fy; n[k + 2] += fz; }
  }
  for (let i = 0; i < n.length; i += 3) {
    const l = Math.hypot(n[i], n[i + 1], n[i + 2]) || 1;
    n[i] /= l; n[i + 1] /= l; n[i + 2] /= l;
  }
  prim.setAttribute('NORMAL', doc.createAccessor().setType('VEC3').setBuffer(root.listBuffers()[0]).setArray(n));
}

// 3. Lighter geometry for head units (the reflection draws the car a second time).
// The body paint keeps every triangle: simplifying bends its large, glossy panels into visible dents.
await doc.transform(dedup(), weld());
await MeshoptSimplifier.ready;
for (const mesh of root.listMeshes()) for (const prim of mesh.listPrimitives()) {
  if (prim.getMaterial() === paint) continue;
  simplifyPrimitive(prim, { simplifier: MeshoptSimplifier, ratio: Number(ratioArg ?? 0.4), error: 0.001 });
}
await doc.transform(prune());
b = getBounds(scene);

// 3b. Lamps get their own materials, so the app can light them when the head unit says the lights are on:
// the headlight units (chrome reflectors and bulbs), the fog lights low in the bumper, and the tail lights.
const lamps = { headlight: null, foglight: null, taillight: null };
function lampOf(n, m) {
  const bb = getBounds(n);
  const cy = (bb.min[1] + bb.max[1]) / 2, cz = (bb.min[2] + bb.max[2]) / 2;
  const [r, g] = m.getBaseColorFactor();
  if (m.getMetallicFactor() >= 0.99 && cz > 1.8 && cy > 0.85 && cy < 1.15) return 'headlight';
  if (m.getMetallicFactor() >= 0.99 && cz > 1.9 && cy > 0.45 && cy < 0.75) return 'foglight';
  if (m.getAlphaMode() === 'OPAQUE' && r > 0.7 && g < 0.1 && cz < -1.9) return 'taillight';
  return null;
}
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  for (const prim of mesh.listPrimitives()) {
    const kind = lampOf(n, prim.getMaterial()); if (!kind) continue;
    lamps[kind] ??= prim.getMaterial().clone().setName(kind);
    prim.setMaterial(lamps[kind]);
  }
}
for (const [kind, m] of Object.entries(lamps)) if (!m) throw new Error('no ' + kind + ' found');

// Of the red tail-light plastic only the high brake light glows; the rest (corner clusters, tailgate bars,
// bumper reflectors) moves to a copy that never glows. The round lamps in the corner clusters glow instead.
const tailOff = lamps.taillight.clone().setName('taillight_off');
const glows = (x, y) => y > 1.6;
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  const m = n.getWorldMatrix();
  for (const prim of [...mesh.listPrimitives()]) {
    if (prim.getMaterial() !== lamps.taillight) continue;
    const pos = prim.getAttribute('POSITION').getArray(), idx = prim.getIndices().getArray();
    const on = [], off = [];
    for (let t = 0; t < idx.length; t += 3) {
      let x = 0, y = 0;
      for (let k = 0; k < 3; k++) {
        const i = idx[t + k] * 3;
        x += (m[0] * pos[i] + m[4] * pos[i + 1] + m[8] * pos[i + 2] + m[12]) / 3;
        y += (m[1] * pos[i] + m[5] * pos[i + 1] + m[9] * pos[i + 2] + m[13]) / 3;
      }
      (glows(x, y) ? on : off).push(idx[t], idx[t + 1], idx[t + 2]);
    }
    const Arr = idx.constructor;
    prim.setIndices(prim.getIndices().clone().setArray(new Arr(on)));
    const dim = prim.clone().setMaterial(tailOff);
    dim.setIndices(prim.getIndices().clone().setArray(new Arr(off)));
    mesh.addPrimitive(dim);
    console.log('tail lights: glowing', on.length / 3, 'unlit', off.length / 3, 'triangles');
  }
}

// The round lamp in each rear corner cluster (a disc and the ring in its middle, white "bulb" parts behind the
// red lens, about 1.0-1.13 m up): they become red tail-light lamps that glow with the lights on.
let bulbs = 0;
const isBulb = (x, y, z) => z < -2.1 && y > 1.0 && y < 1.135 && Math.abs(x) > 0.66 && Math.abs(x) < 0.8;
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  const m = n.getWorldMatrix();
  for (const prim of [...mesh.listPrimitives()]) {
    if (prim.getMaterial()?.getName() !== 'Material.073') continue;
    const pos = prim.getAttribute('POSITION').getArray(), idx = prim.getIndices().getArray();
    const lamp = [], rest = [];
    for (let t = 0; t < idx.length; t += 3) {
      let x = 0, y = 0, z = 0;
      for (let k = 0; k < 3; k++) {
        const i = idx[t + k] * 3;
        x += (m[0] * pos[i] + m[4] * pos[i + 1] + m[8] * pos[i + 2] + m[12]) / 3;
        y += (m[1] * pos[i] + m[5] * pos[i + 1] + m[9] * pos[i + 2] + m[13]) / 3;
        z += (m[2] * pos[i] + m[6] * pos[i + 1] + m[10] * pos[i + 2] + m[14]) / 3;
      }
      (isBulb(x, y, z) ? lamp : rest).push(idx[t], idx[t + 1], idx[t + 2]);
    }
    if (lamp.length === 0) continue;
    const Arr = idx.constructor;
    prim.setIndices(prim.getIndices().clone().setArray(new Arr(rest)));
    const lit = prim.clone().setMaterial(lamps.taillight);
    lit.setIndices(prim.getIndices().clone().setArray(new Arr(lamp)));
    mesh.addPrimitive(lit);
    bulbs += lamp.length / 3;
  }
}
if (bulbs === 0) throw new Error('rear round lamps not found');
console.log('rear round lamps:', bulbs, 'triangles');

// 4. Reflection: the opaque parts again, mirrored under the floor (glass and lights would show through it).
// The reflection is faint (7 %), so it gets its own, much lighter copy of the geometry.
const mirror = doc.createNode('mirror').setScale([1, -1, 1]);
for (const n of scene.listChildren()) {
  const mesh = n.getMesh();
  if (!mesh || mesh.listPrimitives().some((p) => p.getMaterial()?.getAlphaMode() !== 'OPAQUE')) continue;
  const copy = doc.createMesh('mirror_' + mesh.getName());
  for (const prim of mesh.listPrimitives()) {
    const c = prim.clone();
    for (const sem of c.listSemantics()) c.setAttribute(sem, c.getAttribute(sem).clone());
    c.setIndices(c.getIndices().clone());
    copy.addPrimitive(simplifyPrimitive(c, { simplifier: MeshoptSimplifier, ratio: 0.35, error: 0.004 }));
  }
  mirror.addChild(
    doc.createNode('mirror_' + n.getName()).setMesh(copy)
      .setTranslation(n.getTranslation()).setRotation(n.getRotation()).setScale(n.getScale()),
  );
}
scene.addChild(mirror);

// 5. Studio floors (night / day), unlit and blended, drawn over the reflection.
const unlit = doc.createExtension(KHRMaterialsUnlit);
const buffer = root.listBuffers()[0];
const h = FLOOR_SIZE / 2;
const pos = doc.createAccessor().setType('VEC3').setBuffer(buffer).setArray(new Float32Array([-h, 0, -h, h, 0, -h, h, 0, h, -h, 0, h]));
const uv = doc.createAccessor().setType('VEC2').setBuffer(buffer).setArray(new Float32Array([0, 0, 1, 0, 1, 1, 0, 1]));
const nrm = doc.createAccessor().setType('VEC3').setBuffer(buffer).setArray(new Float32Array([0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0]));
const idx = doc.createAccessor().setType('SCALAR').setBuffer(buffer).setArray(new Uint16Array([0, 2, 1, 0, 3, 2]));
// The beams layer sits just above the night floor and is shown only while the lights are on.
for (const [name, kind, y] of [['floor_dark', 'dark', 0], ['floor_light', 'light', 0], ['floor_beams', 'beams', 0.003]]) {
  const N = 512;
  const tex = doc.createTexture(name).setMimeType('image/png').setImage(png(N, floorPixels(N, kind)));
  const mat = doc.createMaterial(name).setBaseColorTexture(tex).setAlphaMode('BLEND')
    .setMetallicFactor(0).setRoughnessFactor(1).setExtension('KHR_materials_unlit', unlit.createUnlit());
  const prim = doc.createPrimitive().setAttribute('POSITION', pos).setAttribute('TEXCOORD_0', uv)
    .setAttribute('NORMAL', nrm).setIndices(idx).setMaterial(mat);
  scene.addChild(doc.createNode(name).setMesh(doc.createMesh(name).addPrimitive(prim)).setTranslation([0, y, 0]));
}

let tris = 0;
for (const n of scene.listChildren()) n.traverse((c) => {
  for (const p of c.getMesh()?.listPrimitives() ?? []) tris += (p.getIndices()?.getCount() ?? 0) / 3;
});
await io.write(out, doc);
console.log('car bounds', b.min.map((x) => x.toFixed(3)), b.max.map((x) => x.toFixed(3)), 'drawn tris incl. mirror', tris, 'materials', root.listMaterials().length);
