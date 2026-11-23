# DevPilot — Project Audit Report

**Date:** 2026-11-22. **Environment:** Windows 11. JDK 25.0.1 (Temurin) used for the build (the project targets Java 21 via `--release 21`; no JDK 21 is installed). Node 24.14.0, npm 11.9.0. **Docker and PostgreSQL are not installed**, and no GitHub OAuth or Gemini credentials were available.

The project folder is **not a git repository**, so Git history could not be checked for previously committed secrets.

---

## 1. Summary

| Area | Result |
|---|---|
| Frontend lint | ✅ 0 errors, 0 warnings (was 5 warnings) |
| Frontend type-check | ✅ `next typegen && tsc --noEmit` passes |
| Frontend unit tests | ✅ 5/5 (new SSE parser tests) |
| Frontend production build | ✅ `next build` passes (Next 16.4.6) |
| Frontend runtime smoke test | ✅ pages load; auth redirects correct |
| Backend compile and package | ✅ `mvnw package` produces the jar |
| Backend tests | ✅ **35/35 pass** (2026-11-23, database up, including `contextLoads` and 2 new security regression tests) |
| Backend startup | ✅ Starts against the Compose database (smoke run with placeholder credentials; see §6) |
| Database / Docker | ✅ `devpilot-postgres` healthy; pgvector 0.8.7; schema created by the app |
| OAuth / Gemini / end-to-end | ⛔ Blocked: no real credentials (OAuth redirect and failure path verified) |
| npm audit | 22 → 4 moderate (dev-only `shadcn` CLI chain) |

---

## 2. Issues found and fixes applied

### Critical

| # | Issue | Fix |
|---|---|---|
| C1 | `GithubApiClient` called `restClientBuilder.baseUrl(..).defaultHeader(AUTHORIZATION, ..)` on the **shared singleton** `RestClient.Builder` for every request. Builders are mutable, so `Authorization` values accumulated across calls and users. Later requests could carry another user's GitHub token (cross-user data exposure) or several conflicting tokens. | Build one immutable client from `builder.clone()` and attach the bearer token per request. Covered by a regression test (`GithubApiClientTest`). |
| C2 | `next@16.3.6` had a critical advisory. | Upgraded `next` and `eslint-config-next` to **16.4.6** (same major version) and applied in-range `npm audit fix`. Build, lint and type-check re-verified. |

### High

| # | Issue | Fix |
|---|---|---|
| H1 | **Streaming never streamed.** `SseEmitter.data(String, APPLICATION_JSON)` writes strings raw (`data:Hello`), but the client `JSON.parse`s every token. Every token threw, the error was swallowed (`use-chat` passed no `onError`), and the answer appeared only when `assistant_message` arrived. Multi-line tokens were also split across `data:` lines. | Tokens are explicitly JSON-encoded. Found by the new `ChatStreamHandlerTest`, which asserts the wire format. |
| H2 | Embedding or vector-store failures (bad Gemini key, quota, DB) were caught per file, so the repository was marked **READY with no vectors**. The unflushed batch also grew and retried, and the chunk count was wrong. | File-fetch errors are still skipped per file. Vector-store failures now propagate and mark the repo `FAILED` with the error, and chunk counts reflect stored chunks only. |
| H3 | The catch-all `@ExceptionHandler(Exception.class)` turned 401s, invalid UUIDs, missing params, malformed JSON and 405s into **500s**, and returned raw `ex.getMessage()` (SQL, upstream responses) to clients. | Explicit handlers for each case. GitHub API errors map to 401 or 502 with actionable messages. Unexpected errors are logged and return a generic message. Covered by `ApiSecurityAndErrorHandlingTest`. |
| H4 | **Bean validation was never enforced.** Only `jakarta.validation-api` was on the classpath, with no provider. `@Valid` / `@NotBlank` / `@NotNull` were ignored, and a null `repositoryId` reached JPA (500). | Replaced with `spring-boot-starter-validation`. Added `@Size` limits (message 8000, title 200). |
| H5 | SSE: no `error` event on model failure (stream just aborted). The Gemini subscription was not cancelled on client disconnect or timeout. | Added an `error` SSE event with a user-facing message, cancellation on completion, timeout and error, and persistence-failure handling. The frontend surfaces `error` events and treats a truncated stream as a failure. |

