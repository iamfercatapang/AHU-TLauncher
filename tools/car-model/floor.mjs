// Studio floor textures for the 3D car, after the Tesla UI prototype's satin floor (values re-derived for
// the Avanza, front at +Z). Output: RGBA, sRGB colour; alpha = 1 - reflection, fading out at the edge.
import { deflateSync } from 'node:zlib';
export const FLOOR_SIZE = 30; // metres, centred on the car
const HALF_W = 0.93, HALF_L = 2.35; // car body footprint (scaled model)
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const toSrgb = (v) => (v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);

// kind: 'dark' (night floor), 'light' (day floor) or 'beams' (the headlight beams, drawn only with the lights on).
export function floorPixels(n, kind) {
  const dark = kind === 'dark';
  const px = Buffer.alloc(n * n * 4);
  let seed = 12345;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) {
    // uv (0,0) = first pixel; the quad maps u to +x and v to +z.
    const x = ((i + 0.5) / n - 0.5) * FLOOR_SIZE;
    const z = ((j + 0.5) / n - 0.5) * FLOOR_SIZE;
    const len = Math.hypot(x, z);
    const qx = (x - 2.4) / 8, qz = (z - 0.5) / 10;
    const pool = Math.exp(-(qx * qx + qz * qz));
    const contact = Math.exp(-Math.pow(Math.abs(x) / HALF_W, 4) - Math.pow(Math.abs(z) / HALF_L, 4));
    // Soft shadow of the key light (high, front-right of the car): falls a little behind-left.
    const shadow = Math.exp(-Math.pow(Math.abs(x + 0.35) / 1.15, 4) - Math.pow(Math.abs(z + 0.4) / 2.6, 4));
    let c, reflect;
    if (dark) {
      c = mix([0.005, 0.006, 0.008], [0.022, 0.026, 0.034], pool);
      c = c.map((v) => v * (1 - contact * 0.92) * (1 - shadow * 0.42));
      reflect = 0.07 * (1 - smooth(2.5, 7.0, len));
    } else {
      // Day: a pale studio floor (same light pool and shadows, no beams), a little more reflective.
      c = mix([0.8, 0.8, 0.8], [0.9, 0.9, 0.91], pool);
      c = c.map((v) => v * (1 - contact * 0.75) * (1 - shadow * 0.3));
      reflect = 0.1 * (1 - smooth(2.5, 7.0, len));
    }
    if (kind === 'beams') {
      // The two low beams widen and fade across the floor in front of the car. Blended over the floor:
      // a cool white at low opacity adds about (0.028, 0.035, 0.046) of light, as the prototype's floor does.
      const fwd = z - HALF_L;
      const bw = 0.18 + Math.max(fwd, 0) * 0.24;
      const o1 = (x - 0.68) / bw, o2 = (x + 0.68) / bw;
      const beams = (Math.exp(-o1 * o1) + Math.exp(-o2 * o2)) * smooth(0, 0.85, fwd) * (1 - smooth(2.5, 8.0, fwd));
      const o = (j * n + i) * 4;
      const col = [0.61, 0.76, 1.0];
      for (let k = 0; k < 3; k++) px[o + k] = Math.round(toSrgb(col[k]) * 255);
      px[o + 3] = Math.max(0, Math.min(255, Math.round(Math.min(1, beams) * 0.046 * 255 * 1.6 + rnd() - 0.5)));
      continue;
    }
    const edge = 1 - smooth(9, 14.5, len);
    const a = edge * (1 - reflect);
    const o = (j * n + i) * 4;
    for (let k = 0; k < 3; k++) px[o + k] = Math.max(0, Math.min(255, Math.round(toSrgb(c[k] / (1 - reflect)) * 255 + rnd() - 0.5)));
    px[o + 3] = Math.max(0, Math.min(255, Math.round(a * 255 + rnd() - 0.5)));
  }
  return px;
}

const CRC = new Int32Array(256).map((_, k) => {
  let c = k;
  for (let i = 0; i < 8; i++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc(buf) {
  let c = -1;
  for (const v of buf) c = CRC[(c ^ v) & 255] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
  return Buffer.concat([len, td, c]);
}

export function png(n, rgba) {
  const stride = n * 4 + 1;
  const raw = Buffer.alloc(n * stride);
  for (let y = 0; y < n; y++) rgba.copy(raw, y * stride + 1, y * n * 4, (y + 1) * n * 4);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(n, 0); ihdr.writeUInt32BE(n, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]);
}

// Route lane lines for the driving view (Tesla's blue lines either side of the car), shown only while navigating.
// The strip's u runs across it, v along it from LANE_NEAR (behind the car) to LANE_FAR (ahead, in metres).
export const LANE_NEAR = -5, LANE_FAR = 45;
export function lanePixels(n) {
  const px = Buffer.alloc(n * n * 4);
  const col = [0.2, 0.42, 1.0]; // linear, a bright Tesla blue
  for (let j = 0; j < n; j++) for (let i = 0; i < n; i++) {
    const z = LANE_NEAR + ((j + 0.5) / n) * (LANE_FAR - LANE_NEAR);
    const u = Math.abs((i + 0.5) / n - 0.5) * 2; // 0 in the middle of the line, 1 at its edges
    const along = smooth(-4.5, -1.5, z) * (1 - smooth(14, LANE_FAR, z));
    const across = 1 - smooth(0.55, 1, u);
    const o = (j * n + i) * 4;
    for (let k = 0; k < 3; k++) px[o + k] = Math.round(toSrgb(col[k]) * 255);
    px[o + 3] = Math.round(along * across * 0.85 * 255);
  }
  return px;
}
