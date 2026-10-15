#!/usr/bin/env node
/* Capturile din ghid (docs/ghid.html, docs/guide.html): Chrome headless prin DevTools Protocol, fără dependențe.
   De fiecare dată aceleași date: prognoza înregistrată și ceasul din tools/guide/demo.json, decolările și jurnalul de exemplu.
   Cercurile albastre numerotate se pun pe elementele reale din pagină și corespund listelor numerotate din ghid.
   Rulare: python3 build.py && node tools/guide-shots.mjs [ro|en] [nume…]   →   docs/img/guide/<limbă>/<nume>.webp
   Rețea: fonturile, hărțile și panta terenului vin de pe internet; prognoza nu. */
import {spawn} from 'node:child_process';
import {createServer} from 'node:http';
import {readFileSync, writeFileSync, mkdirSync, mkdtempSync, rmSync, existsSync, statSync} from 'node:fs';
import {join, extname} from 'node:path';
import {tmpdir} from 'node:os';
import {webpSize} from './guide/webp.mjs';

const root = new URL('..', import.meta.url).pathname, docs = join(root, 'docs');
const demo = JSON.parse(readFileSync(join(root, 'tools/guide/demo.json'), 'utf8'));
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const args = process.argv.slice(2), langs = args.filter(a => a === 'ro' || a === 'en'), only = args.filter(a => a !== 'ro' && a !== 'en');
const MARK = '#2563eb';   // albastru: nu se confundă cu culorile verdictului și nici cu portocaliul aplicației

