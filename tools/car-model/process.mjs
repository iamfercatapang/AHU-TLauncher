import { NodeIO, getBounds } from '@gltf-transform/core';
import { ALL_EXTENSIONS, KHRMaterialsClearcoat, KHRMaterialsUnlit } from '@gltf-transform/extensions';
import { dedup, weld, simplifyPrimitive, prune, flatten, transformMesh, copyToDocument, unpartition } from '@gltf-transform/functions';
import { MeshoptSimplifier } from 'meshoptimizer';
import { floorPixels, png, FLOOR_SIZE } from './floor.mjs';

// Builds app/src/main/assets/models/avanza.glb from the original Sketchfab download. See README.md.
//   node process.mjs [input.glb] [output.glb] [ratio]
// ratio: share of triangles kept for the parts other than the body paint (default 0.4).
import { fileURLToPath } from 'node:url';
import { inflateSync } from 'node:zlib';
import { dirname, resolve } from 'node:path';
const here = dirname(fileURLToPath(import.meta.url));
const [, , inp = resolve(here, 'input/2015_toyota_avanza_g_1.3.glb'),
  out = resolve(here, '../../app/src/main/assets/models/avanza.glb'), ratioArg] = process.argv;
const wheelIn = resolve(here, 'input/volk_racing_ce28_rims.glb');
const tyreIn = resolve(here, 'input/car_wheels_and_tire.glb');
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

// 2c. Dents: the paint is crumpled on both rear quarter panels, just above the wheel arch. There the surface is
// relaxed towards its neighbours (Laplacian smoothing), fading out over 0.22 m; open edges (panel gaps, arch
// lip) stay where they are. Positions in metres in the output frame (front +Z, wheels on y = 0).
const DENTS = [[-1.01, 0.75, -1.43], [1.01, 0.75, -1.43]], DENT_R = 0.22, DENT_PASSES = 30;
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  const m = n.getWorldMatrix();
  for (const prim of mesh.listPrimitives()) {
    if (prim.getMaterial() !== paint) continue;
    const posA = prim.getAttribute('POSITION');
    const p = posA.getArray().slice(), idx = prim.getIndices().getArray(), nv = p.length / 3;
    const nb = Array.from({ length: nv }, () => new Set());
    const edges = new Map();
    for (let t = 0; t < idx.length; t += 3) for (let k = 0; k < 3; k++) {
      const a = idx[t + k], c = idx[t + (k + 1) % 3];
      nb[a].add(c); nb[c].add(a);
      const key = a < c ? a * nv + c : c * nv + a;
      edges.set(key, (edges.get(key) ?? 0) + 1);
    }
    const open = new Uint8Array(nv);
    for (const [key, count] of edges) if (count === 1) { open[Math.floor(key / nv)] = 1; open[key % nv] = 1; }
    const w = new Map();
    for (let v = 0; v < nv; v++) {
      if (open[v]) continue;
      const i = v * 3;
      const x = m[0] * p[i] + m[4] * p[i + 1] + m[8] * p[i + 2] + m[12];
      const y = m[1] * p[i] + m[5] * p[i + 1] + m[9] * p[i + 2] + m[13];
      const z = m[2] * p[i] + m[6] * p[i + 1] + m[10] * p[i + 2] + m[14];
      for (const c of DENTS) {
        const d = Math.hypot(x - c[0], y - c[1], z - c[2]);
        if (d < DENT_R) w.set(v, Math.max(w.get(v) ?? 0, 0.5 * (1 + Math.cos(Math.PI * d / DENT_R))));
      }
    }
    for (let pass = 0; pass < DENT_PASSES; pass++) {
      const next = [];
      for (const [v, wt] of w) {
        let sx = 0, sy = 0, sz = 0;
        for (const u of nb[v]) { sx += p[u * 3]; sy += p[u * 3 + 1]; sz += p[u * 3 + 2]; }
        const s = 0.5 * wt / nb[v].size, i = v * 3;
        next.push([i, p[i] + (sx - p[i] * nb[v].size) * s, p[i + 1] + (sy - p[i + 1] * nb[v].size) * s,
          p[i + 2] + (sz - p[i + 2] * nb[v].size) * s]);
      }
      for (const [i, x, y, z] of next) { p[i] = x; p[i + 1] = y; p[i + 2] = z; }
    }
    if (w.size === 0) throw new Error('dent areas not found on the paint');
    console.log('dents: smoothed', w.size, 'vertices');
    prim.setAttribute('POSITION', posA.clone().setArray(p));
  }
}

