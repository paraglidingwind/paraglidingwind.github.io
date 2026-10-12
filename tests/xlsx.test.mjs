// Exportul Excel și backup-ul din Google Drive: același format (aceleași trei foi), iar backup-ul are în plus
// foaia ascunsă „_sync”. Exportul web trebuie să fie octet cu octet cel din Android (Xlsx.kt).
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, readFileSync} from 'node:fs';
import {core} from './load-core.mjs';

// Aceleași date ca XlsxTest.kt.
const sites = [
  {n: 'Liteni', lat: 46.6129, lon: 23.435, alt: 895, o: [0, 1, 2, 2, 1, 0, 0, 0]},
  {n: 'Făureni', lat: 46.9301, lon: 23.5512, alt: null, o: [0, 0, 0, 0, 2, 2, 1, 0], hf: 0},
  {n: 'Rimetea E', lat: 46.4543, lon: 23.5373, alt: 1176, o: [0, 1, 2, 2, 1, 0, 0, 0], hf: 243},
];
const flights = [
  {id: 'a1', date: '2026-10-03', site: 'Liteni', minutes: 95, u: 1759500000000},
  {id: 'a2', date: '2026-09-27', site: 'Rimetea E', minutes: 40, u: 1759000000000},
  {id: 'a3', date: '2026-09-14', site: 'Liteni', minutes: 180, u: 1758000000000},
  {id: 'a4', date: '2026-08-30', site: 'Agriș S', minutes: 25, u: 1757000000000},
];
const tomb = {z9: 1759600000000};
const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const bytes = async parts => new Uint8Array(await core.zipBlob(parts, XLSX).arrayBuffer());
const plain = l => l.map(f => [f.date, f.site, f.minutes]).sort((a, b) => a[0].localeCompare(b[0]));

test('exportul se citește înapoi (zile și situri)', async () => {
  const back = core.readXlsx(await core.unzip((await bytes(core.xlsxParts(flights, sites))).buffer));
  assert.deepEqual(plain(back.flights), plain(flights));
  assert.deepEqual(back.sites.map(s => s.n), sites.map(s => s.n));
});

test('backup-ul din Drive are exact foile exportului, plus „_sync” ascunsă', async () => {
  const exp = new Map(core.xlsxParts(flights, sites)), drv = new Map(core.xlsxParts(flights, sites, {flights, tomb}));
  for (const f of ['xl/worksheets/sheet1.xml', 'xl/worksheets/sheet2.xml', 'xl/worksheets/sheet3.xml', 'xl/styles.xml', '_rels/.rels'])
    assert.equal(drv.get(f), exp.get(f), f + ' identic cu exportul');
  assert.match(drv.get('xl/workbook.xml'), /<sheet name="_sync" sheetId="4" state="hidden" r:id="rId5"\/>/);
  assert.ok(!exp.has('xl/worksheets/sheet4.xml'), 'exportul nu are foaia de sincronizare');
  // Importul Excel citește backup-ul la fel ca exportul.
  const files = await core.unzip((await bytes([...drv])).buffer);
  assert.deepEqual(plain(core.readXlsx(files).flights), plain(flights));
  // Iar sincronizarea își recapătă id-urile, momentele modificărilor și ștergerile.
  const st = core.readSync(files);
  assert.deepEqual(st.flights.map(f => [f.id, f.date, f.site, f.minutes, f.u]).sort(), flights.map(f => [f.id, f.date, f.site, f.minutes, f.u]).sort());
  assert.deepEqual(st.tomb, tomb);
  assert.equal(core.readSync(await core.unzip((await bytes(core.xlsxParts(flights, sites))).buffer)), null, 'exportul simplu nu are „_sync”');
});

test('nume de locuri cu caractere speciale trec prin backup neschimbate', async () => {
  const odd = [{id: 'q1', date: '2026-07-01', site: 'Agriș & „Dealul” <Mare>', minutes: 60, u: 1}];
  const st = core.readSync(await core.unzip((await bytes(core.xlsxParts(odd, [], {flights: odd, tomb: {}}))).buffer));
  assert.equal(st.flights[0].site, odd[0].site);
});

// Arhiva diferă (Android o comprimă, web-ul nu), dar fiecare fișier XML din ea trebuie să fie identic.
test('exportul web = exportul Android (fiecare XML, octet cu octet)', {skip: !existsSync(new URL('../android/app/build/xlsx-test.xlsx', import.meta.url)) && 'rulează întâi testele Android'}, async () => {
  const f = readFileSync(new URL('../android/app/build/xlsx-test.xlsx', import.meta.url));
  const android = await core.unzip(f.buffer.slice(f.byteOffset, f.byteOffset + f.byteLength));   // un Buffer mic împarte memoria cu altele
  const web = await core.unzip((await bytes(core.xlsxParts(flights.map(({u, ...f}) => f), sites))).buffer);
  assert.deepEqual(Object.keys(web).sort(), Object.keys(android).sort());
  for (const k of Object.keys(web)) assert.equal(web[k], android[k], k);
});
