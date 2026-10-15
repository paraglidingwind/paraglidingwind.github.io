// Teste pentru logica pură din src/core.js: node --test tests/
// Cazurile din data/test_vectors/ sunt aceleași pe care le citește și VectorsTest.kt (Android).
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {core, json, read} from './load-core.mjs';

test('rate(): cazurile comune', () => {
  const v = json('data/test_vectors/rate.json');
  for (const c of v.cases){
    const r = core.rate({o: v.sites[c.site]}, c.w, {...v.settings, ...(c.settings || {})}, {next: c.next || []});
    assert.equal(r.st, c.st, c.name);
    if (c.warn) assert.deepEqual([...(r.warn || [])].sort(), [...c.warn].sort(), c.name + ' (avertizări)');
  }
});

test('summarize(): cazurile comune', () => {
  const v = json('data/test_vectors/summary.json');
  for (const c of v.cases){
    const r = core.summarize({o: v.sites[c.site]}, c.hours, c.w, {...v.settings, ...(c.settings || {})}, c.extra || []);
    if (c.sts) assert.deepEqual(r.sts, c.sts, c.name);
    assert.equal(r.st, c.st, c.name);
    assert.equal(r.text, c.text, c.name);
  }
});

test('sky(): cazurile comune', () => {
  for (const c of json('data/test_vectors/sky.json').cases){
    const k = core.sky(c.w);
    for (const f of ['icon', 'label', 'type']) if (f in c) assert.equal(k[f], c[f], `${c.name}: ${f}`);
  }
});

test('parseCoords(): cazurile comune', () => {
  for (const c of json('data/test_vectors/coords.json').cases){
    const r = core.parseCoords(c.in);
    assert.deepEqual(r && [r.lat, r.lon], c.out, c.in);
  }
});

test('unitatea vântului (UI-13): aceleași verdicte, textele în unitatea aleasă', () => {
  const site = {o: [0, 1, 2, 2, 1, 0, 0, 0]}, s = {good: 5, marg: 7, calm: 1, gust: 9, spread: 4, rain: 0.2};
  const w = {ws: 7.5, wd: 90, wg: 12, pr: 0, w8: 9, d8: 90};
  const msg = u => core.rate(site, w, {...s, unit: u}).why.map(([, m]) => m);
  for (const u of ['kmh', 'kt', 'mph', 'toString', undefined]){
    const r = core.rate(site, w, {...s, unit: u});
    assert.equal(r.st, 'no', u); assert.deepEqual(r.warn, ['alt'], u);
  }
  assert.deepEqual(msg('ms'), ['Direcție bună (E)', 'Vânt prea tare: 7.5 m/s (peste 7)', 'Rafale 12.0 m/s (peste 9)', 'Vânt tare la ~1500 m: 9.0 m/s (peste 8)']);
  assert.deepEqual(msg('kmh'), ['Direcție bună (E)', 'Vânt prea tare: 27.0 km/h (peste 25.2)', 'Rafale 43.2 km/h (peste 32.4)', 'Vânt tare la ~1500 m: 32.4 km/h (peste 28.8)']);
  assert.deepEqual(msg('toString'), msg('ms'));   // unitate necunoscută = m/s
  assert.equal(core.rate(site, {ws: 0.5, wd: 90, wg: 1, pr: 0}, {...s, unit: 'kt'}).why[0][1], 'Vânt slab (1.0 kt), direcția nu contează');
  const sum = u => core.summarize(site, ['10', '11'], [{ws: 2, wd: 90, wg: 3, pr: 0}, {ws: 3, wd: 90, wg: 4, pr: 0}], {...s, unit: u}).text;
  assert.equal(sum('ms'), 'Favorabil 10–11 (2 ore) · vânt din E 2–3 m/s');
  assert.equal(sum('kt'), 'Favorabil 10–11 (2 ore) · vânt din E 4–6 kt');
  assert.equal(sum('mph'), 'Favorabil 10–11 (2 ore) · vânt din E 4–7 mph');
  assert.equal(core.summarize(site, ['10'], [{ws: 0.4, wd: 90, wg: 1, pr: 0}], {...s, unit: 'kmh'}).text, 'Nicio fereastră bună · calm (sub 3.6 km/h) ora 10');
  assert.deepEqual(['ms', 'kmh', 'kt', 'mph'].map(u => core.lim(5, {unit: u})), ['5', '18', '9.7', '11.2']);
});

test('sectorOf(): granițele sectoarelor', () => {
  assert.deepEqual([0, 350, 100, 315, 337.6, -45, 720].map(core.sectorOf), [0, 0, 2, 7, 0, 7, 0]);
});