// 2d. Rear bumper: like the facelifted Avanza, each bumper corner gets a round pocket with a round reflector in
// it (step 3c), instead of the download's flat rectangular reflectors lower down. The body is coarse there, so
// the area is first split into smaller triangles (three times), then pressed in: a flat-bottomed scoop that fades
// out at its rim. POCKETS: reflector centres, in metres in the output frame (rear faces -Z). The pocket is an
// oval (POCKET_RX wide, POCKET_RY tall, its middle POCKET_DROP lower); equal radii make it round.
const POCKETS = [[-0.73, 0.66], [0.73, 0.66]];
const POCKET_RX = 0.085, POCKET_RY = 0.085, POCKET_FLAT = 0.59, POCKET_DEPTH = 0.022, POCKET_DROP = 0;
const POCKET_R = POCKET_RY + POCKET_DROP; // reach from the reflector centre
const pockets = []; // filled below: { centre, normal } of each pocket's floor, for the reflectors
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  for (const prim of mesh.listPrimitives()) {
    if (prim.getMaterial() !== paint) continue;
    const m = n.getWorldMatrix();
    const src = prim.getAttribute('POSITION').getArray();
    const P = []; // world positions
    for (let i = 0; i < src.length; i += 3) P.push([0, 1, 2].map((j) => m[j] * src[i] + m[4 + j] * src[i + 1] + m[8 + j] * src[i + 2] + m[12 + j]));
    let I = Array.from(prim.getIndices().getArray());
    const sub = (a, c) => [a[0] - c[0], a[1] - c[1], a[2] - c[2]];
    const cross = (a, c) => [a[1] * c[2] - a[2] * c[1], a[2] * c[0] - a[0] * c[2], a[0] * c[1] - a[1] * c[0]];
    const norm = (a) => { const l = Math.hypot(...a) || 1; return a.map((x) => x / l); };
    const patches = []; // middles of the closed reflector openings, relaxed below
    const PATCH_R = 0.15;
    // The old reflectors sat in rectangular openings in the bumper skin, with walls about 5 cm deep. The walls go
    // onto the bumper's surface, taken from a smooth fit (z as a quadratic in x and y) of the bumper around each.
    for (const side of [-1, 1]) {
      const inner = (p) => Math.abs(p[0]) > 0.585 && Math.abs(p[0]) < 0.855 && p[1] > 0.44 && p[1] < 0.555;
      const ring = P.filter((p) => Math.sign(p[0]) === side && Math.abs(p[0]) > 0.54 && Math.abs(p[0]) < 0.89 &&
        p[1] > 0.41 && p[1] < 0.585 && p[2] < -2.25 && !inner(p));
      const terms = (x, y) => [1, x, y, x * x, x * y, y * y];
      const A = Array.from({ length: 6 }, () => new Array(7).fill(0));
      for (const p of ring) {
        const f = terms(p[0], p[1]);
        for (let i = 0; i < 6; i++) { for (let j = 0; j < 6; j++) A[i][j] += f[i] * f[j]; A[i][6] += f[i] * p[2]; }
      }
      for (let i = 0; i < 6; i++) { // Gauss-Jordan
        let piv = i; for (let r = i + 1; r < 6; r++) if (Math.abs(A[r][i]) > Math.abs(A[piv][i])) piv = r;
        [A[i], A[piv]] = [A[piv], A[i]];
        for (let r = 0; r < 6; r++) if (r !== i) { const k = A[r][i] / A[i][i]; for (let c = i; c < 7; c++) A[r][c] -= k * A[i][c]; }
      }
      const fit = (x, y) => terms(x, y).reduce((s, f, i) => s + f * A[i][6] / A[i][i], 0);
      // Everything from 1 cm proud of the surface to 8 cm behind it (walls, lip) goes onto the surface.
      const flat = new Set();
      P.forEach((p, v) => {
        if (Math.sign(p[0]) !== side || !inner(p)) return;
        const d = p[2] - fit(p[0], p[1]);
        if (d > -0.01 && d < 0.08) { p[2] = fit(p[0], p[1]); flat.add(v); }
      });
      // Close the opening with a fan from a new middle vertex. Its triangles run the opposite way round each open
      // edge from the triangle already on that edge.
      const edgeUse = new Map();
      for (let t = 0; t < I.length; t += 3) for (let k = 0; k < 3; k++) {
        const a = I[t + k], c = I[t + (k + 1) % 3], key = a < c ? a + ',' + c : c + ',' + a;
        edgeUse.set(key, edgeUse.has(key) ? null : [a, c]);
      }
      const openEdges = [...edgeUse.values()].filter((e) => e && flat.has(e[0]) && flat.has(e[1]));
      if (openEdges.length) {
        const mid = [0, 1, 2].map((j) => openEdges.reduce((s, e) => s + P[e[0]][j], 0) / openEdges.length);
        mid[2] = fit(mid[0], mid[1]);
        const c = P.length; P.push(mid); flat.add(c); patches.push([...mid]);
        for (const [a, b] of openEdges) I.push(b, a, c);
      }
      // Then a few relaxing passes over those vertices and their neighbours, so the patch blends into the bumper.
      const nb = new Map();
      for (let t = 0; t < I.length; t += 3) for (let k = 0; k < 3; k++) {
        const a = I[t + k], c = I[t + (k + 1) % 3];
        if (!nb.has(a)) nb.set(a, new Set()); if (!nb.has(c)) nb.set(c, new Set());
        nb.get(a).add(c); nb.get(c).add(a);
      }
      const band = new Set(flat); for (const v of flat) for (const u of nb.get(v) ?? []) band.add(u);
      for (let pass = 0; pass < 10; pass++) {
        const next = [];
        for (const v of band) {
          const ns = [...(nb.get(v) ?? [])]; if (!ns.length) continue;
          const avg = [0, 1, 2].map((j) => ns.reduce((s, u) => s + P[u][j], 0) / ns.length);
          next.push([v, avg]);
        }
        for (const [v, avg] of next) for (let j = 0; j < 3; j++) P[v][j] += (avg[j] - P[v][j]) * 0.5;
      }
      console.log('old reflector opening closed:', flat.size, 'vertices flattened,', openEdges.length, 'open edges filled');
    }
    // Where each pocket sits: the frontmost-facing hit of a ray from behind the car, along +Z.
    const centres = POCKETS.map(([x, y]) => {
      let best = null;
      for (let t = 0; t < I.length; t += 3) {
        const [a, b, c] = [P[I[t]], P[I[t + 1]], P[I[t + 2]]];
        const d0 = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]); if (Math.abs(d0) < 1e-12) continue;
        const u = ((x - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (y - a[1])) / d0;
        const v = ((b[0] - a[0]) * (y - a[1]) - (x - a[0]) * (b[1] - a[1])) / d0;
        if (u < 0 || v < 0 || u + v > 1) continue;
        const z = a[2] + u * (b[2] - a[2]) + v * (c[2] - a[2]);
        if (z < -1.5 && (!best || z < best[2])) best = [x, y, z];
      }
      if (!best) throw new Error('rear bumper not found at ' + x + ', ' + y);
      return best;
    });
    // Refine: triangles reaching near a pocket (a corner or their middle, so long ones crossing
    // the rim count too) are split in four. A neighbour that gets a new point on one or two of its edges is split
    // to match, so no point sits on another triangle's edge (that would show as a crease in the shading).
    const key = (a, c) => (a < c ? a + ',' + c : c + ',' + a);
    for (let pass = 0; pass < 3; pass++) {
      const mid = new Map(), next = [];
      // New points follow the curve of the surface (the midpoint of the cubic a PN triangle puts on the edge,
      // from the normals at both ends), not the flat triangle: plain midpoints would show the coarse facets.
      const N = P.map(() => [0, 0, 0]);
      for (let t = 0; t < I.length; t += 3) {
        const f = cross(sub(P[I[t + 1]], P[I[t]]), sub(P[I[t + 2]], P[I[t]]));
        for (let k = 0; k < 3; k++) for (let j = 0; j < 3; j++) N[I[t + k]][j] += f[j];
      }
      const midpoint = (a, c) => {
        if (!mid.has(key(a, c))) {
          const p0 = P[a], p1 = P[c], n0 = norm(N[a]), n1 = norm(N[c]), e = sub(p1, p0);
          const w01 = e[0] * n0[0] + e[1] * n0[1] + e[2] * n0[2], w10 = -(e[0] * n1[0] + e[1] * n1[1] + e[2] * n1[2]);
          const b1 = [0, 1, 2].map((j) => (2 * p0[j] + p1[j] - w01 * n0[j]) / 3);
          const b2 = [0, 1, 2].map((j) => (2 * p1[j] + p0[j] - w10 * n1[j]) / 3);
          mid.set(key(a, c), P.length);
          P.push([0, 1, 2].map((j) => (p0[j] + 3 * b1[j] + 3 * b2[j] + p1[j]) / 8));
        }
        return mid.get(key(a, c));
      };
      for (let t = 0; t < I.length; t += 3) {
        const tri = [I[t], I[t + 1], I[t + 2]];
        const pts = [P[tri[0]], P[tri[1]], P[tri[2]], P[tri[0]].map((v, j) => (v + P[tri[1]][j] + P[tri[2]][j]) / 3)];
        const near = centres.some((c) => pts.some((p) => Math.hypot(...sub(p, c)) < POCKET_R + 0.05));
        if (near) { midpoint(tri[0], tri[1]); midpoint(tri[1], tri[2]); midpoint(tri[2], tri[0]); }
      }
      for (let t = 0; t < I.length; t += 3) {
        const tri = [I[t], I[t + 1], I[t + 2]];
        const em = [0, 1, 2].map((k) => mid.get(key(tri[k], tri[(k + 1) % 3]))); // em[k]: point on edge k -> k+1
        const count = em.filter((x) => x !== undefined).length;
        if (count === 0) { next.push(...tri); continue; }
        if (count === 3) {
          const [a, b, c] = tri, [ab, bc, ca] = em;
          next.push(a, ab, ca, ab, b, bc, ca, bc, c, ab, bc, ca);
          continue;
        }
        // Turn the triangle so its first edge is split (and, with two, the second too).
        let r = 0;
        if (count === 1) r = em.findIndex((x) => x !== undefined);
        else r = em.findIndex((x, k) => x !== undefined && em[(k + 1) % 3] !== undefined);
        const [a, b, c] = [tri[r], tri[(r + 1) % 3], tri[(r + 2) % 3]];
        const ab = em[r], bc = em[(r + 1) % 3];
        if (count === 1) next.push(a, ab, c, ab, b, c);
        else next.push(ab, b, bc, a, ab, bc, a, bc, c);
      }
      I = next;
    }
    // Press each pocket in along the surface's own normal there (area-weighted, from the triangles around it).
    // Only the outer skin moves: not the lips of panel gaps (open edges) or the flanges turning into them.
    const VN = P.map(() => [0, 0, 0]), useCount = new Map();
    for (let t = 0; t < I.length; t += 3) {
      const f = cross(sub(P[I[t + 1]], P[I[t]]), sub(P[I[t + 2]], P[I[t]]));
      for (let k = 0; k < 3; k++) {
        for (let j = 0; j < 3; j++) VN[I[t + k]][j] += f[j];
        const ek = key(I[t + k], I[t + (k + 1) % 3]); useCount.set(ek, (useCount.get(ek) ?? 0) + 1);
      }
    }
    const lip = new Set();
    for (const [ek, n2] of useCount) if (n2 === 1) for (const v of ek.split(',')) lip.add(+v);
    for (const c of centres) {
      let nrm = [0, 0, 0];
      for (let t = 0; t < I.length; t += 3) {
        const [a, b, d] = [P[I[t]], P[I[t + 1]], P[I[t + 2]]];
        if (Math.hypot(...sub(a, c)) > POCKET_RX) continue;
        const f = cross(sub(b, a), sub(d, a)); nrm = nrm.map((v, j) => v + f[j]);
      }
      nrm = norm(nrm); if (nrm[2] > 0) nrm = nrm.map((v) => -v); // outward = towards the rear (-Z)
      // Axes across the surface: up (world up, flattened onto it) and sideways.
      const up = norm([0, 1, 0].map((v, j) => v - nrm[1] * nrm[j])), side = cross(up, nrm);
      const mid = c.map((v, j) => v - up[j] * POCKET_DROP);
      let moved = 0;
      for (const [v, p] of P.entries()) {
        const r = sub(p, mid), vn = norm(VN[v]);
        if (lip.has(v) || vn[0] * nrm[0] + vn[1] * nrm[1] + vn[2] * nrm[2] < 0.5) continue;
        if (Math.abs(r[0] * nrm[0] + r[1] * nrm[1] + r[2] * nrm[2]) > 0.08) continue; // other side of the body
        const e = Math.hypot((r[0] * side[0] + r[1] * side[1] + r[2] * side[2]) / POCKET_RX,
          (r[0] * up[0] + r[1] * up[1] + r[2] * up[2]) / POCKET_RY);
        if (e >= 1) continue;
        const f = e <= POCKET_FLAT ? 1 : 0.5 * (1 + Math.cos(Math.PI * (e - POCKET_FLAT) / (1 - POCKET_FLAT)));
        for (let j = 0; j < 3; j++) p[j] -= nrm[j] * POCKET_DEPTH * f;
        moved++;
      }
      pockets.push({ centre: c.map((v, j) => v - nrm[j] * POCKET_DEPTH), normal: nrm });
      console.log('pocket at', c.map((v) => v.toFixed(3)).join(', '), 'normal', nrm.map((v) => v.toFixed(2)).join(', '), 'vertices moved', moved);
    }
    // Relax the closed openings into the bumper (Laplacian smoothing fading out over PATCH_R), clear of the pockets.
    {
      const nb = Array.from({ length: P.length }, () => new Set());
      const uses = new Map();
      for (let t = 0; t < I.length; t += 3) for (let k = 0; k < 3; k++) {
        const a = I[t + k], c = I[t + (k + 1) % 3];
        nb[a].add(c); nb[c].add(a); uses.set(key(a, c), (uses.get(key(a, c)) ?? 0) + 1);
      }
      const open = new Set();
      for (const [k2, n2] of uses) if (n2 === 1) for (const v of k2.split(',')) open.add(+v);
      const w = new Map();
      P.forEach((p, v) => {
        if (open.has(v) || centres.some((c) => Math.hypot(...sub(p, c)) < POCKET_R + 0.01)) return;
        for (const c of patches) {
          const d = Math.hypot(...sub(p, c));
          if (d < PATCH_R) w.set(v, Math.max(w.get(v) ?? 0, 0.5 * (1 + Math.cos(Math.PI * d / PATCH_R))));
        }
      });
      for (let pass = 0; pass < 80; pass++) {
        const next = [];
        for (const [v, wt] of w) {
          const ns = [...nb[v]]; if (!ns.length) continue;
          next.push([v, [0, 1, 2].map((j) => P[v][j] + (ns.reduce((s, u) => s + P[u][j], 0) / ns.length - P[v][j]) * 0.5 * wt)]);
        }
        for (const [v, p] of next) P[v] = p;
      }
    }
    // Back to the node's own frame (uniform scale, rotation and translation).
    const s2 = m[0] * m[0] + m[1] * m[1] + m[2] * m[2];
    const out = new Float32Array(P.length * 3);
    P.forEach((p, i) => {
      const d = [p[0] - m[12], p[1] - m[13], p[2] - m[14]];
      for (let j = 0; j < 3; j++) out[i * 3 + j] = (m[j * 4] * d[0] + m[j * 4 + 1] * d[1] + m[j * 4 + 2] * d[2]) / s2;
    });
    prim.setAttribute('POSITION', prim.getAttribute('POSITION').clone().setArray(out));
    const Idx = P.length > 65535 ? Uint32Array : Uint16Array;
    prim.setIndices(prim.getIndices().clone().setArray(new Idx(I)));
  }
}
if (pockets.length !== 2) throw new Error('expected 2 bumper pockets, made ' + pockets.length);

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

