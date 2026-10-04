/* ---------- logica pură ----------
   Reguli de zbor, cer, rezumatul zilei, coordonate, distanțe, unirea jurnalelor. Fără DOM.
   build.py inserează fișierul în pagină; tests/core.test.mjs îl testează cu node --test.
   Folosește t() și DIRS din pagină (sau din test). Aceleași reguli ca Flyability.kt, Sky.kt și DaySummary.kt:
   orice schimbare începe cu un caz nou în data/test_vectors/, citit de ambele platforme. */
const RANK = {night:-2, na:-1, go:0, calm:1, maybe:2, no:3};
/* Pragurile avertizărilor (SIG-01, SIG-02) și fereastra minimă (SIG-05). Fixe deocamdată; aceleași în Flyability.kt. */
const ALT_MARG = 8, ALT_NO = 12, ALT_BEHIND = 5;      // vânt la ~1500 m (850 hPa), m/s
const CAPE_RISK = 800, LI_RISK = -2;                  // supradezvoltare
const STORM_AHEAD = 2;                                // ore înainte de o furtună prognozată
const MIN_WINDOW = 2;                                 // ore favorabile la rând ca ziua să fie „Favorabil”

function sectorOf(deg){ return Math.round(((deg % 360) + 360) % 360 / 45) % 8; }
const knownDirs = site => site.o.some(v => v > 0);

/* Starea unei ore = cea mai rea componentă. ctx.next = următoarele ore (pentru furtunile care vin).
   warn: 'alt' (vânt la altitudine), 'storm' (supradezvoltare / furtună), 'nodir' (decolare fără direcții). */
function rate(site, w, s, ctx = {}){
  if (!w || w.ws == null || w.wd == null) return {st:'na', why:[], warn:[]};
  if (w.day === 0) return {st:'night', why:[['night', t('Noapte: fără lumină de zbor')]], warn:[]};
  const why = [], warn = []; let st = 'go';
  const bump = (to, msg, kind) => { why.push([to, msg, kind]); if (kind && !warn.includes(kind)) warn.push(kind); if (RANK[to] > RANK[st]) st = to; };
  const known = knownDirs(site);
  if (w.ws < s.calm){
    st = 'calm'; why.push(['calm', t('Vânt slab ({v} m/s), direcția nu contează', {v: w.ws.toFixed(1)})]);
  } else {
    const sec = sectorOf(w.wd), sc = site.o[sec];
    if (!known) bump('maybe', t('Decolarea nu are direcții setate: verifică dacă vântul din {d} e bun aici', {d: DIRS[sec]}), 'nodir');
    else if (sc === 2) why.push(['go', t('Direcție bună ({d})', {d: DIRS[sec]})]);
    else if (sc === 1) bump('maybe', t('Direcție marginală ({d})', {d: DIRS[sec]}));
    else bump('no', t('Direcție nepotrivită ({d}) pentru decolare', {d: DIRS[sec]}));
    if (w.ws > s.marg) bump('no', t('Vânt prea tare: {v} m/s (peste {lim})', {v: w.ws.toFixed(1), lim: s.marg}));
    else if (w.ws > s.good) bump('maybe', t('Vânt tare: {v} m/s (peste {lim})', {v: w.ws.toFixed(1), lim: s.good}));
  }
  if (w.wg != null){
    if (w.wg > s.gust) bump('no', t('Rafale {v} m/s (peste {lim})', {v: w.wg.toFixed(1), lim: s.gust}));
    else if (w.wg - w.ws > s.spread) bump('maybe', t('Turbulent: rafalele depășesc vântul cu {v} m/s', {v: (w.wg-w.ws).toFixed(1)}));
  }
  if (w.pr != null && w.pr > s.rain) bump('no', t('Ploaie {v} mm/h', {v: w.pr.toFixed(1)}));
  // SIG-01: vântul de la ~1500 m. Tare, sau din spatele decolării (zonă de sub vânt, rotori).
  if (w.w8 != null && w.d8 != null){
    const s8 = sectorOf(w.d8);
    if (w.w8 > ALT_NO) bump('no', t('Vânt foarte tare la ~1500 m: {v} m/s (peste {lim})', {v: w.w8.toFixed(1), lim: ALT_NO}), 'alt');
    else if (w.w8 > ALT_MARG) bump('maybe', t('Vânt tare la ~1500 m: {v} m/s (peste {lim})', {v: w.w8.toFixed(1), lim: ALT_MARG}), 'alt');
    if (known && site.o[s8] === 0 && w.w8 > ALT_BEHIND)
      bump('maybe', t('Vânt la ~1500 m din spatele decolării ({d}, {v} m/s): risc de rotori', {d: DIRS[s8], v: w.w8.toFixed(1)}), 'alt');
  }
  // SIG-02: furtună acum sau în următoarele ore; altfel instabilitate mare (supradezvoltare).
  const storm = [w, ...(ctx.next || [])].slice(0, STORM_AHEAD + 1).findIndex(x => x && x.code >= 95);
  if (storm === 0) bump('no', t('Furtună prognozată la această oră'), 'storm');
  else if (storm === 1) bump('no', t('Furtună prognozată în ora următoare'), 'storm');
  else if (storm > 1) bump('no', t('Furtună prognozată peste {n} ore', {n: storm}), 'storm');
  else if (w.cape != null && w.li != null && w.cape > CAPE_RISK && w.li < LI_RISK)
    bump('maybe', t('Risc de supradezvoltare: CAPE {c} J/kg, LI {li}', {c: Math.round(w.cape), li: String(Math.round(w.li * 10) / 10).replace('-', '−')}), 'storm');
  return {st, why, warn};
}

