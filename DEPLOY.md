# Colocando o Resten no ar

O sistema fica em três serviços gratuitos:

| Parte | Onde | O que faz |
|---|---|---|
| Site (React) | **Vercel** | Entrega o app e encaminha `/api/*` para a API |
| API (Spring Boot) | **Render** | Roda o `Dockerfile` deste repositório |
| Banco (PostgreSQL) | **Supabase** | Guarda usuários, playlists e sessões |

O navegador só conversa com o endereço da Vercel. A Vercel repassa `/api` para o Render, então o
cookie de login continua sendo do mesmo site (sem isso o login não funciona).

Faça na ordem abaixo. Os deploys usam a branch `main` dos dois repositórios.

---

## 1. Supabase (banco)

1. Crie um projeto em <https://supabase.com/dashboard>.
   - **Região:** *East US (North Virginia)*, a mesma do Render (passo 2). Banco e API perto um
     do outro deixam o app bem mais rápido.
   - Anote a **senha do banco** que você definir.
2. Não crie tabelas: a API cria tudo sozinha (Flyway) na primeira vez que subir.
3. Clique em **Connect** no topo e escolha **Session pooler** (não use "Direct connection",
   que só funciona por IPv6, nem o "Transaction pooler"). Anote:
   - **host:** algo como `aws-0-us-east-1.pooler.supabase.com`
   - **port:** `5432`
   - **database:** `postgres`
   - **user:** algo como `postgres.abcdefghijklmn`

> O projeto gratuito é pausado depois de 7 dias sem uso. Se o app parar de funcionar,
> entre no painel do Supabase e clique em **Restore project**.

## 2. Render (API)

1. Em <https://dashboard.render.com>, clique em **New → Web Service** e conecte o repositório
   **Reten-api**.
2. Configure:
   - **Name:** `resten-api` (vira `https://resten-api.onrender.com`; se o nome estiver ocupado,
     anote o endereço que o Render der e ajuste o `vercel.json` do front, passo 3)
   - **Branch:** `main`
   - **Language/Runtime:** Docker
   - **Region:** Virginia (US East)
   - **Instance type:** Free
   - **Health Check Path** (em *Advanced*): `/actuator/health`
3. Em **Environment Variables**, cadastre:

| Variável | Valor |
|---|---|
| `RETEN_DB_HOST` | host do Session pooler do Supabase |
| `RETEN_DB_PORT` | `5432` |
| `RETEN_DB_NAME` | `postgres` |
| `RETEN_DB_USER` | user do Session pooler (`postgres.xxxx`) |
| `RETEN_DB_PASSWORD` | senha do banco do Supabase |
| `RETEN_DB_PARAMS` | `?sslmode=require` |
| `RETEN_DB_POOL_SIZE` | `5` |
| `RETEN_COOKIE_SECURE` | `true` |
| `RETEN_FRONTEND_URL` | endereço da Vercel (passo 3), ex.: `https://resten.vercel.app` |
| `RETEN_TOKEN_ENCRYPTION_KEY` | chave nova: rode `openssl rand -base64 32` (ou use a do seu `.env`) |
| `RETEN_MAIL_HOST` | `smtp.gmail.com` |
| `RETEN_MAIL_PORT` | `587` |
| `RETEN_MAIL_USERNAME` | seu Gmail |
| `RETEN_MAIL_PASSWORD` | a senha de app do Gmail |
| `RETEN_MAIL_SMTP_AUTH` | `true` |
| `RETEN_MAIL_STARTTLS` | `true` |
| `RETEN_MAIL_FROM` | `Resten <seu-gmail>` (sem aspas no painel) |
| `OPENAI_API_KEY` | sua chave da OpenAI |
| `OPENAI_MODEL` | `gpt-5-mini` |
| `SPOTIFY_CLIENT_ID` | do painel do Spotify |
| `SPOTIFY_CLIENT_SECRET` | do painel do Spotify |
| `SPOTIFY_REDIRECT_URI` | `https://<endereço-da-vercel>/api/spotify/callback` |
| `GOOGLE_BOOKS_API_KEY` | opcional |

   Os valores são os mesmos do seu `.env` local, menos banco, endereços e `RETEN_COOKIE_SECURE`.
   Não defina `PORT`: o Render cuida dela.

4. Clique em **Deploy**. A primeira subida demora (compila o projeto). Quando o log mostrar
   `Tomcat started`, abra `https://resten-api.onrender.com/actuator/health`: deve aparecer
   `{"status":"UP"}`.

> O plano grátis desliga a API depois de 15 minutos sem acesso. A próxima visita acorda ela,
> mas leva alguns minutos (a instância tem pouca CPU). Para uma demonstração, abra o link uns
> minutos antes.

## 3. Vercel (site)

1. Se o endereço do Render não for `https://resten-api.onrender.com`, troque-o no
   `vercel.json` do repositório **Reten-front** e faça commit na `main`.
2. Em <https://vercel.com/new>, importe o repositório **Reten-front**. A Vercel detecta o Vite
   sozinha (build `npm run build`, saída `dist`). Não precisa de variáveis de ambiente.
3. Depois do deploy, anote o endereço (ex.: `https://resten.vercel.app`). Em
   **Settings → Domains** você pode trocar o nome ou ligar um domínio próprio.
4. Volte no Render e confira se `RETEN_FRONTEND_URL` e `SPOTIFY_REDIRECT_URI` usam exatamente
   esse endereço. Se mudou algo, salve: o Render refaz o deploy.

## 4. Spotify

No app em <https://developer.spotify.com/dashboard>:

1. **Settings → Redirect URIs:** adicione `https://<endereço-da-vercel>/api/spotify/callback`
   (igual ao `SPOTIFY_REDIRECT_URI`) e salve. Pode manter a de desenvolvimento.
2. **User Management:** enquanto o app estiver em modo de desenvolvimento, só as contas
   cadastradas aqui (nome e e-mail da conta Spotify) conseguem conectar. Para liberar para
   qualquer pessoa, é preciso pedir a revisão do app ao Spotify.

## 5. Conferindo

1. Abra o endereço da Vercel e crie uma conta: o código de confirmação deve chegar no e-mail.
2. Gere uma playlist e salve no Spotify.
3. Se algo falhar, os erros aparecem em **Logs** no serviço do Render.

## Atualizações

Depois de configurado, cada push na `main` publica sozinho: a Vercel refaz o site e o Render
refaz a API. Novas migrações do Flyway rodam no banco quando a API sobe.