// 3-. The download's rectangular bumper reflectors (red plastic, a see-through red lens and a silver backing,
// about 0.47-0.52 m up, each in a part shared with the tail lamps) are cut out; round ones replace them (3c).
let reflectorTris = 0;
const inOldReflector = (x, y, z) => Math.abs(x) > 0.55 && Math.abs(x) < 0.9 && y > 0.4 && y < 0.6 && z < -2.0;
for (const n of scene.listChildren()) {
  const mesh = n.getMesh(); if (!mesh) continue;
  const m = n.getWorldMatrix();
  for (const prim of mesh.listPrimitives()) {
    if (prim.getMaterial() === paint) continue;
    const pos = prim.getAttribute('POSITION').getArray(), idx = prim.getIndices().getArray(), keep = [];
    for (let t = 0; t < idx.length; t += 3) {
      const c = [0, 1, 2].map((j) => [0, 1, 2].reduce((s, k) => {
        const i = idx[t + k] * 3;
        return s + (m[j] * pos[i] + m[4 + j] * pos[i + 1] + m[8 + j] * pos[i + 2] + m[12 + j]) / 3;
      }, 0));
      if (inOldReflector(...c)) reflectorTris++; else keep.push(idx[t], idx[t + 1], idx[t + 2]);
    }
    if (keep.length !== idx.length) prim.setIndices(prim.getIndices().clone().setArray(new idx.constructor(keep)));
  }
}
console.log('bumper reflectors: removed', reflectorTris, 'triangles of the rectangular ones');

