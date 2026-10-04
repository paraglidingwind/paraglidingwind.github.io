// Teste pentru logica pură din src/core.js: node --test tests/
// Cazurile din data/test_vectors/ sunt aceleași pe care le citește și VectorsTest.kt (Android).
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {core, json} from './load-core.mjs';

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
