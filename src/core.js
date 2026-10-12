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

/* ---------- rețea ---------- */
/* FIA-02: fiecare încercare are 15 s; la o eroare de rețea sau 5xx mai încercăm de 2 ori, după 2 s și după 6 s. */
async function fetchRetry(url, waits = [2000, 6000], get = (u, o) => fetch(u, o)){
  for (let i = 0; ; i++){
    const ac = new AbortController(), timer = setTimeout(() => ac.abort(), 15000);
    try {
      const r = await get(url, {signal: ac.signal});
      if (r.status < 500 || i >= waits.length) return r;
    } catch (e) {
      if (i >= waits.length) throw e;
    } finally { clearTimeout(timer); }
    await new Promise(ok => setTimeout(ok, waits[i]));
  }
}

/* ---------- validări (aceleași în Flight.kt / Model.kt) ---------- */
/* O zi de zbor: locul, data (nu în viitor), timpul în aer (cel mult 24 h). */
function validateFlight(site, date, h, m, today){
  if (!String(site || '').trim()) return t('Alege sau scrie locația.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) return t('Alege data.');
  if (date > today) return t('Data nu poate fi în viitor.');
  if (h < 0 || m < 0) return t('Orele și minutele nu pot fi negative.');
  if (h * 60 + m <= 0) return t('Scrie cât timp ai stat în aer.');
  if (h * 60 + m > 24 * 60) return t('O zi are cel mult 24 de ore în aer.');
  return null;
}
/* Pragurile: pozitive și în ordine (calm < bun ≤ marginal, rafalele maxime cel puțin cât vântul bun). */
function validateSettings(s){
  if (['good', 'marg', 'calm', 'gust', 'spread', 'rain'].some(k => !(s[k] >= 0))) return t('Pragurile trebuie să fie numere pozitive.');
  if (s.calm >= s.good) return t('„Calm sub” trebuie să fie mai mic decât „Bun până la”.');
  if (s.good > s.marg) return t('„Bun până la” nu poate depăși „Marginal până la”.');
  if (s.gust < s.good) return t('„Rafale maxime” nu poate fi sub „Bun până la”.');
  return null;
}

/* ---------- profiluri și zone (ONB-02, ONB-05) ---------- */
/* Limitele pentru fiecare tip de pilot; aceleași în Model.kt. Orele afișate (from/to) nu țin de profil. */
const PROFILES = {
  elev:    {good:4, marg:5, calm:1, gust:6,  spread:3, rain:0.2},
  pilot:   {good:5, marg:7, calm:1, gust:9,  spread:4, rain:0.2},
  avansat: {good:7, marg:9, calm:1, gust:11, spread:5, rain:0.2},
};
/* Distanța de la un punct la un dreptunghi [latMin, lonMin, latMax, lonMax]; 0 = înăuntru. */
function bboxKm(lat, lon, [a, b, c, d]){
  return distKm(lat, lon, Math.min(c, Math.max(a, lat)), Math.min(d, Math.max(b, lon)));
}
/* Celulele de 2°×2° din docs/sites/index.json: „46,22” = lat 46–48, lon 22–24. */
const cellBox = k => { const [a, b] = k.split(',').map(Number); return [a, b, a + 2, b + 2]; };
const cellOf = (lat, lon) => `${Math.floor(lat / 2) * 2},${Math.floor(lon / 2) * 2}`;
/* Țările care pot avea decolări la cel mult km de punct: doar fișierele lor se descarcă. */
function countriesNear(index, lat, lon, km){
  return Object.keys(index).filter(cc => Object.keys(index[cc].c).some(k => bboxKm(lat, lon, cellBox(k)) <= km));
}
/* Țara probabilă: cea cu cele mai multe decolări în celula fusului orar; altfel regiunea din limba telefonului (ro-RO → RO). */
function guessCountry(index, lang, point){
  if (point){
    const k = cellOf(point[0], point[1]);
    const best = Object.keys(index).filter(cc => cc !== 'XX' && index[cc].c[k]).sort((x, y) => index[y].c[k] - index[x].c[k])[0];
    if (best) return best;
  }
  const r = (String(lang || '').split(/[-_]/)[1] || '').toUpperCase();
  return index[r] ? r : null;
}

/* ---------- export Excel ----------
   Fișier .xlsx construit direct (XML + arhivă zip fără compresie), fără biblioteci.
   Aceeași structură ca Xlsx.kt din aplicația Android. */
const xmlEsc = t => String(t).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const excelDate = d => { const [y, m, dd] = d.split('-').map(Number); return (Date.UTC(y, m - 1, dd) - Date.UTC(1899, 11, 30)) / 864e5; };
const col = i => 'ABCDEFG'[i];
function xlRow(r, cells){
  return `<row r="${r}">` + cells.map((c, i) => {
    const ref = col(i) + r;
    if (c.f) return `<c r="${ref}" s="${c.s}"><f>${c.f}</f><v>${c.v}</v></c>`;
    if (typeof c.v === 'string') return `<c r="${ref}" t="inlineStr"${c.s ? ` s="${c.s}"` : ''}><is><t>${xmlEsc(c.v)}</t></is></c>`;
    return `<c r="${ref}"${c.s ? ` s="${c.s}"` : ''}><v>${c.v}</v></c>`;
  }).join('') + '</row>';
}
function xlSheet(widths, rows){
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    + '<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
    + '<cols>' + widths.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join('') + '</cols>'
    + '<sheetData>' + rows.map((c, i) => xlRow(i + 1, c)).join('') + '</sheetData></worksheet>';
}
/* Stiluri: 1 = antet (bold), 2 = dată zz.ll.aaaa, 3 = durată [h]:mm, 4 = durată bold. */
const sumMinutes = list => list.reduce((a, f) => a + f.minutes, 0);
/* sync = {flights, tomb}: doar pentru backup-ul din Google Drive. Adaugă foaia ascunsă „_sync” (id-ul și momentul
   ultimei modificări al fiecărei zile, plus ștergerile), ca sincronizarea să știe ce s-a schimbat. Primele trei foi
   sunt exact cele din export; fără sync, fișierul e octet cu octet exportul (la fel ca Xlsx.kt). */
function xlsxParts(list, siteList, sync){
  const H = s => ({v: s, s: 1});
  const days = [...list].sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id));
  const n = days.length;
  const s1 = [[H(t('Data')), H(t('Locație')), H(t('Timp în aer')), H(t('Minute'))],
    ...days.map(f => [{v: excelDate(f.date), s: 2}, {v: f.site}, {v: f.minutes / 1440, s: 3}, {v: f.minutes}]),
    [H('Total'), {v: ''}, {f: `SUM(C2:C${n + 1})`, v: sumMinutes(days) / 1440, s: 4}, {f: `SUM(D2:D${n + 1})`, v: sumMinutes(days), s: 1}]];
  const by = new Map();
  for (const f of list){ const x = by.get(f.site) || {site: f.site, n: 0, m: 0, last: ''}; x.n++; x.m += f.minutes; if (f.date > x.last) x.last = f.date; by.set(f.site, x); }
  const s2 = [[H(t('Locație')), H(t('Zile de zbor')), H(t('Timp în aer')), H(t('Ultima zi'))],
    ...[...by.values()].sort((a, b) => b.m - a.m).map(x => [{v: x.site}, {v: x.n}, {v: x.m / 1440, s: 3}, {v: excelDate(x.last), s: 2}])];
  const dirsOf = (o, v) => o.map((x, i) => x === v ? DIRS[i] : '').filter(Boolean).join(', ');
  const s3 = [[H(t('Nume')), H(t('Latitudine')), H(t('Longitudine')), H(t('Altitudine')), H(t('Direcții bune')), H(t('Marginal')), H('Holfuy')],
    ...siteList.map(st => { const hf = st.hf ?? -1;
      return [{v: st.n}, {v: st.lat}, {v: st.lon}, st.alt != null ? {v: st.alt} : {v: ''}, {v: dirsOf(st.o, 2)}, {v: dirsOf(st.o, 1)}, hf > 0 ? {v: hf} : {v: hf === 0 ? t('fără') : ''}]; })];
  const NS = 'http://schemas.openxmlformats.org', X = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n';
  const WS = 'application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml';
  const s4 = sync && [[{v: 'id'}, {v: 'data'}, {v: 'locatie'}, {v: 'minute'}, {v: 'modificat'}, {v: 'tip'}],
    ...[...sync.flights].sort((a, b) => a.id.localeCompare(b.id)).map(f => [{v: f.id}, {v: f.date}, {v: f.site}, {v: f.minutes}, {v: f.u || 0}, {v: 'zi'}]),
    ...Object.entries(sync.tomb || {}).sort().map(([id, u]) => [{v: id}, {v: ''}, {v: ''}, {v: ''}, {v: u}, {v: 'sters'}])];
  const ov4 = s4 ? `<Override PartName="/xl/worksheets/sheet4.xml" ContentType="${WS}"/>` : '';
  const sh4 = s4 ? `<sheet name="_sync" sheetId="4" state="hidden" r:id="rId5"/>` : '';
  const rel4 = s4 ? `<Relationship Id="rId5" Type="${NS}/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>` : '';
  return [
    ['[Content_Types].xml', X + `<Types xmlns="${NS}/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="${WS}"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="${WS}"/><Override PartName="/xl/worksheets/sheet3.xml" ContentType="${WS}"/>${ov4}<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>`],
    ['_rels/.rels', X + `<Relationships xmlns="${NS}/package/2006/relationships"><Relationship Id="rId1" Type="${NS}/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`],
    ['xl/workbook.xml', X + `<workbook xmlns="${NS}/spreadsheetml/2006/main" xmlns:r="${NS}/officeDocument/2006/relationships"><sheets><sheet name="${xmlEsc(t('Zile de zbor'))}" sheetId="1" r:id="rId1"/><sheet name="${xmlEsc(t('Pe locații'))}" sheetId="2" r:id="rId2"/><sheet name="${xmlEsc(t('Situri'))}" sheetId="3" r:id="rId3"/>${sh4}</sheets><calcPr calcId="0" fullCalcOnLoad="1"/></workbook>`],
    ['xl/_rels/workbook.xml.rels', X + `<Relationships xmlns="${NS}/package/2006/relationships"><Relationship Id="rId1" Type="${NS}/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="${NS}/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="${NS}/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/><Relationship Id="rId4" Type="${NS}/officeDocument/2006/relationships/styles" Target="styles.xml"/>${rel4}</Relationships>`],
    ['xl/styles.xml', X + `<styleSheet xmlns="${NS}/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="dd.mm.yyyy"/><numFmt numFmtId="165" formatCode="[h]:mm"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`],
    ['xl/worksheets/sheet1.xml', xlSheet([12, 26, 12, 10], s1)],
    ['xl/worksheets/sheet2.xml', xlSheet([26, 13, 12, 12], s2)],
    ['xl/worksheets/sheet3.xml', xlSheet([24, 11, 11, 11, 18, 18, 9], s3)],
    ...(s4 ? [['xl/worksheets/sheet4.xml', xlSheet([16, 12, 24, 9, 16, 7], s4)]] : []),
  ];
}
const CRC_T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++){ let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xFFFFFFFF; for (let i = 0; i < b.length; i++) c = CRC_T[(c ^ b[i]) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
/* Arhivă zip simplă (fără compresie): antet local + director central + sfârșit. */
function zipBlob(files, type){
  const enc = new TextEncoder(), parts = [], central = []; let off = 0;
  for (const [fname, text] of files){
    const name = enc.encode(fname), data = enc.encode(text), crc = crc32(data), n = data.length;
    const h = new DataView(new ArrayBuffer(30));
    h.setUint32(0, 0x04034b50, true); h.setUint16(4, 20, true); h.setUint16(6, 0x0800, true); h.setUint16(12, 0x21, true);
    h.setUint32(14, crc, true); h.setUint32(18, n, true); h.setUint32(22, n, true); h.setUint16(26, name.length, true);
    parts.push(new Uint8Array(h.buffer), name, data);
    const c = new DataView(new ArrayBuffer(46));
    c.setUint32(0, 0x02014b50, true); c.setUint16(4, 20, true); c.setUint16(6, 20, true); c.setUint16(8, 0x0800, true); c.setUint16(14, 0x21, true);
    c.setUint32(16, crc, true); c.setUint32(20, n, true); c.setUint32(24, n, true); c.setUint16(28, name.length, true); c.setUint32(42, off, true);
    central.push(new Uint8Array(c.buffer), name);
    off += 30 + name.length + n;
  }
  const size = central.reduce((a, b) => a + b.length, 0), e = new DataView(new ArrayBuffer(22));
  e.setUint32(0, 0x06054b50, true); e.setUint16(8, files.length, true); e.setUint16(10, files.length, true); e.setUint32(12, size, true); e.setUint32(16, off, true);
  return new Blob([...parts, ...central, new Uint8Array(e.buffer)], {type});
}
/* ---------- import Excel ----------
   Citește un export (al nostru sau re-salvat din Excel). Același algoritm ca readXlsx() din Xlsx.kt. */
async function unzip(buf){
  const dv = new DataView(buf), u8 = new Uint8Array(buf), dec = new TextDecoder(), out = {};
  let eocd = -1;
  for (let i = buf.byteLength - 22; i >= Math.max(0, buf.byteLength - 65557); i--) if (dv.getUint32(i, true) === 0x06054b50){ eocd = i; break; }
  if (eocd < 0) throw new Error('nu e zip');
  let p = dv.getUint32(eocd + 16, true);
  for (let k = dv.getUint16(eocd + 10, true); k > 0; k--){
    if (dv.getUint32(p, true) !== 0x02014b50) throw new Error('zip stricat');
    const method = dv.getUint16(p + 10, true), size = dv.getUint32(p + 20, true), nlen = dv.getUint16(p + 28, true), off = dv.getUint32(p + 42, true);
    const name = dec.decode(u8.subarray(p + 46, p + 46 + nlen));
    p += 46 + nlen + dv.getUint16(p + 30, true) + dv.getUint16(p + 32, true);
    if (!/\.(xml|rels)$/.test(name) || (method !== 0 && method !== 8)) continue;
    const start = off + 30 + dv.getUint16(off + 26, true) + dv.getUint16(off + 28, true);
    let data = u8.subarray(start, start + size);
    if (method === 8) data = new Uint8Array(await new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).arrayBuffer());
    out[name] = dec.decode(data);
  }
  return out;
}
const xmlUnesc = t => t.replace(/&(#x?[0-9a-fA-F]+|lt|gt|quot|apos|amp);/g, (_, e) =>
  e[0] === '#' ? String.fromCodePoint(e[1] === 'x' ? parseInt(e.slice(2), 16) : +e.slice(1)) : ({lt:'<', gt:'>', quot:'"', apos:"'", amp:'&'})[e]);
const xlTexts = x => [...x.matchAll(/<t(?:\s[^>]*)?>(.*?)<\/t>/gs)].map(m => xmlUnesc(m[1])).join('');
const xlAttr = (tag, name) => (tag.match(new RegExp('\\s' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '="([^"]*)"')) || [])[1];
function xlCells(xml, shared){
  return [...xml.matchAll(/<row\b([^>]*?)(?:\/>|>(.*?)<\/row>)/gs)].map(r => {
    const m = {}; let pos = 0;
    for (const c of (r[2] || '').matchAll(/<c\b([^>]*?)(?:\/>|>(.*?)<\/c>)/gs)){
      const attrs = c[1], body = c[2] || '', ref = attrs.match(/\br="([A-Z]+)\d*"/);
      const col = ref ? [...ref[1]].reduce((a, ch) => a * 26 + ch.charCodeAt(0) - 64, 0) - 1 : pos;
      pos = col + 1;
      const t = (attrs.match(/\bt="(\w+)"/) || [])[1], vm = body.match(/<v>(.*?)<\/v>/s), v = vm ? xmlUnesc(vm[1]) : null;
      m[col] = (t === 's' ? (v != null ? shared[+v] : null) : t === 'inlineStr' ? xlTexts(body) : v) ?? '';
    }
    return m;
  });
}
const num = v => { const t = String(v ?? '').trim(); return t !== '' && isFinite(+t) ? +t : null; };
function parseExcelDate(v){
  const s = String(v ?? '').trim(), n = num(s);
  if (n != null){ if (n <= 1000 || n >= 200000) return null; const d = new Date(Date.UTC(1899, 11, 30) + Math.floor(n) * 864e5); return d.toISOString().slice(0, 10); }
  let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/), y, mo, d;
  if (m) [y, mo, d] = [+m[1], +m[2], +m[3]];
  else if ((m = s.match(/^(\d{1,2})[./](\d{1,2})[./](\d{4})/))) [y, mo, d] = [+m[3], +m[2], +m[1]];
  else return null;
  const x = new Date(Date.UTC(y, mo - 1, d));
  return x.getUTCFullYear() === y && x.getUTCMonth() === mo - 1 && x.getUTCDate() === d ? x.toISOString().slice(0, 10) : null;
}
function parseExcelMinutes(minutes, duration){
  const n = num(minutes); if (n != null) return Math.round(n);
  const d = String(duration ?? '').trim(); if (!d) return null;
  const f = num(d); if (f != null) return f < 10 ? Math.round(f * 1440) : null;
  const m = d.match(/^(\d+):(\d{1,2})/); return m ? +m[1] * 60 + +m[2] : null;
}
const DIR_IN = {N:0, NE:1, E:2, SE:3, S:4, SV:5, SW:5, V:6, W:6, NV:7, NW:7};
const dirSet = v => new Set(String(v ?? '').toUpperCase().split(/[,;\s]+/).map(x => DIR_IN[x]).filter(x => x != null));
/* Foaia cu numele dat (XML-ul ei) dintr-un .xlsx despachetat, sau null. */
function xlSheetXml(files, name){
  const wb = files['xl/workbook.xml']; if (!wb) return null;
  const rels = {}; for (const m of (files['xl/_rels/workbook.xml.rels'] || '').matchAll(/<Relationship\b[^>]*>/g)) rels[xlAttr(m[0], 'Id')] = xlAttr(m[0], 'Target');
  const tag = [...wb.matchAll(/<sheet\b[^>]*>/g)].map(m => m[0]).find(x => xmlUnesc(xlAttr(x, 'name') || '') === name); if (!tag) return null;
  const target = rels[xlAttr(tag, 'r:id')]; if (!target) return null;
  return files[target.startsWith('/') ? target.slice(1) : 'xl/' + target] || null;
}
const xlShared = files => [...(files['xl/sharedStrings.xml'] || '').matchAll(/<si\b[^>]*>(.*?)<\/si>/gs)].map(m => xlTexts(m[1]));
function readXlsx(files){
  if (!files['xl/workbook.xml']) return null;
  const sheet = name => xlSheetXml(files, name), shared = xlShared(files);
  const days = sheet('Zile de zbor') || sheet('Flying days'); if (!days) return null;
  const fl = [];
  for (const r of xlCells(days, shared)){
    const date = parseExcelDate(r[0]), site = String(r[1] ?? '').trim(), min = parseExcelMinutes(r[3], r[2]);
    if (date && site && min > 0) fl.push({date, site, minutes: min});
  }
  const st = [], s3 = sheet('Situri') || sheet('Sites');
  if (s3) for (const r of xlCells(s3, shared)){
    const name = String(r[0] ?? '').trim(), lat = num(r[1]), lon = num(r[2]);
    if (!name || lat == null || lon == null) continue;
    const good = dirSet(r[4]), marg = dirSet(r[5]), h = String(r[6] ?? '').trim(), hn = num(h), alt = num(r[3]);
    const hf = hn != null ? Math.round(hn) : ['fără', 'none'].includes(h.toLowerCase()) ? 0 : -1;
    st.push({n: name, lat, lon, alt: alt != null ? Math.round(alt) : null, o: [0,1,2,3,4,5,6,7].map(i => good.has(i) ? 2 : marg.has(i) ? 1 : 0), ...(hf >= 0 ? {hf} : {})});
  }
  return {flights: fl, sites: st};
}
/* Starea de sincronizare din foaia ascunsă „_sync” a backup-ului din Drive: {flights, tomb}, sau null dacă foaia lipsește. */
function readSync(files){
  const xml = xlSheetXml(files, '_sync'); if (!xml) return null;
  const flights = [], tomb = {};
  for (const r of xlCells(xml, xlShared(files)).slice(1)){
    const id = String(r[0] ?? '').trim(), u = num(r[4]) || 0; if (!id) continue;
    if (String(r[5]).trim() === 'sters'){ tomb[id] = u; continue; }
    const date = String(r[1] ?? '').trim(), site = String(r[2] ?? '').trim(), minutes = Math.round(num(r[3]) || 0);
    if (/^\d{4}-\d{2}-\d{2}$/.test(date) && site && minutes > 0) flights.push({id, date, site, minutes, u});
  }
  return {flights, tomb};
}