// 3a. Wheels: the stock rims (and their centre caps, which would poke through the new hub) are replaced by
// "Volk Racing CE28 Rims" (MrDogeSuper, CC BY 4.0), one copy per corner, in machined silver. Each sits where the
// stock rim was: same centre, same diameter, outer face flush. Tyres, discs and calipers stay.
const STOCK_RIMS = ['Tex_0009_0_dds.001', 'Tex_0009_0_dds.002'], STOCK_CAPS = ['Material.068'];
const rimSpots = [];
for (const n of [...scene.listChildren()]) {
  const name = n.getMesh()?.listPrimitives()[0]?.getMaterial()?.getName();
  if (!STOCK_RIMS.includes(name) && !STOCK_CAPS.includes(name)) continue;
  if (STOCK_RIMS.includes(name)) for (const side of [-1, 1]) {
    // The node holds both wheels of one axle; measure the half on this side.
    const m = n.getWorldMatrix(), mn = [Infinity, Infinity, Infinity], mx = [-Infinity, -Infinity, -Infinity];
    for (const prim of n.getMesh().listPrimitives()) {
      const p = prim.getAttribute('POSITION').getArray();
      for (let i = 0; i < p.length; i += 3) {
        const w = [0, 1, 2].map((j) => m[j] * p[i] + m[4 + j] * p[i + 1] + m[8 + j] * p[i + 2] + m[12 + j]);
        if (Math.sign(w[0]) !== side) continue;
        for (let j = 0; j < 3; j++) { mn[j] = Math.min(mn[j], w[j]); mx[j] = Math.max(mx[j], w[j]); }
      }
    }
    rimSpots.push({ side, outer: side > 0 ? mx[0] : mn[0], y: (mn[1] + mx[1]) / 2, z: (mn[2] + mx[2]) / 2,
      diameter: Math.max(mx[1] - mn[1], mx[2] - mn[2]) });
  }
  n.dispose();
}
if (rimSpots.length !== 4) throw new Error('expected 4 stock rims, found ' + rimSpots.length);
const wheelDoc = await io.read(wheelIn);
await wheelDoc.transform(flatten());
const wheelNode = wheelDoc.getRoot().listNodes().find((n) => n.getMesh());
transformMesh(wheelNode.getMesh(), wheelNode.getWorldMatrix()); // bake its placement: axle along x, face to +x
const wheelMesh = copyToDocument(doc, wheelDoc, [wheelNode.getMesh()]).get(wheelNode.getMesh());
const rim = wheelMesh.listPrimitives()[0].getMaterial().setName('rim')
  .setBaseColorFactor([0.78, 0.79, 0.8, 1]).setMetallicFactor(1).setRoughnessFactor(0.22);