/* Rezumatul unei zile. hs = orele afișate, ws = datele lor, extra = orele de după (furtuni care vin).
   „Favorabil” doar pentru minimum MIN_WINDOW ore bune la rând; o oră izolată apare ca „Doar …”. */
function summarize(site, hs, ws, s, extra = []){
  const all = [...ws, ...extra];
  const rs = ws.map((w, i) => rate(site, w, s, {next: [all[i + 1], all[i + 2]]})), sts = rs.map(r => r.st);
  const runs = x => { const out = []; let a = -1;
    sts.forEach((y, i) => { if (y === x){ if (a < 0) a = i; } else if (a >= 0){ out.push([a, i - 1]); a = -1; } });
    if (a >= 0) out.push([a, sts.length - 1]);
    return out; };
  const longest = l => l.reduce((b, r) => !b || r[1] - r[0] > b[1] - b[0] ? r : b, null);
  const span = ([a, b]) => a === b ? t('ora {h}', {h: hs[a]}) : `${hs[a]}–${hs[b]}`;
  const n = ([a, b]) => b - a + 1;
  const wind = ([a, b]) => {
    const part = ws.slice(a, b + 1).filter(w => w && w.ws != null && w.wd != null), cnt = new Map();
    part.forEach(w => { const k = DIRS[sectorOf(w.wd)]; cnt.set(k, (cnt.get(k) || 0) + 1); });
    let dir = '–', mx = 0; for (const [k, v] of cnt) if (v > mx){ dir = k; mx = v; }
    const lo = Math.round(Math.min(...part.map(w => w.ws))), hi = Math.round(Math.max(...part.map(w => w.ws)));
    return t('vânt din {d} {r} m/s', {d: dir, r: lo === hi ? lo : lo + '–' + hi});
  };
  const goRuns = runs('go'), g = longest(goRuns.filter(r => n(r) >= MIN_WINDOW)), m = longest(runs('maybe')), c = longest(runs('calm'));
  const si = rs.findIndex(r => r.warn.includes('storm'));
  const tail = si >= 0 ? t(' · risc de supradezvoltare de la {h}:00', {h: hs[si]}) : '';
  if (g) return {sts, st:'go', text: t('Favorabil {s} ({n}) · {w}', {s: span(g), n: n(g) === 1 ? t('1 oră') : t('{n} ore', {n: n(g)}), w: wind(g)}) + tail};
  if (goRuns.length) return {sts, st:'maybe', text: t('Doar {s}, sub fereastra minimă de {n} ore', {s: goRuns.map(span).join(', '), n: MIN_WINDOW})
    + (m ? t(' · marginal {s}', {s: span(m)}) : '') + tail};
  if (m) return {sts, st:'maybe', text: t('Cel mult marginal: {s} · {w}', {s: span(m), w: wind(m)}) + tail};
  if (sts.every(x => x === 'na' || x === 'night')) return {sts, st:'na', text: t('Fără date')};
  if (c) return {sts, st:'calm', text: t('Nicio fereastră bună · calm (sub {c} m/s) {s}', {c: s.calm, s: span(c)}) + tail};
  return {sts, st:'no', text: t('Nicio fereastră bună') + tail};
}

/* ---------- cer ---------- */
const WMO = {0:'Senin',1:'Predominant senin',2:'Parțial noros',3:'Înnorat',45:'Ceață',48:'Ceață cu chiciură',
  51:'Burniță slabă',53:'Burniță',55:'Burniță densă',56:'Burniță înghețată',57:'Burniță înghețată',
  61:'Ploaie slabă',63:'Ploaie',65:'Ploaie puternică',66:'Ploaie înghețată',67:'Ploaie înghețată',
  71:'Ninsoare slabă',73:'Ninsoare',75:'Ninsoare puternică',77:'Grăunțe de zăpadă',
  80:'Averse slabe',81:'Averse',82:'Averse violente',85:'Averse de ninsoare',86:'Averse de ninsoare',
  95:'Furtună',96:'Furtună cu grindină',99:'Furtună cu grindină'};
