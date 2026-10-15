/* Sugestiile din aplicație → email către autor, prin Cloudflare Email Routing (legătura send_email).
   Nu păstrează nimic. Poate trimite doar la adresa verificată din configurație, deci nu poate fi folosit
   ca să trimită emailuri altcuiva. Publicare: vezi worker/feedback/README.md. */
import {EmailMessage} from 'cloudflare:email';
import {checkFeedback, buildMail} from './mail.js';

const ORIGINS = ['https://paraglidingwind.com', 'https://paraglidingwind-test.github.io', 'http://localhost:8765', 'http://localhost:8767'];

export default {
  async fetch(req, env){
    const origin = req.headers.get('Origin') || '';
    const cors = ORIGINS.includes(origin)
      ? {'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Methods': 'POST', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Max-Age': '86400', 'Vary': 'Origin'} : {};
    const json = (status, body) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json', ...cors}});
    if (req.method === 'OPTIONS') return new Response(null, {status: cors['Access-Control-Allow-Origin'] ? 204 : 403, headers: cors});
    if (req.method !== 'POST' || new URL(req.url).pathname !== '/feedback') return json(404, {error: 'not_found'});
    if (!cors['Access-Control-Allow-Origin']) return json(403, {error: 'origin'});
    // Cel mult câteva mesaje pe minut de la aceeași adresă IP (legătura LIMIT din wrangler.toml).
    if (env.LIMIT && !(await env.LIMIT.limit({key: req.headers.get('CF-Connecting-IP') || 'local'})).success) return json(429, {error: 'rate'});
    let data;
    try { data = await req.json(); } catch { return json(400, {error: 'json'}); }
    if (data && data.website) return json(200, {ok: true});   // câmp ascuns, completat doar de roboți
    const err = checkFeedback(data);
    if (err) return json(400, {error: err});
    try {
      await env.MAIL.send(new EmailMessage(env.FROM, env.TO, buildMail({...data, from: env.FROM, to: env.TO})));
    } catch (e) {
      console.error('send_email', e && e.message);
      return json(502, {error: 'send'});
    }
    return json(200, {ok: true});
  },
};