const wb = { min: [Infinity, Infinity, Infinity], max: [-Infinity, -Infinity, -Infinity] };
for (const prim of wheelMesh.listPrimitives()) {
  const p = prim.getAttribute('POSITION').getArray();
  for (let i = 0; i < p.length; i++) { wb.min[i % 3] = Math.min(wb.min[i % 3], p[i]); wb.max[i % 3] = Math.max(wb.max[i % 3], p[i]); }
}
const wheelDia = Math.max(wb.max[1] - wb.min[1], wb.max[2] - wb.min[2]);
// As modelled, the axle runs along x and the spoke face looks towards -x: the left wheels use it as it is, the
// right ones are turned half a turn about the vertical axis.
for (const s of rimSpots) {
  const k = s.diameter / wheelDia, turned = s.side > 0, flip = turned ? -1 : 1;
  scene.addChild(doc.createNode('rim').setMesh(wheelMesh).setScale([k, k, k])
    .setRotation(turned ? [0, 1, 0, 0] : [0, 0, 0, 1])
    .setTranslation([s.outer + s.side * wb.min[0] * k, s.y - (wb.min[1] + wb.max[1]) / 2 * k,
      s.z - flip * (wb.min[2] + wb.max[2]) / 2 * k]));
}
await doc.transform(prune(), unpartition());
console.log('rims: replaced 4, rim diameter', rimSpots[0].diameter.toFixed(3), 'm');

