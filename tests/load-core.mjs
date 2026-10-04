// Încarcă src/core.js așa cum îl folosește pagina: t() și DIRS vin din afară (aici: română, fără dicționar).
import {readFileSync} from 'node:fs';

export const read = p => readFileSync(new URL('../' + p, import.meta.url), 'utf8');
export const json = p => JSON.parse(read(p));
export const t = (ro, v) => v ? ro.replace(/\{(\w+)\}/g, (_, k) => v[k] ?? '') : ro;
export const DIRS = ['N', 'NE', 'E', 'SE', 'S', 'SV', 'V', 'NV'];
export const core = new Function('t', 'DIRS', read('src/core.js') +
  '\nreturn {RANK, sectorOf, rate, summarize, sky, WMO, CLOUD_NOTES, distKm, parseCoords, syncMerge};')(t, DIRS);
