// UI-02 și UI-01: culorile de stare din src/index.html, în ambele teme.
// Textul de pe fiecare stare are contrast ≥ 4,5:1 (WCAG AA), iar „Favorabil” și „Nu” se deosebesc și pentru daltoniști
// (simulare Machado 2009, severitate 1; ΔE CIE76 ≥ 15).
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {read} from './load-core.mjs';

const html = read('src/index.html');
function tokens(selectorStart){
  const i = html.indexOf(selectorStart); assert.ok(i >= 0, selectorStart);
  const block = html.slice(i, html.indexOf('}', i));
  return Object.fromEntries([...block.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map(m => [m[1], m[2]]));
}
const LIGHT = tokens(':root{'), DARK = tokens(':root[data-theme="dark"]{');

const lin = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
const rgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const lum = h => { const [r, g, b] = rgb(h).map(lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
const DEUT = [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]];
const PROT = [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]];
function lab(h, M){
  let l = rgb(h).map(lin);
  if (M) l = M.map(row => Math.min(1, Math.max(0, row[0] * l[0] + row[1] * l[1] + row[2] * l[2])));
  const X = (0.4124 * l[0] + 0.3576 * l[1] + 0.1805 * l[2]) / 0.95047, Y = 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2], Z = (0.0193 * l[0] + 0.1192 * l[1] + 0.9505 * l[2]) / 1.08883;
  const f = v => v > 0.008856 ? Math.cbrt(v) : 7.787 * v + 16 / 116;
  return [116 * f(Y) - 16, 500 * (f(X) - f(Y)), 200 * (f(Y) - f(Z))];
}
const dE = (a, b, M) => Math.hypot(...lab(a, M).map((v, i) => v - lab(b, M)[i]));

for (const [name, T] of [['luminoasă', LIGHT], ['întunecată', DARK]]){
  test(`tema ${name}: text de stare ≥ 4,5:1`, () => {
    for (const s of ['go', 'maybe', 'no', 'calm']){
      const c = contrast(T[s], T[s + '-bg']);
      assert.ok(c >= 4.5, `${s}: ${c.toFixed(2)}:1 (${T[s]} pe ${T[s + '-bg']})`);
    }
  });
  test(`tema ${name}: „Favorabil” și „Nu” se deosebesc la daltonism`, () => {
    for (const [cvd, M] of [['deuteranopie', DEUT], ['protanopie', PROT]]){
      const d = dE(T['go-bg'], T['no-bg'], M);
      assert.ok(d >= 15, `${cvd}: ΔE ${d.toFixed(1)}`);
    }
  });
}