### Medium

| # | Issue | Fix |
|---|---|---|
| M1 | A backend restart during indexing left repos `INDEXING` forever, and they could never be re-indexed ("already being indexed"). | On `ApplicationReadyEvent`, interrupted jobs are marked `FAILED` ("please retry"). |
| M2 | A failed retrieval (embedding call) happened after the user message was saved, leaving an unanswered question in history and returning a generic 500. | Retrieve first, then persist. Returns 502 with a clear message. The client re-syncs messages on failure. |
| M3 | Open redirect: `/login?next=//evil.com` passed the `startsWith("/")` check. | Only same-origin paths are allowed (rejects `//` and `/\`). |
| M4 | Nested `<a>` inside `<a>` in citation chips (invalid HTML, React hydration warning). | Inner link replaced by a decorative icon. Added an aria-label and URL-encoded paths. |
| M5 | Opening chat for a never-indexed (`PENDING`) repo showed an endless "Indexing…" spinner. | Shows a "Start indexing" action. |
| M6 | The backend could not start from a fresh clone (no `application.properties`, and Spring does not read `.env`). The README showed only bash `export`s. `.gitignore`'s `.env.*` would also have ignored any `.env.example`. | `application-example.properties` now imports `backend/.env`. Added `backend/.env.example` and `client/.env.example`, and un-ignored `.env.example`. Added DB defaults, overridable model names, and an env-controlled `Secure` cookie flag. |
| M7 | The only test needed a live DB and had no configuration. There were no frontend tests and no CI. | 32 focused tests added (see §4). Added a test profile, `npm test` / `npm run typecheck` scripts, and a GitHub Actions workflow with a pgvector service. |

### Low

- Placeholder page metadata ("Create Next App") replaced with "DevPilot".
- 5 unused-import/variable lint warnings removed.
- Ineffective `@Transactional` annotations on self-invoked `protected` methods (and a mixed `jakarta.transaction` import) removed. `repository.save` is already transactional.
- Stale comments referring to OpenAI and a wrong Javadoc package corrected. The commented-out OpenAI dependency was removed.
- Login error message now distinguishes OAuth failure from session-verification failure.

### Reviewed, not changed (recorded only)

- Column name typo `tocken_scopes`: renaming would orphan existing data under `ddl-auto=update`.
- CSRF is disabled and relies on `SameSite=Lax` cookies plus a strict CORS allow-list. This is acceptable for same-site deployment and documented as a limitation.
- Citations carry no line ranges because the chunker doesn't record offsets. Documented.
- The default chat model `gemini-3.8-flash` could not be verified without a key; it is now overridable via `GEMINI_CHAT_MODEL`.
- `.github/modernize/` holds VS Code Java-upgrade hook scripts. Its own `.gitignore` (`**/*`) keeps it out of Git.
- 4 remaining moderate npm advisories are in `shadcn` CLI → `ts-morph` → `minimatch` (dev tooling, not shipped to browsers). The only automated fix downgrades `shadcn` to 1.0.0, so it was not applied.

---

## 3. Files changed

**Backend**
- `services/github/GithubApiClient.java`: per-request auth, immutable client (C1)
- `exceptions/GlobalExceptionHandler.java`: status mapping, no leaks (H3)
- `exceptions/UpstreamServiceException.java` *(new)*: 502 for model/vector failures
- `services/indexing/IndexingService.java`: failure handling, restart recovery (H2, M1)
- `repository/RepositoryRepository.java`: `findByIndexStatus`
- `services/ai/ChatStreamHandler.java`: JSON tokens, error event, cancellation (H1, H5)
- `services/ChatService.java`: retrieve-before-persist (M2)
- `services/ai/ChatPromptBuilder.java`, `RagSettings.java`: comment fixes
- `dto/ChatMessageRequest.java`, `dto/CreateChatSessionRequest.java`: size limits
- `pom.xml`: validation starter (H4), removed a dead comment
- `src/main/resources/application-example.properties`: `.env` import, defaults, overrides (M6)
- `.env.example` *(new)*
- `src/test/...`: 7 new test classes, updated `BackendApplicationTests`, new `application-test.properties`
- `src/main/resources/application.properties`: **local copy of the example, git-ignored, contains no secrets**

**Frontend**
- `lib/sse.ts` *(new)*, `lib/sse.test.ts` *(new)*, `lib/stream-chat.ts`: SSE parsing and error handling
- `hooks/use-chat.ts`: draft cleanup and re-sync on failure
- `app/login/page.tsx` (M3), `components/chat/citation-chips.tsx` (M4), `components/chat/indexing-state.tsx` (M5)
- `app/layout.tsx`, `components/providers/query-provider.tsx`, `hooks/use-auth.ts`, `components/dashboard/overview-dashboard.tsx`: low-severity fixes
- `package.json`, `package-lock.json`: Next 16.4.6, audit fixes, `typecheck` / `test` scripts
- `tsconfig.json`: `allowImportingTsExtensions` (needed by the Node test runner; `noEmit` is already on)
- `.env.example` *(new)*

**Root**
- `.gitignore`: `!.env.example`
- `.github/workflows/ci.yml` *(new)*
- `README.md`: setup (Windows and macOS/Linux), env table, DB, tests, troubleshooting, limitations
- `PROJECT_AUDIT_REPORT.md` *(this file)*

---

## 4. Commands executed and results

| Command | Result |
|---|---|
| `java -version`, `node -v`, `npm -v`, `docker --version` | JDK 26 on PATH; JDK 25 found; Node 24.14 / npm 11.9; **docker: not found** |
| `client> npm ci` | OK; 22 vulnerabilities reported |
| `client> npm run lint` (before) | 0 errors, 5 warnings |
| `client> npx tsc --noEmit` (before) | `LayoutProps` not found (generated type; needs `next typegen`) |
| `client> npx next build` (before) | OK |
| `client> npm install --save-exact next@16.4.6 eslint-config-next@16.4.6` and `npm audit fix` | OK; 4 moderate remain |
| `client> npm test` | 5 pass / 0 fail |
| `client> npm run lint` (after) | 0 errors, 0 warnings |
| `client> npm run typecheck` (after) | OK |
| `client> npm run build` (after) | OK (8 routes + proxy) |
| `client> next start` + curl smoke tests | `/`, `/login`, `/auth/callback` → 200. `/dashboard`, `/dashboard/settings`, `/chat/123` → 307 to `/login?next=…`. `/login` with auth cookie → 307 `/dashboard`. Unknown route → 404. Title "DevPilot". GitHub button → `http://localhost:8080/oauth2/authorization/github` |
| `backend> mvnw -DskipTests compile` (JAVA_HOME=JDK 25) | OK |
| `backend> mvnw test` (after) | 33 run: **32 pass**, 1 error: `BackendApplicationTests.contextLoads` → `Connection to localhost:5433 refused` |
| `backend> mvnw -DskipTests package` | OK, `target/backend-0.0.1-SNAPSHOT.jar` |
| `backend> mvnw spring-boot:run` | Startup reaches DB connection → `Connection to localhost:5433 refused` |
| Secret-pattern grep (Google/GitHub/OpenAI keys, private keys, passwords) | No real secrets; only dummy test values |

**Test labelling:** all new backend tests use mocks or stubs for GitHub and Gemini (`MockRestServiceServer`, a stub `ChatModel`, Mockito). They verify DevPilot's own logic and wire formats, **not** the real integrations.

---

## 5. Blocked checks and next actions

| Check | Blocker | To run it |
|---|---|---|
| ~~PostgreSQL / pgvector, `contextLoads`, backend startup~~ | ✅ Resolved 2026-11-23 (see §6) | — |
| GitHub OAuth flow, repo sync | No OAuth App credentials | Create the OAuth App (callback `http://localhost:8080/login/oauth2/code/github`) and fill `backend/.env` |
| Indexing with real embeddings, chat streaming, citations, history persistence | No Gemini key, no DB | Set `GEMINI_API_KEY`, index a small repo, ask a question; confirm tokens appear progressively |
| Browser-only checks (console errors, responsive layout, accessibility) | No browser automation configured | Manual check at 375px and desktop widths in DevTools |
| Git history secret scan | Folder is not a git repo | Run on the real clone: `git log -p \| Select-String -Pattern "AIza\|ghp_\|client-secret"` and rotate any hit |

---

## 6. Database verification (2026-11-23, after Docker Desktop was installed)

| Step | Command / check | Result |
|---|---|---|
| Docker | `docker --version` / `docker compose version` / `docker info` | 29.9.3 / v5.7.0 / engine running (Docker Desktop, x86_64) |
| Compose config | `docker compose config --quiet`, `--services` | Valid; one service, `postgres` |
| Pre-existing data | `docker ps -a`, `docker volume ls` | No containers or volumes existed, so nothing could be overwritten |
| Start | `docker compose up -d postgres` | Pulled `pgvector/pgvector:pg16`, created volume `devpilot-main_devpilot_pg_data`, container **healthy** |
| Init script | container logs | `01-extensions.sql` ran: 3 × `CREATE EXTENSION` |
| Connection | `psql` in container; TCP `localhost:5433` from Windows | PostgreSQL 16.16; extensions `vector 0.8.7`, `hstore 1.8`, `uuid-ossp 1.1`; host port reachable |
| Port mapping | compose `5433:5432` vs `DB_URL` default | Consistent: host 5433 → container 5432. **No configuration change needed**; the earlier failure was only "no database running". |
| Backend tests | `.\mvnw.cmd -B test` (JAVA_HOME = JDK 25, `--release 21`) | **33 run, 0 failures, 0 errors, BUILD SUCCESS**. `contextLoads` passes in 8 s |
| Schema | `information_schema` | `users`, `repositories`, `chat_sessions`, `chat_messages`, `vector_store` (`vector(768)`, HNSW `vector_cosine_ops` index) |
| Backend run | `.\mvnw.cmd spring-boot:run` with **placeholder values set only in that process** (no files written) | Started in 5.3 s; Hikari connected; Tomcat on 8080; no WARN/ERROR |
| HTTP | curl | `/api/auth/login-url` 200. `/api/auth/me`, `/api/repos`, `/api/chat/sessions` (GET/POST) anonymous → 401. `/oauth2/authorization/github` → 302 to GitHub (scope `read:user repo`, PKCE S256, callback `http://localhost:8080/login/oauth2/code/github`). Callback with invalid state → 302 `/login?error=oauth_failed`. CORS: `localhost:3000` allowed, other origins 403 |
| Frontend | lint / typecheck / test / build, then `next start` | All pass; `/`, `/login`, `/auth/callback` 200; protected routes 307 → `/login?next=…`; GitHub button targets the backend |

Notes:
- No JDK 21 is installed, so JDK 25 was used. The project's Java target (`<java.version>21</java.version>`) was **not** changed. CI uses Temurin 21.
- Mockito prints a warning about self-attaching its agent on newer JDKs. It is informational and does not affect results.
- Both app servers were stopped after the smoke test. The database container was left running, with its volume intact.

### 6.1 Configuration and secret-handling checks (2026-11-23)

| Check | Result |
|---|---|
| Variables read by `application.properties` vs `backend/.env.example` | Identical set of 13: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `TOKEN_ENCRYPTOR_PASSWORD`, `TOKEN_ENCRYPTOR_SALT`, `SESSION_COOKIE_SECURE` |
| Other env lookups in source | Only `NEXT_PUBLIC_API_BASE_URL` (frontend, optional, defaults to `http://localhost:8080`) |
| `.gitignore` behaviour (tested with `git check-ignore` in a throwaway repo) | Ignored: `backend/.env`, `client/.env`, `client/.env.local`, `backend/src/main/resources/application.properties`, `target/`, `.next/`, `node_modules/`, `*.log`, `*.pem`, `client_secret*.json`, `.vscode/`. Tracked: both `.env.example` files, `application-example.properties`, `application-test.properties` |
| `backend/.env` | **Not created yet**; it needs the user's own credentials |

### 6.2 Live integration tests (GitHub OAuth, Gemini)

**Status: BLOCKED (re-checked 2026-11-23).** `backend/.env` still does not exist, so the backend cannot start with real credentials. As instructed, it was not started with fake ones. Variables needing values: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GEMINI_API_KEY`, `TOKEN_ENCRYPTOR_PASSWORD`, `TOKEN_ENCRYPTOR_SALT`, `DB_PASSWORD`.

| Live check | Status |
|---|---|
| Backend start with real config / `.env` import | BLOCKED |
| GitHub sign-in success → dashboard; identity and session | BLOCKED |
| Logout invalidates the session (live) | BLOCKED (POST-only logout verified by test, §6.3) |
| Repository sync (public and private) | BLOCKED |
| Indexing a small repo: filtering, chunks, real Gemini embeddings in pgvector, READY / FAILED, retry | BLOCKED |
| Gemini answer, progressive streaming, citations, saved and restored history | BLOCKED |
| Real Gemini error cases (invalid model, quota) | BLOCKED |
| Previously verified without credentials (smoke run, 2026-11-23): anonymous 401s, OAuth redirect (scope, PKCE, callback), bad-callback → `/login?error=oauth_failed`, CORS allow-list | PASS |

The database contains no user data yet (0 users, 0 repositories, 0 vectors).

### 6.3 Security review, 2026-11-23

Reviewed:
- every log statement (no tokens or secrets logged)
- GitHub token handling (encrypted at rest; sent only as a per-request `Authorization` header)
- queries (only Spring Data derived queries and Spring AI filter expressions built from server-side UUIDs; no hand-written SQL)
- ownership checks (all repo, index and chat operations are scoped by the signed-in user's ID)
- CORS, cookies, error handling

Two verified defects were fixed. Each was first reproduced by a failing test:

| # | Defect | Evidence before fix | Fix | Regression test |
|---|---|---|---|---|
| S1 | Logout accepted **GET**. CSRF protection is disabled, and Spring's `logoutUrl` then matches any method, so a link or `<img>` could sign a user out. | `GET /api/auth/logout` returned 204 | `logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(POST, "/api/auth/logout"))`. The frontend already uses POST. | `logoutOnlyAcceptsPost` |
| S2 | When the indexing executor queue was full, `TaskRejectedException` came **after** the repo was set to `INDEXING`. The user got a 500 and the repo stayed stuck ("already being indexed") until a restart. | Reproduced in test | The controller catches the rejection, marks the repo `FAILED` with a retry hint, and returns **503**. New `ServiceBusyException`, mapped in `GlobalExceptionHandler`. | `fullIndexingQueueIs503AndDoesNotLeaveRepoStuckIndexing` |

Files changed: `config/SecurityConfig.java`, `controllers/RepoController.java`, `services/indexing/IndexingService.java` (`markFailed` now public), `exceptions/ServiceBusyException.java` (new), `exceptions/GlobalExceptionHandler.java`, `ApiSecurityAndErrorHandlingTest.java` (+2 tests).

### 6.4 Regression run, 2026-11-23

| Command | Result |
|---|---|
| `backend> .\mvnw.cmd -B test` (JDK 25, `--release 21`; no JDK 21 installed, Java 21 is verified separately in CI) | **35 run, 0 failures, 0 errors, BUILD SUCCESS** |
| `client> npm run lint` | PASS, 0 problems |
| `client> npm run typecheck` | PASS |
| `client> npm test` | PASS, 5/5 |
| `client> npm run build` | PASS |

## 7. GitHub-readiness assessment

**Ready to commit, with the caveats above.** The source contains no secrets, and real configuration (`application.properties`, `.env`, `.env.local`) is git-ignored and covered by templates. Build artifacts, `node_modules`, and IDE files are ignored. The frontend passes lint, type-check, tests and build. All 35 backend tests pass against the local pgvector database, and CI runs the database-backed test on GitHub's runners.

The live OAuth and Gemini paths (sign-in, sync, real embeddings, streamed answers) have **not** been exercised yet (§6.2).
