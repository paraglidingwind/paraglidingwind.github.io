/* Logica pură a sugestiilor: verificarea datelor primite și emailul (MIME) trimis autorului.
   Fără dependențe și fără Cloudflare, ca să poată fi testată cu node --test (tests/feedback.test.mjs). */

export const KINDS = {idee: 'Idee', problema: 'Problemă', altceva: 'Altceva'};
export const MAX_MESSAGE = 3000;

/* null = în regulă; altfel codul erorii (îl primește aplicația). */
export function checkFeedback(d){
  if (!d || typeof d !== 'object' || Array.isArray(d)) return 'json';
  if (!Object.hasOwn(KINDS, d.kind)) return 'kind';
  if (typeof d.message !== 'string' || d.message.trim().length < 5 || d.message.length > MAX_MESSAGE) return 'message';
  // Adresa pentru răspuns e opțională; fără spații, < > " sau rânduri noi (ar putea strica antetul emailului).
  if (d.reply != null && d.reply !== '' && (typeof d.reply !== 'string' || d.reply.length > 200 || !/^[^\s@<>"]+@[^\s@<>"]+\.[^\s@<>"]+$/.test(d.reply))) return 'reply';
  if (d.tech != null && (typeof d.tech !== 'string' || d.tech.length > 600)) return 'tech';
  if (d.lang != null && d.lang !== 'ro' && d.lang !== 'en') return 'lang';
  return null;
}

const utf8b64 = s => { let bin = ''; for (const b of new TextEncoder().encode(s)) bin += String.fromCharCode(b); return btoa(bin); };
// Antet: ASCII simplu rămâne așa; diacriticele merg codate (RFC 2047). Rândurile noi sunt deja scoase.
const header = s => /^[\x20-\x7e]*$/.test(s) ? s : `=?UTF-8?B?${utf8b64(s)}?=`;

/* Emailul către autor: text simplu UTF-8, cu adresa de răspuns în Reply-To dacă utilizatorul a lăsat-o. */
export function buildMail({from, to, kind, message, reply, tech, lang, date = new Date(), id = crypto.randomUUID()}){
  const label = KINDS[kind], text = message.trim();
  const subject = `Sugestie · ${label} · ${text.replace(/\s+/g, ' ').slice(0, 60)}`;
  const body = [text, '', '—', `Tip: ${label} · limba aplicației: ${lang || '?'}`,
    reply ? `Răspuns la: ${reply}` : 'Fără adresă pentru răspuns.', ...(tech ? [tech] : [])].join('\r\n');
  return [
    `From: ${header('Vânt la Decolare')} <${from}>`,
    `To: <${to}>`,
    ...(reply ? [`Reply-To: <${reply}>`] : []),
    `Subject: ${header(subject)}`,
    `Date: ${date.toUTCString()}`,
    `Message-ID: <${id}@paraglidingwind.com>`,
    'MIME-Version: 1.0',
    'Content-Type: text/plain; charset=UTF-8',
    'Content-Transfer-Encoding: base64',
    '',
    utf8b64(body).match(/.{1,76}/g).join('\r\n'),
    '',
  ].join('\r\n');
}