const CLOUD_NOTES = {
  'Cumulus':'Nori de convecție: semn de termice.',
  'Cumulus congestus':'Dezvoltare verticală puternică: risc de supradezvoltare și averse.',
  'Cumulonimbus':'Nor de furtună: nu se zboară.',
  'Stratocumulus':'Strat de nori joși: termice slabe.',
  'Stratus':'Plafon jos și uniform.',
  'Altocumulus':'Nori la altitudine medie.',
  'Altostratus':'Strat la altitudine medie: umbrește solul, termice slabe.',
  'Cirrus':'Nori înalți subțiri: adesea anunță un front.',
  'Cirrostratus':'Văl înalt: slăbește termicele.'};
function sky(w){
  const code = w.code ?? -1, cc = w.cc ?? 0, lo = w.lo ?? 0, mi = w.mi ?? 0, hi = w.hi ?? 0, cape = w.cape ?? 0, day = w.day !== 0;
  let type = null; const top = Math.max(lo, mi, hi);
  if (top >= 20){
    if (lo === top) type = (day && cape >= 200) ? (cape >= 1000 ? 'Cumulus congestus' : 'Cumulus') : (lo >= 80 ? 'Stratus' : 'Stratocumulus');
    else if (mi === top) type = mi >= 80 ? 'Altostratus' : 'Altocumulus';
    else type = hi >= 80 ? 'Cirrostratus' : 'Cirrus';
  }
  let icon, label;
  if (code >= 95){ icon = 'thunder'; label = WMO[code]; type = 'Cumulonimbus'; }
  else if ([71,73,75,77,85,86].includes(code)){ icon = 'snow'; label = WMO[code]; }
  else if (code >= 51 && code <= 82){ icon = 'rain'; label = WMO[code]; }
  else if (code === 45 || code === 48){ icon = 'fog'; label = WMO[code]; }
  else {
    if (cc < 15){ icon = day ? 'sun' : 'moon'; label = 'Senin'; }
    else if (cc < 50){ icon = day ? 'partly' : 'cloud'; label = 'Parțial noros'; }
    else if (cc < 85){ icon = 'cloud'; label = 'Mai mult noros'; }
    else { icon = 'overcast'; label = 'Înnorat'; }
    if (day && type && type.startsWith('Cumulus') && cc < 85) icon = 'cumulus';
    else if (day && (type === 'Cirrus' || type === 'Cirrostratus') && lo < 20 && mi < 20) icon = 'cirrus';
  }
  return {icon, label: t(label || 'Necunoscut'), type};
}

/* ---------- locuri ---------- */
function distKm(a, b, c, d){
  const r = x => x * Math.PI / 180, h = Math.sin(r(c-a)/2)**2 + Math.cos(r(a))*Math.cos(r(c))*Math.sin(r(d-b)/2)**2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}
/* Coordonate din text liber sau dintr-un link Google Maps. Identic cu parseCoords() din Kotlin. */
function parseCoords(str){
  let s = str; try{ s = decodeURIComponent(str) }catch{}
  const m = s.match(/!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/) || s.match(/@(-?\d+\.\d+),(-?\d+\.\d+)/)
    || s.match(/[?&](?:q|query|ll|center)=(-?\d+\.\d+),\s*(-?\d+\.\d+)/) || s.match(/(-?\d{1,2}\.\d+)\s*[,;\s]\s*(-?\d{1,3}\.\d+)/);
  if (!m) return null;
  const lat = +m[1], lon = +m[2];
  return (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) ? {lat, lon} : null;
}

/* ---------- jurnal ----------
   Unirea a două jurnale (local + Drive): pe fiecare id câștigă modificarea mai nouă, ștergerile mai noi șterg,
   iar aceeași zi notată separat pe două dispozitive (alt id, aceeași dată + locație + durată) rămâne o singură dată. */
function syncMerge(a, b){
  const tb = {...(b.tomb || {})};
  for (const [id, u] of Object.entries(a.tomb || {})) if (!(tb[id] >= u)) tb[id] = u;
  const fa = a.flights || [], fb = b.flights || [], ida = new Set(fa.map(f => f.id)), idb = new Set(fb.map(f => f.id));
  const k = f => `${f.date}|${f.site}|${f.minutes}`, cnt = new Map();
  for (const f of fb) if (!ida.has(f.id)) cnt.set(k(f), (cnt.get(k(f)) || 0) + 1);
  const drop = new Set();
  for (const f of fa) if (!idb.has(f.id)){ const c = cnt.get(k(f)) || 0; if (c > 0){ cnt.set(k(f), c - 1); drop.add(f.id); } }
  const by = new Map();
  for (const f of [...fb, ...fa]){ if (drop.has(f.id)) continue; const cur = by.get(f.id); if (!cur || (f.u || 0) > (cur.u || 0)) by.set(f.id, f); }
  for (const [id, u] of Object.entries(tb)){ const f = by.get(id); if (f && (f.u || 0) <= u) by.delete(id); }
  return {flights: [...by.values()], tomb: tb};
}
