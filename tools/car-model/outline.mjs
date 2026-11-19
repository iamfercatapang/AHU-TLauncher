// Prints the car's outline points for CarFraming.OUTLINE (ui/home/CarFraming.kt): for 160 directions spread
// over a sphere, the car vertex furthest that way (floor and reflection left out). Paste the output into OUTLINE.
//   node outline.mjs [model.glb]
import { NodeIO } from '@gltf-transform/core';
import { ALL_EXTENSIONS } from '@gltf-transform/extensions';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const [, , file = resolve(here, '../../app/src/main/assets/models/avanza.glb')] = process.argv;
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);
const doc = await io.read(file);
const pts = [];
for (const node of doc.getRoot().listScenes()[0].listChildren()) {
  const name = node.getName();
  if (name === 'mirror' || name.startsWith('floor')) continue;
  node.traverse((n) => {
    const mesh = n.getMesh(); if (!mesh) return;
    const m = n.getWorldMatrix();
    for (const p of mesh.listPrimitives()) {
      const a = p.getAttribute('POSITION').getArray();
      for (let i = 0; i < a.length; i += 3) {
        const x = a[i], y = a[i + 1], z = a[i + 2];
        pts.push([m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]]);
      }
    }
  });
}
const N = 160, chosen = new Set();
for (let k = 0; k < N; k++) {
  const y = 1 - (2 * (k + 0.5)) / N, r = Math.sqrt(1 - y * y), phi = k * Math.PI * (3 - Math.sqrt(5));
  const d = [Math.cos(phi) * r, y, Math.sin(phi) * r];
  let best = -1, bv = -Infinity;
  for (let i = 0; i < pts.length; i++) { const v = pts[i][0] * d[0] + pts[i][1] * d[1] + pts[i][2] * d[2]; if (v > bv) { bv = v; best = i; } }
  chosen.add(best);
}
const out = [...chosen].map((i) => pts[i].map((v) => Number(v.toFixed(3))));
for (let i = 0; i < out.length; i += 4) console.log('        ' + out.slice(i, i + 4).map((p) => p.join(', ')).join(', ') + ',');
