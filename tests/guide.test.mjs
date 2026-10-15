// Ghidul (docs/ghid.html, docs/guide.html): cele două limbi au aceeași structură, imaginile există și au dimensiunile corecte.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync} from 'node:fs';
import {read} from './load-core.mjs';
import {webpSize} from '../tools/guide/webp.mjs';

const pages = {ro: read('docs/ghid.html'), en: read('docs/guide.html')};
const ids = html => [...html.matchAll(/\sid="([\w-]+)"/g)].map(m => m[1]);
const imgs = html => [...html.matchAll(/<img ([^>]+)>/g)].map(m => Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(x => [x[1], x[2]])));

test('RO și EN: aceleași secțiuni, în aceeași ordine, cu aceleași imagini și același număr de pași', () => {
  assert.deepEqual(ids(pages.en), ids(pages.ro));
  assert.deepEqual(imgs(pages.en).map(i => i.src.replace('/en/', '/')), imgs(pages.ro).map(i => i.src.replace('/ro/', '/')));
  const steps = html => [...html.matchAll(/<ol class="marks">([\s\S]*?)<\/ol>/g)].map(m => (m[1].match(/<li>/g) || []).length);
  assert.deepEqual(steps(pages.en), steps(pages.ro));
});

test('fiecare imagine există, are text alternativ și width/height ale fișierului', () => {
  for (const [lang, html] of Object.entries(pages)){
    for (const i of imgs(html)){
      assert.ok(i.src.startsWith(`img/guide/${lang}/`), i.src);
      assert.ok(i.alt && i.alt.length > 10, `${i.src}: alt`);
      const z = webpSize(readFileSync(new URL('../docs/' + i.src, import.meta.url)));
      assert.deepEqual([+i.width, +i.height], [z.width, z.height], `${i.src}: rulează node tools/guide-shots.mjs`);
    }
  }
});

test('nicio imagine nefolosită în docs/img/guide', () => {
  for (const [lang, html] of Object.entries(pages)){
    const used = new Set(imgs(html).map(i => i.src.split('/').pop()));
    assert.deepEqual(readdirSync(new URL(`../docs/img/guide/${lang}/`, import.meta.url)).filter(f => !used.has(f)), []);
  }
});

test('legăturile #… duc la secțiuni existente, inclusiv cele din aplicație', () => {
  for (const html of Object.values(pages)){
    const have = new Set(ids(html));
    for (const m of html.matchAll(/href="#([\w-]+)"/g)) assert.ok(have.has(m[1]), '#' + m[1]);
  }
  const app = read('src/index.html'), have = new Set(ids(pages.ro));
  const links = [...app.matchAll(/(?:ghid\.html|\$\{GUIDE\})#([\w-]+)/g)].map(m => m[1]);
  assert.ok(links.length >= 1);
  for (const id of links) assert.ok(have.has(id), 'aplicația trimite la #' + id);
});
