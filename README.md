# Reten

Cada história tem uma trilha sonora. O Reten transforma livros em playlists do Spotify a partir da
atmosfera de cada obra, com interpretação por inteligência artificial.

## Estrutura

```
.                     backend Spring Boot 4 (Java 21) — pacote com.Reaten
├── src/main/java/com/Reaten/
│   ├── auth/         cadastro, login, recuperação de senha
│   ├── user/         usuários
│   ├── book/         busca de livros (Open Library / Google Books) e livros escolhidos
│   ├── playlist/     núcleo: geração, prévia, edição, histórico
│   ├── music/        domínio musical independente de provedor
│   ├── spotify/      OAuth, busca e criação de playlists na Spotify Web API
│   ├── ai/           interpretação literária e recomendações (OpenAI)
│   ├── shared/       erros (RFC 9457), criptografia e código transversal
│   └── config/       configurações do Spring
├── src/main/resources/db/migration/   migrações Flyway
└── docker-compose.yml                 Postgres + Mailpit para desenvolvimento
```

O frontend (React + TypeScript + Vite) fica em [Reten-front](https://github.com/Jhxnn/Reten-front).
Para desenvolver localmente, clone-o em `./frontend` (pasta ignorada por este repositório).

Branches: `desenv` recebe o trabalho do dia a dia; `main` recebe apenas versões estáveis.

## Rodando localmente

Pré-requisitos: JDK 21+, Node 20+, Docker.

```bash
cp .env.example .env          # preencha RETEN_DB_PASSWORD e RETEN_TOKEN_ENCRYPTION_KEY (openssl rand -base64 32)
docker compose up -d          # Postgres (5434) e Mailpit (SMTP 1026, interface http://localhost:8026)
./mvnw spring-boot:run        # API em http://localhost:8090
```

```bash
git clone https://github.com/Jhxnn/Reten-front.git frontend
cd frontend
npm install
npm run dev                   # http://127.0.0.1:5173 — /api é encaminhado para a API
```

Acesse sempre por **http://127.0.0.1:5173** (não `localhost`): a Spotify só aceita redirect por IP de
loopback, e o cookie de sessão precisa estar no mesmo host do retorno do OAuth.

Testes do backend usam Testcontainers (Docker precisa estar rodando): `./mvnw test`.

## API

| Método | Caminho                     | Acesso   | Descrição |
| ------ | --------------------------- | -------- | --------- |
| GET    | `/api/auth/csrf`            | público  | Garante o cookie `XSRF-TOKEN` |
| POST   | `/api/auth/register`        | público  | Cria a conta e inicia a sessão |
| POST   | `/api/auth/login`           | público  | Inicia a sessão |
| POST   | `/api/auth/logout`          | sessão   | Encerra a sessão |
| GET    | `/api/auth/me`              | sessão   | Usuário atual |
| POST   | `/api/auth/password/forgot` | público  | Envia link de redefinição (sempre 202) |
| POST   | `/api/auth/password/reset`  | público  | Redefine a senha e encerra todas as sessões |
| GET    | `/api/spotify/connection`   | sessão   | Estado da conexão Spotify |
| GET    | `/api/spotify/authorize`    | sessão   | Redireciona para o consentimento da Spotify |
| GET    | `/api/spotify/callback`     | público* | Retorno do OAuth; redireciona para o frontend |
| DELETE | `/api/spotify/connection`   | sessão   | Desconecta e apaga os tokens |
| GET    | `/api/books/search?q=&by=`  | sessão   | Busca no catálogo (`by`: all, title, author, isbn) |
| GET    | `/api/books/catalog?id=`    | sessão   | Detalhes de um livro do catálogo, com sinopse |
| POST   | `/api/books/select`         | sessão   | Escolhe o livro: guarda no Reten e no histórico |
| GET    | `/api/books/recent`         | sessão   | Livros escolhidos recentemente pelo usuário |
| GET    | `/api/books/{id}`           | sessão   | Livro guardado no Reten |
| POST   | `/api/playlists`            | sessão   | Inicia a geração com IA (202, status `GENERATING`) |
| GET    | `/api/playlists/{id}`       | sessão   | Estado e resultado da geração (`GENERATING`, `READY`, `FAILED`) |
| GET    | `/api/playlists/usage`      | sessão   | Gerações usadas nas últimas 24 h e se a IA está configurada |

Requisições que alteram dados exigem o cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`.
Erros seguem a RFC 9457 (`application/problem+json`) com um campo `code` estável.
\* o callback valida a sessão e o `state` internamente.

## Segurança

- Sessão no servidor (Spring Session JDBC), cookie `RETEN_SESSION` HttpOnly + SameSite=Lax (Secure em produção).
- Id de sessão trocado no login; token CSRF rotacionado no login.
- Senhas com BCrypt. Tokens de redefinição: aleatórios (256 bits), guardados como SHA-256, 30 min, uso único.
- Tokens do Spotify cifrados com AES-256-GCM (`RETEN_TOKEN_ENCRYPTION_KEY`), renovados automaticamente.

## Estado das integrações

| Integração      | Situação |
| --------------- | -------- |
| PostgreSQL      | Configurado (Flyway) |
| E-mail (SMTP)   | Funcionando com Mailpit em desenvolvimento; produção requer um provedor SMTP |
| Livros          | **Open Library** funcionando sem chave (edições em português via `lang=por`; sinopses geralmente em inglês). **Google Books** é usado automaticamente quando `GOOGLE_BOOKS_API_KEY` estiver definido — a cota anônima dele está zerada |
| Spotify Web API | OAuth implementado e testado com cliente simulado. **Precisa de `SPOTIFY_CLIENT_ID`/`SECRET` para funcionar de verdade.** Busca e playlists na Fase 5 |
| OpenAI API      | Implementada (Responses API, Structured Outputs estrito, validação e novas tentativas) e testada com servidor simulado. **Precisa de `OPENAI_API_KEY` para gerar de verdade**; sem ela, a geração responde `ai_not_configured`. As sugestões ainda não são verificadas no Spotify (Fase 5) |

### Geração com IA

- A geração é assíncrona: `POST /api/playlists` registra a tentativa e responde na hora; o frontend consulta o
  status a cada 2 s. Gerações presas por mais de 5 minutos (ex.: reinício do servidor) viram `FAILED`.
- A IA recebe apenas metadados, sinopse e as impressões opcionais do leitor (máx. 500 caracteres, tratadas como
  dado, não como instrução). Nenhum texto do livro é enviado.
- Limite: `RETEN_GENERATION_DAILY_LIMIT` gerações por usuário em 24 h (padrão 10), contando tentativas que falham.
- Modelo e raciocínio: `OPENAI_MODEL` (padrão `gpt-5-mini`) e `OPENAI_REASONING_EFFORT` (padrão `low`; deixe vazio
  para modelos sem raciocínio).

### Configurando o Spotify

1. Crie um app em https://developer.spotify.com/dashboard (a conta dona precisa ser Premium).
2. Em *Redirect URIs*, cadastre exatamente `http://127.0.0.1:5173/api/spotify/callback`.
3. Em *User Management*, adicione o e-mail Spotify de cada pessoa que vai testar (máximo 5 em Development Mode).
4. Preencha `SPOTIFY_CLIENT_ID` e `SPOTIFY_CLIENT_SECRET` no `.env` e reinicie a API.

> Em Development Mode, a Spotify permite no máximo 5 usuários cadastrados manualmente.
> Por isso, conectar o Spotify é opcional no Reten: só é exigido para salvar a playlist na conta.

### Configurando o Google Books (opcional)

1. Em https://console.cloud.google.com/apis/credentials, crie uma chave de API e ative a *Books API*.
2. Preencha `GOOGLE_BOOKS_API_KEY` no `.env` e reinicie a API. A busca passa a usar o Google Books;
   livros já escolhidos do Open Library continuam funcionando.