// 3a'. Tyres: the stock tyres are replaced by the light tyre of "Car wheels and tire" (Márcio Meireles, CC BY 4.0),
// its own rim left out. It is a low-profile tyre, so its radius is remapped to run from just inside the CE28's
// rim to the stock tyre's outside, and its width is matched to the stock tyre's; the tread stays outermost. Its
// 4096-pixel textures (colour, roughness, normal map) are scaled down to TYRE_TEX: the tyre is small on screen.
const TYRE_TEX = 512;
const tyreSpots = [];
for (const n of [...scene.listChildren()]) {
  const mesh = n.getMesh(); if (!mesh || mesh.listPrimitives().some((p) => p.getMaterial() === paint)) continue;
  // A stock tyre node: both tyres of one axle, a ring about 0.68 m tall standing on the floor.
  const m = n.getWorldMatrix(), box = { [-1]: null, 1: null };
  for (const prim of mesh.listPrimitives()) {
    const p = prim.getAttribute('POSITION').getArray();
    for (let i = 0; i < p.length; i += 3) {
      const w = [0, 1, 2].map((j) => m[j] * p[i] + m[4 + j] * p[i + 1] + m[8 + j] * p[i + 2] + m[12 + j]);
      const b2 = box[Math.sign(w[0]) || 1] ??= [[Infinity, Infinity, Infinity], [-Infinity, -Infinity, -Infinity]];
      for (let j = 0; j < 3; j++) { b2[0][j] = Math.min(b2[0][j], w[j]); b2[1][j] = Math.max(b2[1][j], w[j]); }
    }
  }
  const sides = [-1, 1].map((sd) => box[sd]);
  const isTyre = sides.every((b2) => b2 && b2[0][1] < 0.05 && b2[1][1] > 0.6 && b2[1][1] < 0.75 &&
    b2[1][2] - b2[0][2] > 0.6 && b2[1][2] - b2[0][2] < 0.75 && b2[1][0] - b2[0][0] < 0.3);
  if (!isTyre) continue;
  for (const sd of [-1, 1]) {
    const [mn, mx] = box[sd];
    tyreSpots.push({ side: sd, x: (mn[0] + mx[0]) / 2, y: (mn[1] + mx[1]) / 2, z: (mn[2] + mx[2]) / 2,
      width: mx[0] - mn[0], outer: Math.max(mx[1] - mn[1], mx[2] - mn[2]) / 2 });
  }
  n.dispose();
}
if (tyreSpots.length !== 4) throw new Error('expected 4 stock tyres, found ' + tyreSpots.length);

