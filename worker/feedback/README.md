# Worker-ul de sugestii

Butonul **Sugestii** din aplicație trimite mesajul aici (`POST https://api.paraglidingwind.com/feedback`), iar Worker-ul
îl trimite pe email autorului, prin Cloudflare Email Routing. Nu păstrează nimic.

- `src/mail.js`: verificarea datelor și emailul (testate în `tests/feedback.test.mjs`).
- `src/index.js`: Worker-ul (CORS doar pentru paraglidingwind.com, mediul de test și localhost; limită pe IP; câmp-capcană pentru roboți).
- `wrangler.example.toml`: configurația; copia reală `wrangler.toml` are adresa de destinație și nu intră în git.

Local: `npx wrangler dev` (emailul nu pleacă, e salvat într-un fișier, iar calea apare în consolă).
Publicare: `npx wrangler login` (o dată), apoi `npx wrangler deploy`.
