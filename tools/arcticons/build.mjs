// Picks the Arcticons icons for common in-car apps and turns them into Android vector drawables. See README.md.
//   node build.mjs
// Writes app/src/main/res/drawable/arc_<name>.xml and app/src/main/java/com/ahu/tlauncher/launcher/ArcticonsMap.kt.
// Downloads (appfilter.xml and the SVGs, at the pinned commit) are kept in cache/.
import { mkdir, readFile, writeFile, readdir, rm } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const COMMIT = '5f7c14a40ea7758ed46e03f69daaa51af907c978'; // Arcticons-Team/Arcticons, 2026-11-19
const RAW = `https://raw.githubusercontent.com/Arcticons-Team/Arcticons/${COMMIT}`;
/** Line width in the icons' 48-unit box. Arcticons' own default is 1; a little bolder reads better at car distance. */
const STROKE = 1.4;

/** The apps that get an icon: exact packages, and prefixes for head-unit firmware apps. */
const PACKAGES = [
  // Navigation and rides
  'com.google.android.apps.maps', 'com.waze', 'com.here.app.maps', 'com.sygic.aura', 'net.osmand', 'net.osmand.plus',
  'app.organicmaps', 'com.mapswithme.maps.pro', 'com.tomtom.gplay.navapp', 'ru.yandex.yandexnavi', 'com.ubercab',
  'com.ubercab.driver', 'com.grabtaxi.passenger', 'com.grabtaxi.driver2', 'com.google.android.projection.gearhead',
  // Music, radio, podcasts, video
  'com.spotify.music', 'com.google.android.apps.youtube.music', 'com.google.android.youtube', 'app.revanced.android.youtube',
  'app.revanced.android.apps.youtube.music', 'com.apple.android.music', 'com.amazon.mp3', 'deezer.android.app',
  'com.soundcloud.android', 'com.aspiro.tidal', 'com.pandora.android', 'tunein.player', 'com.audible.application',
  'au.com.shiftyjelly.pocketcasts', 'com.google.android.apps.podcasts', 'com.netflix.mediaclient',
  'com.amazon.avod.thirdpartyclient', 'com.maxmpz.audioplayer', 'org.videolan.vlc', 'com.tencent.ibg.joox',
  'com.google.android.music', 'com.android.music', 'com.android.fmradio', 'com.caf.fmradio', 'com.mediatek.fmradio',
  'com.radio.fmradio', 'com.google.android.apps.youtube.kids', 'com.shazam.android',
  // Calls and messages
  'com.google.android.dialer', 'com.android.dialer', 'com.google.android.apps.messaging', 'com.android.mms',
  'com.google.android.contacts', 'com.android.contacts', 'com.whatsapp', 'com.whatsapp.w4b', 'com.facebook.orca',
  'org.telegram.messenger', 'com.viber.voip', 'com.discord',
  // Car tools
  'com.ovz.carscanner', 'org.prowl.torque', 'org.prowl.torquefree', 'com.gasbuddy.mobile',
  // Everyday
  'com.android.chrome', 'com.google.android.googlequicksearchbox', 'com.google.android.apps.googleassistant',
  'com.android.settings', 'com.android.vending', 'org.fdroid.fdroid', 'com.google.android.gm',
  'com.google.android.calendar', 'com.google.android.deskclock', 'com.android.deskclock', 'com.google.android.calculator',
  'com.android.calculator2', 'com.google.android.apps.photos', 'com.android.gallery3d', 'com.android.camera2',
  'com.google.android.GoogleCamera', 'com.android.documentsui', 'com.google.android.documentsui', 'com.google.android.apps.nbu.files',
  'com.google.android.apps.translate', 'com.google.android.keep', 'com.android.bluetooth',
];
const PREFIXES = ['com.syu.', 'com.microntek.', 'com.fyt.', 'com.ts.', 'com.android.car.'];
const wanted = (pkg) => PACKAGES.includes(pkg) || PREFIXES.some((p) => pkg.startsWith(p));

const here = dirname(fileURLToPath(import.meta.url));
const cache = resolve(here, 'cache');
const resDir = resolve(here, '../../app/src/main/res/drawable');
const ktFile = resolve(here, '../../app/src/main/java/com/ahu/tlauncher/launcher/ArcticonsMap.kt');
await mkdir(resolve(cache, 'svg'), { recursive: true });

async function cached(path, url) {
  if (existsSync(path)) return readFile(path, 'utf8');
  const r = await fetch(url);
  if (!r.ok) throw new Error(url + ': HTTP ' + r.status);
  const text = await r.text();
  await writeFile(path, text);
  return text;
}