// Minimal PNG reader (8-bit, not interlaced: grey, RGB, palette, grey+alpha, RGBA) and a box filter.
function readPng(buf) {
  let i = 8, w = 0, h = 0, type = 0, plte = null; const idat = [];
  while (i < buf.length) {
    const len = buf.readUInt32BE(i), kind = buf.toString('latin1', i + 4, i + 8), d = buf.subarray(i + 8, i + 8 + len);
    if (kind === 'IHDR') {
      w = d.readUInt32BE(0); h = d.readUInt32BE(4); type = d[9];
      if (d[8] !== 8 || d[12] !== 0) throw new Error('unsupported PNG');
    } else if (kind === 'PLTE') plte = d; else if (kind === 'IDAT') idat.push(d);
    i += 12 + len;
  }
  const bpp = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type], raw = inflateSync(Buffer.concat(idat)), row = w * bpp;
  const px = Buffer.alloc(h * row);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (row + 1)], src = raw.subarray(y * (row + 1) + 1, (y + 1) * (row + 1)), o = y * row;
    for (let x = 0; x < row; x++) {
      const a = x >= bpp ? px[o + x - bpp] : 0, b = y > 0 ? px[o - row + x] : 0, c = x >= bpp && y > 0 ? px[o - row + x - bpp] : 0;
      let v = src[x];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      px[o + x] = v & 255;
    }
  }
  const rgba = Buffer.alloc(w * h * 4);
  for (let k = 0; k < w * h; k++) {
    const s2 = k * bpp;
    const [r, g, b, a] = type === 0 ? [px[s2], px[s2], px[s2], 255] : type === 2 ? [px[s2], px[s2 + 1], px[s2 + 2], 255]
      : type === 3 ? [plte[px[s2] * 3], plte[px[s2] * 3 + 1], plte[px[s2] * 3 + 2], 255]
      : type === 4 ? [px[s2], px[s2], px[s2], px[s2 + 1]] : [px[s2], px[s2 + 1], px[s2 + 2], px[s2 + 3]];
    rgba[k * 4] = r; rgba[k * 4 + 1] = g; rgba[k * 4 + 2] = b; rgba[k * 4 + 3] = a;
  }
  return { w, h, rgba };
}
function shrink({ w, h, rgba }, n) {
  if (w !== h || w % n) throw new Error('texture must be square and a multiple of ' + n);
  const f = w / n, out = Buffer.alloc(n * n * 4);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) for (let c = 0; c < 4; c++) {
    let sum = 0;
    for (let dy = 0; dy < f; dy++) for (let dx = 0; dx < f; dx++) sum += rgba[((y * f + dy) * w + x * f + dx) * 4 + c];
    out[(y * n + x) * 4 + c] = Math.round(sum / (f * f));
  }
  return out;
}

const tyreDoc = await io.read(tyreIn);
await tyreDoc.transform(flatten());
// The light tyre centred on the origin (the file has two; the other sits off to one side).
const tyreNode = tyreDoc.getRoot().listNodes().find((n) => n.getMesh()?.listPrimitives()
  .some((p) => p.getMaterial()?.getName() === 'tire-low-v1') && [12, 13, 14].every((i) => Math.abs(n.getWorldMatrix()[i]) < 1e-3));
if (!tyreNode) throw new Error('light tyre not found');
transformMesh(tyreNode.getMesh(), tyreNode.getWorldMatrix()); // axle along x, centred
for (const tex of tyreDoc.getRoot().listTextures()) tex.setImage(png(TYRE_TEX, shrink(readPng(Buffer.from(tex.getImage())), TYRE_TEX)));
const tyreMesh = copyToDocument(doc, tyreDoc, [tyreNode.getMesh()]).get(tyreNode.getMesh());
// Matte rubber: the download's roughness map says 0.1 (wet, mirror-like), so it's dropped for an even 0.85.
// Its colour and normal map (the tread and sidewall detail) stay.
tyreMesh.listPrimitives()[0].getMaterial().setName('tyre')
  .setMetallicRoughnessTexture(null).setMetallicFactor(0).setRoughnessFactor(0.85);
