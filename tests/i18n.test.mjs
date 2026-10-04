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