/* ---------- serverul local pentru docs/ ---------- */
const TYPES = {'.html': 'text/html; charset=utf-8', '.json': 'application/json', '.js': 'text/javascript', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.webp': 'image/webp', '.webmanifest': 'application/manifest+json'};
const server = createServer((q, r) => {
  const p = join(docs, decodeURIComponent(new URL(q.url, 'http://x').pathname).replace(/\/$/, '/index.html'));
  if (!p.startsWith(docs) || !existsSync(p) || !statSync(p).isFile()){ r.writeHead(404); return r.end(); }
  r.writeHead(200, {'content-type': TYPES[extname(p)] || 'application/octet-stream'}); r.end(readFileSync(p));
});
await new Promise(ok => server.listen(0, '127.0.0.1', ok));
const origin = `http://127.0.0.1:${server.address().port}`;

/* ---------- Chrome + un client DevTools minimal ---------- */
const prof = mkdtempSync(join(tmpdir(), 'ghid-'));
const chrome = spawn(CHROME, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${prof}`, '--no-first-run',
  '--no-default-browser-check', '--hide-scrollbars', '--mute-audio', 'about:blank'], {stdio: ['ignore', 'ignore', 'pipe']});
const wsUrl = await new Promise((ok, fail) => {
  let buf = ''; chrome.stderr.on('data', d => { buf += d; const m = buf.match(/ws:\/\/\S+/); if (m) ok(m[0]); });
  chrome.on('exit', () => fail(new Error('Chrome s-a oprit:\n' + buf)));
});
const ws = new WebSocket(wsUrl);
await new Promise((ok, fail) => { ws.onopen = ok; ws.onerror = fail; });
let seq = 0;
const pending = new Map(), waiters = [];
ws.onmessage = e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)){ const [ok, fail] = pending.get(m.id); pending.delete(m.id); m.error ? fail(new Error(m.error.message)) : ok(m.result); return; }
  for (const w of [...waiters]) if (w.method === m.method && w.sessionId === m.sessionId){ waiters.splice(waiters.indexOf(w), 1); w.ok(m.params); }
};
const send = (method, params = {}, sessionId) => new Promise((ok, fail) => {
  const id = ++seq; pending.set(id, [ok, fail]); ws.send(JSON.stringify({id, method, params, sessionId}));
});
const once = (method, sessionId) => new Promise(ok => waiters.push({method, sessionId, ok}));

/* ---------- ce rulează în pagină înaintea aplicației ---------- */
const prelude = state => `(() => {
  const DEMO = ${JSON.stringify({now: demo.now, sites: demo.sites, forecast: demo.forecast})};
  // Ceasul pornește de la ora din demo.json și merge mai departe normal.
  const RD = Date, off = RD.parse(DEMO.now) - RD.now();
  function D(...a){ return new.target ? (a.length ? new RD(...a) : new RD(RD.now() + off)) : new RD(RD.now() + off).toString(); }
  D.prototype = RD.prototype; D.now = () => RD.now() + off; D.parse = RD.parse; D.UTC = RD.UTC;
  window.Date = D;
  // Prognoza: răspunsul înregistrat al decolării cerute (sau al celei mai apropiate dintre cele de exemplu).
  const realFetch = window.fetch;
  window.fetch = (u, o) => {
    const s = String(u && u.url || u);
    if (!s.startsWith('https://api.open-meteo.com/v1/forecast')) return realFetch(u, o);
    const q = new URL(s).searchParams, lat = q.get('latitude').split(',').map(Number), lon = q.get('longitude').split(',').map(Number);
    const out = lat.map((a, i) => { let k = 0;
      DEMO.sites.forEach((x, j) => { if (Math.hypot(x.lat - a, x.lon - lon[i]) < Math.hypot(DEMO.sites[k].lat - a, DEMO.sites[k].lon - lon[i])) k = j; });
      return DEMO.forecast[k]; });
    return Promise.resolve(new Response(JSON.stringify(out.length === 1 ? out[0] : out), {headers: {'content-type': 'application/json'}}));
  };
  if (!sessionStorage.getItem('ghid')){
    sessionStorage.setItem('ghid', '1'); localStorage.clear();
    for (const [k, v] of Object.entries(${JSON.stringify(state)})) localStorage.setItem(k, JSON.stringify(v));
  }
  window.__sleep = ms => new Promise(r => setTimeout(r, ms));
  window.__until = async (f, ms = 10000) => { const t0 = performance.now();
    while (!f()){ if (performance.now() - t0 > ms) throw new Error('timeout: ' + f); await __sleep(50); } };
  window.__el = (sel, i = 0) => { const l = document.querySelectorAll(sel), e = l[i < 0 ? l.length + i : i]; if (!e) throw new Error('lipsește: ' + sel); return e; };
  // Marcajele stau în dialogul deschis (altfel ar fi sub el) și nu primesc atingeri.
  const host = () => [...document.querySelectorAll('dialog[open]')].pop() || document.body;
  const put = (css, text) => { const d = document.createElement('div'); d.className = '__mk';
    Object.assign(d.style, {position: 'fixed', zIndex: 2147483647, pointerEvents: 'none', boxSizing: 'border-box'}, css);
    if (text != null) d.textContent = text; host().append(d); return d; };
  window.__badge = (x, y, n) => put({left: Math.min(innerWidth - 27, Math.max(3, x - 12)) + 'px', top: Math.min(innerHeight - 27, Math.max(3, y - 12)) + 'px',
    width: '24px', height: '24px', borderRadius: '50%', background: '${MARK}', color: '#fff', border: '2px solid #fff',
    boxShadow: '0 1px 4px rgb(0 0 0 / .45)', font: '700 13px/20px system-ui, sans-serif', textAlign: 'center'}, n);
  window.__ring = (r, radius = 12) => put({left: r.left - 3 + 'px', top: r.top - 3 + 'px', width: r.width + 6 + 'px', height: r.height + 6 + 'px',
    border: '3px solid ${MARK}', borderRadius: radius + 'px'});
  window.__line = (x1, y1, x2, y2) => put({left: x1 + 'px', top: y1 - 1 + 'px', width: Math.hypot(x2 - x1, y2 - y1) + 'px', height: '2px',
    background: '${MARK}', transformOrigin: '0 50%', transform: 'rotate(' + Math.atan2(y2 - y1, x2 - x1) + 'rad)'});
  // [selector, poziție, {i, ring, dx, dy, radius}] → cercul cu numărul k+1 lângă element.
  window.__mark = items => {
    for (const e of document.querySelectorAll('.__mk')) e.remove();
    document.activeElement?.blur?.();
    items.forEach(([sel, at = 'tr', o = {}], k) => {
      const r = (typeof sel === 'string' ? __el(sel, o.i) : sel).getBoundingClientRect();
      if (o.ring) __ring(r, o.radius);
      const P = {tr: [r.right, r.top], tl: [r.left, r.top], br: [r.right, r.bottom], bl: [r.left, r.bottom], r: [r.right, r.top + r.height / 2],
        l: [r.left, r.top + r.height / 2], t: [r.left + r.width / 2, r.top], b: [r.left + r.width / 2, r.bottom], c: [r.left + r.width / 2, r.top + r.height / 2]}[at];
      __badge(P[0] + (o.dx || 0), P[1] + (o.dy || 0), o.n ?? k + 1);
    });
  };
  // Foaia de jos deschisă, cu o fâșie din ce e în spatele ei.
  window.__sheet = (pad = 14) => { const r = [...document.querySelectorAll('dialog[open]')].pop().getBoundingClientRect(), y = Math.max(0, r.top - pad);
    return {x: 0, y, width: 360, height: Math.min(innerHeight, r.bottom) - y}; };
  window.__rect = (...sels) => { const rs = sels.map(s => (typeof s === 'string' ? __el(s) : s).getBoundingClientRect());
    const x = Math.min(...rs.map(r => r.left)), y = Math.min(...rs.map(r => r.top));
    return {x, y, width: Math.max(...rs.map(r => r.right)) - x, height: Math.max(...rs.map(r => r.bottom)) - y}; };
})();`;

async function open(lang, {state = {}, h = 740} = {}){
  const {browserContextId} = await send('Target.createBrowserContext');
  const {targetId} = await send('Target.createTarget', {url: 'about:blank', browserContextId});
  const {sessionId} = await send('Target.attachToTarget', {targetId, flatten: true});
  const S = (m, p) => send(m, p, sessionId);
  await S('Page.enable');
  await S('Emulation.setDeviceMetricsOverride', {width: 360, height: h, deviceScaleFactor: 2, mobile: true});
  await S('Emulation.setTimezoneOverride', {timezoneId: 'Europe/Bucharest'});
  await S('Emulation.setLocaleOverride', {locale: lang === 'en' ? 'en-GB' : 'ro-RO'});
  await S('Emulation.setEmulatedMedia', {features: [{name: 'prefers-color-scheme', value: 'light'}]});
  await S('Emulation.setGeolocationOverride', {latitude: demo.geo[0], longitude: demo.geo[1], accuracy: 30});
  await send('Browser.grantPermissions', {origin, permissions: ['geolocation'], browserContextId});
  await S('Page.addScriptToEvaluateOnNewDocument', {source: prelude(state)});
  const loaded = once('Page.loadEventFired', sessionId);
  await S('Page.navigate', {url: `${origin}/?lang=${lang}`});
  await loaded;
  const page = {
    async eval(code){
      const r = await S('Runtime.evaluate', {expression: `(async () => { ${code} })()`, awaitPromise: true, returnByValue: true});
      if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
      return r.result.value;
    },
    async shot(file, clip){
      await page.eval('await __sleep(250)');
      const c = clip ? {x: Math.max(0, clip.x), y: Math.max(0, clip.y), width: Math.min(360, clip.width), height: clip.height, scale: 1} : {x: 0, y: 0, width: 360, height: h, scale: 1};
      const {data} = await S('Page.captureScreenshot', {format: 'webp', quality: 82, clip: c});
      writeFileSync(file, Buffer.from(data, 'base64'));
    },
    close: () => send('Target.disposeBrowserContext', {browserContextId}),
  };
  await page.eval(`await document.fonts.ready; await __until(() => !document.querySelector('#refresh.spin'))`);
  return page;
}

/* ---------- capturile ---------- */
const PILOT = {good: 5, marg: 7, calm: 1, gust: 9, spread: 4, rain: 0.2, from: 8, to: 20};
const BOARD = {'vld.sites': demo.sites, 'vld.settings': PILOT, 'vld.flights': demo.flights};
const board = `await __until(() => document.querySelector('#board .grid, #board .sumrow'));`;
// Indicii decolărilor de exemplu (ordinea din demo.json).
const LITENI = 0, RIMETEA_V = 4, PRIA = 6;
const SHOTS = {
  // Prima pornire: bun venit.
  start: {state: {}, run: `
    await __until(() => document.querySelector('#dlgOnb[open] [data-onb="go"]'));
    __mark([['.onb-lang .seg2', 'r', {dx: 16}], ['[data-onb="go"][data-zone="near"]:not([data-onbview])', 'r', {dx: -16}], ['[data-onbview="map"]', 'r', {dx: -16}],
      ['[data-onb="go"][data-zone="country"]', 'r', {dx: -16}], ['[data-onb="import"]', 'r', {dx: 16}]]);`},
  // Alegerea decolărilor: lângă mine, listă.
  alege: {state: {}, run: `
    await __until(() => document.querySelector('#dlgOnb[open] [data-onb="go"]'));
    __el('[data-onb="go"][data-zone="near"]:not([data-onbview])').click();
    await __until(() => document.querySelectorAll('#pkList .row[data-pk]').length > 5);
    for (const n of ['Agris / Ciurila', 'Agriş', 'Liteni']){ const i = pick.rows.findIndex(x => x.s.n === n); if (i >= 0) __el('#pkList [data-pk="' + i + '"]').click(); }
    __el('#onbBody').scrollTop = 0;
    __mark([['.pk-seg', 'tr', {dx: -6}], ['.chips', 'r', {dx: -6}], ['.pk-tools .seg2', 'tl', {dx: 2}],
      ['#pkList .row[aria-checked="true"] .chk', 'l', {dx: -18}], ['#onbFoot .btn', 'r', {dx: -18}]]);`},
  // Aceeași alegere, pe hartă.
  harta: {state: {}, run: `
    await __until(() => document.querySelector('#dlgOnb[open] [data-onb="go"]'));
    __el('[data-onbview="map"]').click();
    await __until(() => window.L && pick.markers && pick.markers.size > 5, 20000);
    for (const n of ['Agris / Ciurila', 'Agriş', 'Liteni']){ const i = pick.rows.findIndex(x => x.s.n === n); if (i >= 0){ togglePick(pick.rows[i].s); pick.markers.get(i).setStyle(markerStyle(pick.rows[i].s)); } }
    renderPickFoot();
    await __until(() => [...document.querySelectorAll('.leaflet-tile')].every(t => t.classList.contains('leaflet-tile-loaded')), 30000).catch(() => {});
    const i = pick.rows.findIndex(x => x.s.n === 'Dezmir'); pick.markers.get(i).openPopup();
    await __sleep(400);
    __mark([['.leaflet-popup [data-pkm]', 'r', {dx: 8}], ['#onbFoot .btn', 'r', {dx: -18}]]);`},
  // Tabla, azi, pe ore.
  tabla: {state: BOARD, run: `${board}
    scrollToNow(); await __sleep(100);
    __mark([['.day', 'tr', {dx: -6, dy: 4}], ['.viewbar .seg2', 'tr', {dx: -4, dy: 2}], ['.grid tbody th .site', 'br', {i: 1, dx: -12, dy: -10}],
      ['#nowcol', 't', {dy: -8}], ['.cell[data-site="1"][data-h="13"]', 'br', {ring: true, radius: 4, dx: -4, dy: -4}],
      ['#refresh', 'b', {dy: 2}], ['#topLang', 'b', {dy: 2}], ['.tabbar', 't', {dy: 2}]]);`},
  // O celulă mărită, cu părțile ei.
  celula: {state: BOARD, h: 420, run: `${board}
    const src = __el('.cell[data-site="${PRIA}"][data-h="12"]'), st = document.createElement('div');
    st.className = '__mk'; Object.assign(st.style, {position: 'fixed', inset: 0, background: getComputedStyle(document.body).backgroundColor, zIndex: 2147483000, display: 'grid', placeItems: 'center'});
    const c = src.cloneNode(true); Object.assign(c.style, {width: '52px', borderLeft: '0', transform: 'scale(3.3)', borderRadius: '3px', boxShadow: '0 0 0 .3px #0002'});
    st.append(c); document.body.append(st); await __sleep(100);
    const R = e => c.querySelector(e).getBoundingClientRect(), box = c.getBoundingClientRect(), L = box.left - 40, Rr = box.right + 40;
    const at = (sel, side, n) => { const r = R(sel), y = r.top + r.height / 2, x = side < 0 ? L : Rr, px = side < 0 ? r.left + 4 : r.right - 4;
      __line(Math.min(x, px), y, Math.max(x, px), y); __badge(x, y, n); };
    const fr = R('.flag'); __line(L, fr.top + 8, fr.left + 8, fr.top + 8); __badge(L, fr.top + 8, 1);
    at('.sky', -1, 2); at('.arr', 1, 3); at('b', 1, 4); at('small', 1, 5);`, clip: `return __rect('.cell[style*="scale"]')`, pad: [44, 64]},
  // Fișa unei ore: Rimetea V la 11.
  ora: {state: BOARD, h: 860, run: `${board}
    __el('.cell[data-site="${RIMETEA_V}"][data-h="11"]').click(); await __until(() => document.querySelector('#dlgCell[open] .verdict'));
    __mark([['#dlgCell .verdict', 'r', {dx: -16}], ['#dlgCell .reasons', 'tr', {dx: -6, dy: 8}], ['#dlgCell .skyrow', 'r', {dx: -40}],
      ['#dlgCell .facts', 'tr', {dx: -6, dy: 10}], ['#dlgCell .links', 'r', {dx: -8}]]);`, clip: `return __sheet()`},
  // Pe scurt, cu cele mai bune primele.
  scurt: {state: {...BOARD, 'vld.view': 'summary', 'vld.sortBest': true}, run: `${board}
    __mark([['.viewbar .seg2', 'tr', {dx: -4, dy: 2}], ['#sortBest', 'tr', {dx: -4, dy: 2}], ['.sumrow .strip', 'tr', {i: 0, dx: -4, dy: -4}],
      ['.sumrow', 'br', {i: 0, dx: -18, dy: -16}]]);`},
  // Pagina decolării: Liteni (are stație Holfuy).
  sit: {state: BOARD, h: 1060, run: `${board}
    __el('.grid .site[data-site="${LITENI}"]').click(); await __until(() => document.querySelector('#dlgCell[open] .week'));
    __mark([['#cellBody svg.rose', 'bl', {dx: 6, dy: -6}], ['[data-flew]', 'b', {dy: 2}], ['#dlgCell [data-dir-edit]', 'b', {dy: 2}],
      ['#dlgCell .wk', 'tr', {i: 1, dx: -10, dy: 10}], ['details.hfbox summary', 'r', {dx: -16}], ['details.hfbox ~ .links', 'r', {dx: -8}]]);`, clip: `return __sheet()`},
  // Decolare fără direcții: Pria, cu sugestia din pantă.
  'fara-directii': {state: BOARD, h: 900, run: `${board}
    __el('.grid .site[data-site="${PRIA}"]').click(); await __until(() => document.querySelector('#dlgCell[open] [data-dir-suggest]'));
    __el('[data-dir-suggest]').click(); await __until(() => document.querySelector('#dirEditor .compass') && !/…/.test(__el('#dirEditor .hint').textContent), 20000);
    __el('#dirEditor').scrollIntoView({block: 'end'}); await __sleep(100);
    __mark([['.nodir-box [data-dir-suggest]', 'r', {dx: 16}], ['#dirEditor [data-drose="2"]', 'r', {dx: 18}], ['#dirSave', 'tl', {dx: 4, dy: 4}]]);`,
    clip: `const r = __rect('.nodir-box', '#dirEditor'); return {x: 0, y: r.y - 12, width: 360, height: r.height + 24}`},
  // Lista de decolări.
  situri: {state: BOARD, h: 800, run: `${board}
    __el('#edit').click(); await __until(() => document.querySelector('#dlgEdit[open] #myList .row'));
    __mark([['#myList .row .acts', 'l', {i: 1, dx: -6}], ['#pickOpen', 'tr', {dx: -4, dy: 4}], ['#resetSites', 'tr', {dx: -4, dy: 4}], ['#manualBox summary', 'r', {dx: -10}]]);`,
    clip: `return __sheet()`},
  // Setări.
  setari: {state: BOARD, h: 1500, run: `${board}
    __el('#settingsOpen').click(); await __until(() => document.querySelector('#dlgSettings[open] .prof'));
    __mark([['#profiles', 'tr', {dx: -8, dy: 6}], ['#customBox summary', 'r', {dx: -10}], ['#dlgSettings .two', 'tr', {dx: -8, dy: 6}],
      ['#unitSeg', 'tr', {dx: -4, dy: 4}], ['#models', 'tr', {dx: -8, dy: 6}], ['#langSeg', 'tr', {dx: -4, dy: 4}]]);`,
    clip: `return __sheet()`},
  // Jurnalul, pe zile.
  jurnal: {state: BOARD, h: 900, run: `${board}
    __el('#log').click(); await __until(() => document.querySelector('#dlgLog[open] .stats'));
    __mark([['#logBody .gbtn', 'tr', {dx: -4, dy: 4}], ['#logBody .stats', 'tr', {dx: -6, dy: 6}], ['#addFlight', 'tr', {dx: -4, dy: 4}],
      ['#exportXlsx', 'tr', {dx: -4, dy: 4}], ['#logBody .seg', 'tr', {dx: -6, dy: 4}], ['#logBody .list .row', 'b', {i: 0}]]);`,
    clip: `return __sheet()`},
  // O zi de zbor nouă.
  zi: {state: BOARD, run: `${board}
    __el('#log').click(); await __until(() => document.querySelector('#addFlight')); __el('#addFlight').click();
    await __until(() => document.querySelector('#dlgFlight[open]'));
    __el('#flSite').value = 'Agriș N'; __el('#flH').value = 1; __el('#flM').value = 20; showFlTotal();
    __mark([['#flSite', 'tr', {dx: -44, dy: 2}], ['#flDate', 'tr', {dx: -44, dy: 2}], ['#dlgFlight .two', 'tr', {dx: -8, dy: 4}], ['#dlgFlight .btn.primary', 'r', {dx: 16}]]);`,
    clip: `return __sheet()`},
  // Jurnalul, pe ani.
  ani: {state: BOARD, h: 900, run: `${board}
    __el('#log').click(); await __until(() => document.querySelector('#dlgLog[open] [data-view="years"]'));
    __el('[data-view="years"]').click(); await __until(() => document.querySelector('.yrow'));
    __el('.yrow').scrollIntoView({block: 'center'}); await __sleep(100);
    __mark([['.yrow', 't', {i: 0}], ['.yrow .ybars', 'r', {i: 0, dx: -6}]]);`,
    clip: `const r = __rect('#dlgLog .seg', '.yrow:last-of-type'); return {x: 0, y: r.y - 10, width: 360, height: r.height + 20}`},
};

/* După capturi, width/height din pagina ghidului (ro: ghid.html, en: guide.html) urmează imaginile noi (fără salturi la încărcare). */
const PAGES = {ro: 'ghid.html', en: 'guide.html'};
function updateSizes(lang){
  const f = join(docs, PAGES[lang]);
  writeFileSync(f, readFileSync(f, 'utf8').replace(/<img src="(img\/guide\/[a-z]+\/[\w-]+\.webp)" width="\d+" height="\d+"/g, (_, src) => {
    const z = webpSize(readFileSync(join(docs, src))); return `<img src="${src}" width="${z.width}" height="${z.height}"`; }));
}
const out = lang => { const d = join(docs, 'img/guide', lang); mkdirSync(d, {recursive: true}); return d; };
let failed = 0;
try {
  for (const lang of langs.length ? langs : ['ro', 'en']){
    for (const [name, s] of Object.entries(SHOTS)){
      if (only.length && !only.includes(name)) continue;
      const page = await open(lang, {state: s.state, h: s.h || 740});
      try {
        await page.eval(s.run);
        let clip = s.clip ? await page.eval(s.clip) : null;
        if (clip && s.pad) clip = {x: clip.x - s.pad[1], y: clip.y - s.pad[0], width: clip.width + 2 * s.pad[1], height: clip.height + 2 * s.pad[0]};
        await page.shot(join(out(lang), name + '.webp'), clip);
        console.log(lang, name, 'ok');
      } catch (e) { failed++; console.error(lang, name, 'EROARE', e.message); }
      finally { await page.close(); }
    }
    updateSizes(lang);
  }
} finally {
  ws.close(); server.close();
  await new Promise(ok => { chrome.once('exit', ok); chrome.kill(); });
  rmSync(prof, {recursive: true, force: true, maxRetries: 5});
}
process.exit(failed ? 1 : 0);