// Remap: radius rIn..rOut -> rim radius (less 2 mm, so the bead sits under the rim's lip)..stock outside;
// width -> stock width. Done once, in the tyre's own frame (x = axle), against the first stock tyre.
{
  const s0 = tyreSpots[0], rimR = rimSpots[0].diameter / 2 - 0.002;
  const prim = tyreMesh.listPrimitives()[0], pos = prim.getAttribute('POSITION'), p = pos.getArray().slice();
  let rIn = Infinity, rOut = 0, xMin = Infinity, xMax = -Infinity;
  for (let i = 0; i < p.length; i += 3) {
    const r = Math.hypot(p[i + 1], p[i + 2]);
    rIn = Math.min(rIn, r); rOut = Math.max(rOut, r); xMin = Math.min(xMin, p[i]); xMax = Math.max(xMax, p[i]);
  }
  for (let i = 0; i < p.length; i += 3) {
    const r = Math.hypot(p[i + 1], p[i + 2]), r2 = rimR + ((r - rIn) * (s0.outer - rimR)) / (rOut - rIn);
    p[i] = ((p[i] - (xMin + xMax) / 2) * s0.width) / (xMax - xMin);
    if (r > 0) { p[i + 1] *= r2 / r; p[i + 2] *= r2 / r; }
  }
  prim.setAttribute('POSITION', pos.clone().setArray(p));
  console.log('tyres: profile', (rIn / rOut).toFixed(2), '->', (rimR / s0.outer).toFixed(2), 'outside', (2 * s0.outer).toFixed(3), 'm');
}
for (const s of tyreSpots) {
  // Centred on the new rim at that corner (the stock tyre's own box sits a few mm off it).
  const r = rimSpots.filter((q) => q.side === s.side).sort((a, b) => Math.abs(a.z - s.z) - Math.abs(b.z - s.z))[0];
  scene.addChild(doc.createNode('tyre').setMesh(tyreMesh)
    .setRotation(s.side > 0 ? [0, 1, 0, 0] : [0, 0, 0, 1]).setTranslation([s.x, r.y, r.z]));
}
await doc.transform(prune(), unpartition());
console.log('tyres: replaced 4, textures', TYRE_TEX, 'px');
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

// 3c. Round bumper reflectors, one on the floor of each pocket (step 2d): a red disc 9 cm across with a short
// rim, in the unlit tail-light plastic (reflectors don't light up).
{
  const R = 0.045, SEG = 48, LIFT = 0.002, RIM = 0.004;
  const verts = [], tris = [];
  for (const { centre: c, normal: nrm } of pockets) {
    // Two axes across the pocket floor.
    const up = Math.abs(nrm[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
    const cr = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
    const nz = (a) => { const l = Math.hypot(...a); return a.map((v) => v / l); };
    const u = nz(cr(up, nrm)), v = cr(nrm, u);
    const at = (r, a, h) => [0, 1, 2].map((j) => c[j] + nrm[j] * h + (u[j] * Math.cos(a) + v[j] * Math.sin(a)) * r);
    const base = verts.length / 3;
    verts.push(...at(0, 0, LIFT + RIM));
    for (let s = 0; s < SEG; s++) verts.push(...at(R, (2 * Math.PI * s) / SEG, LIFT + RIM)); // face ring
    for (let s = 0; s < SEG; s++) verts.push(...at(R, (2 * Math.PI * s) / SEG, -0.004)); // rim, into the floor
    for (let s = 0; s < SEG; s++) {
      const a = base + 1 + s, b = base + 1 + ((s + 1) % SEG), a2 = a + SEG, b2 = b + SEG;
      tris.push(base, a, b, a, b2, b, a, a2, b2); // counter-clockwise seen from outside
    }
  }
  const P = new Float32Array(verts);
  // Smooth normals, so the edge between face and rim reads as a slightly domed lens.
  const prim = doc.createPrimitive()
    .setAttribute('POSITION', doc.createAccessor().setType('VEC3').setBuffer(root.listBuffers()[0]).setArray(P))
    .setIndices(doc.createAccessor().setType('SCALAR').setBuffer(root.listBuffers()[0]).setArray(new Uint16Array(tris)))
    .setMaterial(tailOff);
  const N = new Float32Array(P.length);
  for (let t = 0; t < tris.length; t += 3) {
    const [a, b, c] = [tris[t] * 3, tris[t + 1] * 3, tris[t + 2] * 3];
    const e1 = [P[b] - P[a], P[b + 1] - P[a + 1], P[b + 2] - P[a + 2]], e2 = [P[c] - P[a], P[c + 1] - P[a + 1], P[c + 2] - P[a + 2]];
    const f = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
    for (const k of [a, b, c]) for (let j = 0; j < 3; j++) N[k + j] += f[j];
  }
  for (let i = 0; i < N.length; i += 3) { const l = Math.hypot(N[i], N[i + 1], N[i + 2]) || 1; N[i] /= l; N[i + 1] /= l; N[i + 2] /= l; }
  prim.setAttribute('NORMAL', doc.createAccessor().setType('VEC3').setBuffer(root.listBuffers()[0]).setArray(N));
  scene.addChild(doc.createNode('reflectors').setMesh(doc.createMesh('reflectors').addPrimitive(prim)));
  console.log('bumper reflectors: added', pockets.length, 'round ones');
}

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
