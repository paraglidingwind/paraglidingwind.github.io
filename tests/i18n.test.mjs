// Fiecare text trecut prin t() are traducere în data/i18n_en.json (altfel versiunea EN ar afișa româna).
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {core, read} from './load-core.mjs';

const en = JSON.parse(read('data/i18n_en.json'));

test('toate textele t(\'…\') au traducere în engleză', () => {
  const src = read('src/index.html') + read('src/core.js'), keys = new Set();
  for (const m of src.matchAll(/\bt\(\s*'((?:[^'\\]|\\.)*)'/g)) keys.add(m[1].replace(/\\'/g, "'"));
  const missing = [...keys].filter(k => !(k in en));
  assert.deepEqual(missing, [], 'lipsesc din data/i18n_en.json');
});

test('etichetele de cer și notele despre nori au traducere', () => {
  const labels = [...Object.values(core.WMO), ...Object.values(core.CLOUD_NOTES), 'Senin', 'Parțial noros', 'Mai mult noros', 'Înnorat', 'Necunoscut'];
  assert.deepEqual([...new Set(labels)].filter(k => !(k in en)), []);
});

test('textele fixe din pagină (HTML) au traducere în engleză', () => {
  const html = read('src/index.html'), body = html.slice(html.indexOf('<body>'), html.indexOf('<script>'));
  const keep = new Set(['Română', 'English', 'Windy', 'Meteoblue', 'Export Excel', 'Import Excel']);
  const keys = new Set();
  for (const m of body.matchAll(/>([^<>]+)</g)){ const k = m[1].trim(); if (k && /\p{L}{2}/u.test(k)) keys.add(k); }
  for (const m of body.matchAll(/(?:aria-label|placeholder|title)="([^"]+)"/g)) if (/\p{L}{2}/u.test(m[1])) keys.add(m[1]);
  const missing = [...keys].filter(k => !(k in en) && !keep.has(k) && !/^ex: |^\d/.test(k));
  assert.deepEqual(missing, [], 'texte din HTML fără traducere în data/i18n_en.json');
});
