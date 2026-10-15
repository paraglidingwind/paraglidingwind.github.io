// Sugestiile (worker/feedback/src/mail.js): ce acceptă Worker-ul și cum arată emailul trimis autorului.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {checkFeedback, buildMail, MAX_MESSAGE} from '../worker/feedback/src/mail.js';

const ok = {kind: 'idee', message: 'Aș vrea vântul și în km/h.', lang: 'ro'};
const decode = raw => {
  const [head, body] = raw.split('\r\n\r\n');
  return {head, text: new TextDecoder().decode(Uint8Array.from(atob(body.replace(/\s+/g, '')), c => c.charCodeAt(0)))};
};

test('acceptă o sugestie normală, cu sau fără adresă pentru răspuns', () => {
  assert.equal(checkFeedback(ok), null);
  assert.equal(checkFeedback({...ok, reply: 'pilot@example.com', tech: 'Versiunea abc · ro'}), null);
  assert.equal(checkFeedback({...ok, reply: ''}), null);
});

test('refuză date greșite', () => {
  for (const [d, err] of [[null, 'json'], [[], 'json'], [{...ok, kind: 'spam'}, 'kind'], [{...ok, kind: 'toString'}, 'kind'],
    [{...ok, message: '  ok '}, 'message'], [{...ok, message: 'x'.repeat(MAX_MESSAGE + 1)}, 'message'], [{...ok, message: 5}, 'message'],
    [{...ok, reply: 'fără arobas'}, 'reply'], [{...ok, reply: 'a@b.ro\r\nBcc: x@y.ro'}, 'reply'], [{...ok, reply: '<a@b.ro>'}, 'reply'],
    [{...ok, tech: 'x'.repeat(601)}, 'tech'], [{...ok, lang: 'fr'}, 'lang']])
    assert.equal(checkFeedback(d), err, JSON.stringify(d));
});

test('emailul: expeditor, destinatar, Reply-To, subiect și text cu diacritice', () => {
  const raw = buildMail({...ok, kind: 'problema', reply: 'pilot@example.com', tech: 'Versiunea abc · ro · 360×800',
    from: 'feedback@paraglidingwind.com', to: 'autor@example.com', date: new Date('2026-10-15T10:00:00Z'), id: 'x1'});
  const {head, text} = decode(raw);
  assert.match(head, /^From: =\?UTF-8\?B\?[A-Za-z0-9+/=]+\?= <feedback@paraglidingwind\.com>$/m);
  assert.match(head, /^To: <autor@example\.com>$/m);
  assert.match(head, /^Reply-To: <pilot@example\.com>$/m);
  assert.match(head, /^Message-ID: <x1@paraglidingwind\.com>$/m);
  const subj = head.match(/^Subject: =\?UTF-8\?B\?(.+)\?=$/m)[1];
  assert.equal(new TextDecoder().decode(Uint8Array.from(atob(subj), c => c.charCodeAt(0))), 'Sugestie · Problemă · Aș vrea vântul și în km/h.');
  assert.equal(text, 'Aș vrea vântul și în km/h.\r\n\r\n—\r\nTip: Problemă · limba aplicației: ro\r\nRăspuns la: pilot@example.com\r\nVersiunea abc · ro · 360×800');
});

test('fără adresă de răspuns nu există Reply-To; rândurile noi din mesaj nu ajung în antet', () => {
  const raw = buildMail({...ok, message: 'Linia unu\r\nBcc: victima@example.com\nlinia trei', from: 'f@p.com', to: 't@p.com'});
  const {head, text} = decode(raw);
  assert.doesNotMatch(head, /Reply-To|Bcc/);
  assert.equal(head.split('\r\n').length, 8);   // From, To, Subject, Date, Message-ID, MIME-Version, Content-Type, Content-Transfer-Encoding
  assert.match(text, /^Linia unu\r\nBcc: victima@example\.com\nlinia trei/);
  assert.match(text, /Fără adresă pentru răspuns\./);
});