test('syncMerge(): ultima modificare câștigă, ștergerile rămân șterse, dublurile dispar', () => {
  const a = {flights: [{id: 'a', date: '2026-10-01', site: 'Liteni', minutes: 60, u: 5}, {id: 'x', date: '2026-10-02', site: 'Agriș', minutes: 30, u: 1}], tomb: {}};
  const b = {flights: [{id: 'a', date: '2026-10-01', site: 'Liteni', minutes: 90, u: 9}, {id: 'y', date: '2026-10-02', site: 'Agriș', minutes: 30, u: 2}], tomb: {z: 3}};
  const m = core.syncMerge(a, b);
  assert.equal(m.flights.length, 2);
  assert.equal(m.flights.find(f => f.id === 'a').minutes, 90);
  assert.ok(!m.flights.some(f => f.id === 'x'), 'aceeași zi notată pe două dispozitive apare o singură dată');
  const d = core.syncMerge({flights: [{id: 'q', date: '2026-10-03', site: 'S', minutes: 10, u: 1}], tomb: {}}, {flights: [], tomb: {q: 2}});
  assert.equal(d.flights.length, 0, 'o ștergere mai nouă șterge');
});

test('distKm(): Cluj–București ≈ 324 km', () => {
  assert.ok(Math.abs(core.distKm(46.77, 23.59, 44.43, 26.1) - 324) < 3);
});

test('validări: cazurile comune (zi de zbor, praguri)', () => {
  const v = json('data/test_vectors/validate.json');
  for (const c of v.flight) assert.equal(core.validateFlight(c.site, c.date, c.h, c.m, v.today), c.err, JSON.stringify(c));
  for (const c of v.settings) assert.equal(core.validateSettings(c.s), c.err, JSON.stringify(c.s));
});

test('fetchRetry(): reîncearcă după 5xx și după erori de rețea, apoi renunță', async () => {
  const seq = (...xs) => { let i = 0; const calls = []; return {calls, get: async url => { calls.push(url); const x = xs[i++]; if (x instanceof Error) throw x; return {status: x, ok: x < 400}; }}; };
  let f = seq(503, 200);
  assert.equal((await core.fetchRetry('u', [1, 1], f.get)).status, 200); assert.equal(f.calls.length, 2);
  f = seq(new TypeError('rețea'), 200);
  assert.equal((await core.fetchRetry('u', [1, 1], f.get)).status, 200);
  f = seq(503, 503, 503);
  assert.equal((await core.fetchRetry('u', [1, 1], f.get)).status, 503, 'după ultima încercare întoarce răspunsul'); assert.equal(f.calls.length, 3);
  f = seq(404);
  assert.equal((await core.fetchRetry('u', [1, 1], f.get)).status, 404); assert.equal(f.calls.length, 1, 'un 4xx nu se reîncearcă');
  f = seq(new TypeError('a'), new TypeError('b'), new TypeError('c'));
  await assert.rejects(core.fetchRetry('u', [1, 1], f.get));
});

test('profilurile de limite sunt valide și în ordine', () => {
  for (const [k, p] of Object.entries(core.PROFILES)) assert.equal(core.validateSettings(p), null, k);
  assert.ok(core.PROFILES.elev.marg <= core.PROFILES.pilot.marg && core.PROFILES.pilot.marg <= core.PROFILES.avansat.marg);
  assert.deepEqual(core.PROFILES.pilot, {good: 5, marg: 7, calm: 1, gust: 9, spread: 4, rain: 0.2}, 'Pilot = pragurile implicite de până acum');
});

test('zone: țările din jurul unui punct și țara probabilă (docs/sites/index.json)', () => {
  const index = JSON.parse(read('docs/sites/index.json'));
  assert.equal(index.RO.n, 182);
  const near = core.countriesNear(index, 46.77, 23.59, 100);           // Cluj, 100 km
  assert.ok(near.includes('RO') && near.length < 8, near.join(','));
  assert.ok(!core.countriesNear(index, 46.77, 23.59, 100).includes('FR'));
  assert.equal(core.guessCountry(index, 'en-US', [45.9, 24.9]), 'RO', 'fusul orar contează mai mult decât limba');
  assert.equal(core.guessCountry(index, 'de-AT', null), 'AT');
  assert.equal(core.guessCountry(index, 'en', null), null);
  assert.equal(core.bboxKm(45, 25, [44, 24, 46, 26]), 0);
  assert.ok(core.countriesNear(index, 45.9, 6.1, 50).includes('FR'), 'Annecy');
});