// 1. Which drawable each wanted component uses.
const appfilter = await cached(resolve(cache, 'appfilter.xml'), `${RAW}/app/src/main/assets/appfilter.xml`);
const byComponent = new Map(); // "package/fully.qualified.Activity" -> drawable
for (const m of appfilter.matchAll(/<item\s+component="ComponentInfo\{([^/}]+)\/([^}]+)\}"\s+drawable="([^"]+)"/g)) {
  const [, pkg, cls, drawable] = m;
  if (!wanted(pkg)) continue;
  const full = cls.startsWith('.') ? pkg + cls : cls;
  if (!byComponent.has(pkg + '/' + full)) byComponent.set(pkg + '/' + full, drawable);
}

// 2. SVG -> VectorDrawable. Arcticons are simple: paths, circles, ellipses, lines, polylines, polygons and rects,
// styled by class (stroke or fill, white), sometimes with a transform.
const num = (s) => +(+s).toFixed(4);
const attrs = (tag) => Object.fromEntries([...tag.matchAll(/([\w:-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
function shapePath(name, a) {
  const f = (k) => num(a[k] ?? 0);
  switch (name) {
    case 'path': return a.d;
    case 'circle': { const [cx, cy, r] = [f('cx'), f('cy'), f('r')]; return `M${num(cx - r)},${cy}a${r},${r} 0 1,0 ${num(2 * r)},0a${r},${r} 0 1,0 ${num(-2 * r)},0Z`; }
    case 'ellipse': { const [cx, cy, rx, ry] = [f('cx'), f('cy'), f('rx'), f('ry')]; return `M${num(cx - rx)},${cy}a${rx},${ry} 0 1,0 ${num(2 * rx)},0a${rx},${ry} 0 1,0 ${num(-2 * rx)},0Z`; }
    case 'line': return `M${f('x1')},${f('y1')}L${f('x2')},${f('y2')}`;
    case 'polyline': case 'polygon': {
      const p = (a.points ?? '').trim().split(/[\s,]+/).map(Number);
      let d = `M${p[0]},${p[1]}`; for (let i = 2; i + 1 < p.length; i += 2) d += `L${p[i]},${p[i + 1]}`;
      return name === 'polygon' ? d + 'Z' : d;
    }
    case 'rect': {
      const [x, y, w, h] = [f('x'), f('y'), f('width'), f('height')];
      let rx = Math.min(num(a.rx ?? a.ry ?? 0), w / 2), ry = Math.min(num(a.ry ?? a.rx ?? 0), h / 2);
      if (!rx || !ry) return `M${x},${y}h${w}v${h}h${-w}Z`;
      return `M${num(x + rx)},${y}h${num(w - 2 * rx)}a${rx},${ry} 0 0,1 ${rx},${ry}v${num(h - 2 * ry)}a${rx},${ry} 0 0,1 ${-rx},${ry}` +
        `h${num(-(w - 2 * rx))}a${rx},${ry} 0 0,1 ${-rx},${-ry}v${num(-(h - 2 * ry))}a${rx},${ry} 0 0,1 ${rx},${-ry}Z`;
    }
  }
  return null;
}
/** An SVG transform as VectorDrawable group attributes (translate, rotate, uniform-or-not scale; no skew). */
function groupOf(t) {
  if (!t) return null;
  const g = { tx: 0, ty: 0, rot: 0, sx: 1, sy: 1, px: 0, py: 0 };
  for (const m of t.matchAll(/(\w+)\(([^)]*)\)/g)) {
    const v = m[2].trim().split(/[\s,]+/).map(Number);
    if (m[1] === 'translate') { g.tx += v[0]; g.ty += v[1] ?? 0; }
    else if (m[1] === 'rotate') { g.rot += v[0]; if (v.length === 3) { g.px = v[1]; g.py = v[2]; } }
    else if (m[1] === 'scale') { g.sx *= v[0]; g.sy *= v[1] ?? v[0]; }
    else if (m[1] === 'matrix') {
      const [a, b, c, d, e, f2] = v;
      const sx = Math.hypot(a, b), rot = Math.atan2(b, a), sy = (a * d - b * c) / sx;
      if (Math.abs(a * c + b * d) > 1e-3 * sx * Math.abs(sy)) return 'skew';
      g.tx += e; g.ty += f2; g.rot += (rot * 180) / Math.PI; g.sx *= sx; g.sy *= sy;
    } else return 'skew';
  }
  return g;
}
function toVector(svg, name) {
  const vb = (svg.match(/viewBox="([^"]+)"/)?.[1] ?? '0 0 48 48').trim().split(/[\s,]+/).map(Number);
  const styles = {};
  for (const m of (svg.match(/<style>([\s\S]*?)<\/style>/)?.[1] ?? '').matchAll(/((?:\.[\w-]+\s*,?\s*)+)\{([^}]*)\}/g)) {
    const props = Object.fromEntries(m[2].split(';').filter((x) => x.includes(':')).map((x) => x.split(':').map((y) => y.trim())));
    for (const cls of m[1].split(',')) styles[cls.trim().slice(1)] = { ...(styles[cls.trim().slice(1)] ?? {}), ...props };
  }
  const out = [];
  for (const m of svg.matchAll(/<(path|circle|ellipse|line|polyline|polygon|rect)\b([^>]*?)\/?>/g)) {
    const a = attrs(m[2]);
    const st = { ...(styles[a.class] ?? {}) };
    if (a.style) for (const kv of a.style.split(';')) { const [k, v] = kv.split(':').map((y) => y?.trim()); if (k) st[k] = v; }
    for (const k of ['fill', 'stroke', 'stroke-width']) if (a[k]) st[k] = a[k];
    const d = shapePath(m[1], a); if (!d) continue;
    const filled = st.fill && st.fill !== 'none';
    const stroked = st.stroke && st.stroke !== 'none';
    if (!filled && !stroked) continue;
    const g = groupOf(a.transform);
    if (g === 'skew') throw new Error('skewed transform');
    let p = `<path android:pathData="${d.replace(/\s+/g, ' ').trim()}"`;
    if (filled) p += ' android:fillColor="#FFFFFFFF"';
    if (stroked) {
      const w = st['stroke-width'] ? num(parseFloat(st['stroke-width']) * STROKE) : STROKE;
      p += ` android:strokeColor="#FFFFFFFF" android:strokeWidth="${w}" android:strokeLineCap="round" android:strokeLineJoin="round"`;
    }
    p += '/>';
    out.push(g ? `<group android:translateX="${num(g.tx)}" android:translateY="${num(g.ty)}" android:rotation="${num(g.rot)}"` +
      ` android:pivotX="${num(g.px)}" android:pivotY="${num(g.py)}" android:scaleX="${num(g.sx)}" android:scaleY="${num(g.sy)}">${p}</group>` : p);
  }
  if (!out.length) throw new Error('no shapes');
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- "${name}" from Arcticons (https://github.com/Arcticons-Team/Arcticons), CC BY-SA 4.0. Generated by tools/arcticons/build.mjs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="${vb[2]}" android:viewportHeight="${vb[3]}">
    <group android:translateX="${-vb[0]}" android:translateY="${-vb[1]}">
        ${out.join('\n        ')}
    </group>
</vector>
`;
}

// 3. Fetch and convert each drawable once; drop components whose icon can't be converted.
const drawables = [...new Set(byComponent.values())].sort();
const res = new Map(); // drawable -> resource name
const failed = [];
for (const name of drawables) {
  const resName = 'arc_' + name.toLowerCase().replace(/[^a-z0-9_]/g, '_');
  try {
    const svg = await cached(resolve(cache, 'svg', name + '.svg'), `${RAW}/icons/white/${name}.svg`);
    await writeFile(resolve(resDir, resName + '.xml'), toVector(svg, name));
    res.set(name, resName);
  } catch (e) { failed.push(name + ' (' + e.message + ')'); }
}
// Old icons no longer selected go.
for (const f of await readdir(resDir)) if (f.startsWith('arc_') && ![...res.values()].includes(f.slice(0, -4))) await rm(resolve(resDir, f));

// 4. The lookup table: exact component first, then the first icon listed for the package.
const comp = [...byComponent].filter(([, d]) => res.has(d)).sort(([a], [b]) => a.localeCompare(b));
const byPackage = new Map();
for (const [c, d] of comp) { const pkg = c.split('/')[0]; if (!byPackage.has(pkg)) byPackage.set(pkg, d); }
// Inner classes have '$' in their names, which Kotlin strings would read as a template: escape it.
const kt = (k) => k.split('$').join('\\$');
const line = (k, d) => `        "${kt(k)}" to R.drawable.${res.get(d)},`;
await writeFile(ktFile, `package com.ahu.tlauncher.launcher

import com.ahu.tlauncher.R

// Generated by tools/arcticons/build.mjs from Arcticons (https://github.com/Arcticons-Team/Arcticons) at
// ${COMMIT.slice(0, 12)}. Icons: CC BY-SA 4.0. Don't edit by hand; change the script and run it again.

/** Minimal line icons for common in-car apps: by "package/fully.qualified.Activity", and by package. */
internal object ArcticonsMap {
    val byComponent: Map<String, Int> = mapOf(
${comp.map(([c, d]) => line(c, d)).join('\n')}
    )
    val byPackage: Map<String, Int> = mapOf(
${[...byPackage].sort(([a], [b]) => a.localeCompare(b)).map(([p, d]) => line(p, d)).join('\n')}
    )
}
`);
console.log('components', comp.length, 'packages', byPackage.size, 'icons', res.size, 'failed', failed.length);
for (const f of failed) console.log('  skipped', f);
