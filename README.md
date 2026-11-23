# DevPilot — GitHub RAG Code Assistant

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-16-000000?logo=nextdotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)
![pgvector](https://img.shields.io/badge/pgvector-Enabled-4B7BE5?logo=postgresql&logoColor=white)
![Gemini](https://img.shields.io/badge/Gemini-Google%20AI-8E44AD?logo=google&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

DevPilot is a GitHub-backed RAG assistant for codebases. It connects to a
developer's GitHub account, indexes repository content, and lets developers
ask natural-language questions about source code using relevant retrieved
files and code chunks.

The project is designed to help developers understand unfamiliar repositories
faster by turning repository content into searchable embeddings and using a
Gemini-based chat model to answer questions with relevant code context.

---

## Project Highlights

- GitHub OAuth authentication and repository synchronization
- Asynchronous repository indexing and code chunking
- Gemini embeddings with PostgreSQL + pgvector
- Repository-scoped semantic retrieval
- HNSW vector indexing with cosine similarity
- SSE-based streaming AI responses
- Persisted chat sessions and message history
- Citation metadata for retrieved code context

### Current Indexing Scale

The current local development database contains:

| Metric | Value |
|---|---:|
| GitHub repositories indexed | **9** |
| Unique files indexed | **219** |
| Vectorized code chunks | **994** |
| Embedding dimensions | **768** |
| Vector index | **HNSW** |
| Similarity metric | **Cosine similarity** |

> These figures represent the current development database and are not fixed
> system limits.

---

## Features

- GitHub OAuth login and session-based authentication
- GitHub repository listing and synchronization
- Per-repository indexing workflow with status tracking
- Repository-level indexing progress and failure reporting
- Source file filtering and language detection
- Code chunking for repository content
- Gemini embeddings for code and document chunks
- PostgreSQL + pgvector vector storage
- Semantic similarity retrieval scoped to the selected repository
- RAG-based chat grounded in retrieved code context
- Streaming AI responses over Server-Sent Events (SSE)
- Chat session history and persisted message storage
- Citation metadata attached to generated responses

---

## Why DevPilot?

Large codebases are difficult to understand without prior context. Developers
often spend time searching through directories, reading files, and reconstructing
architecture from scattered source.

DevPilot addresses this by indexing repository content into a vector store and
retrieving relevant code passages for natural-language questions.

Instead of manually searching through a repository, developers can ask questions
such as:

- Where is authentication implemented?
- How does repository indexing work?
- What is the request flow for a particular feature?
- Where are database operations handled?
- How is a particular service connected to the rest of the application?

---

## How It Works

```mermaid
flowchart LR
    A[GitHub] --> B[GitHub OAuth]
    B --> C[Repository Sync]
    C --> D[Code Indexing]
    D --> E[Chunking]
    E --> F[Gemini Embeddings]
    F --> G[PostgreSQL + pgvector]
    G --> H[Semantic Retrieval]
    H --> I[Relevant Code Context]
    I --> J[Gemini Chat Model]
    J --> K[SSE Streaming]
    K --> L[Next.js UI]
```

The implemented flow is:

1. The user signs in with GitHub.
2. The backend synchronizes the user's accessible repositories from the
   GitHub API.
3. A selected repository is indexed asynchronously.
4. Repository files are filtered, downloaded, and split into chunks.
5. Each chunk is embedded using a Gemini embedding model.
6. The resulting vectors are stored in PostgreSQL using pgvector.
7. When the user asks a question, the backend embeds the prompt and performs
   similarity search inside the selected repository's vector space.
8. Relevant code chunks are assembled as context.
9. The Gemini chat model receives the retrieved context and repository information.
10. The response is streamed back to the frontend over SSE.
11. The frontend renders the streamed answer and citation information.

---

## Screenshots

### Repository Dashboard

![DevPilot Repository Dashboard](docs/screenshots/dashboard.png)

*GitHub repositories connected to DevPilot with indexing status.*

### RAG Chat

![DevPilot RAG Chat](docs/screenshots/chat.png)

*Natural-language codebase question answered using repository-scoped retrieval.*

### Repository Indexing

![DevPilot Repository Indexing](docs/screenshots/indexing.png)

*Repository indexing and code processing workflow.*

## Architecture

```mermaid
flowchart LR

    subgraph Frontend
        A[Next.js / React UI]
    end

    subgraph Backend
        B[Spring Boot API]
        C[GitHub Integration]
        D[Indexing Pipeline]
    end

    E[Gemini Embeddings]
    F[PostgreSQL + pgvector]
    G[RAG Retrieval]
    H[Gemini Chat Model]
    I[SSE Streaming]

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    F --> G
    G --> H
    H --> I
    I --> A
```

---

## Tech Stack

| Layer | Technology |
|------|------------|
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS |
| UI Components | shadcn-style component library, custom UI layer |
| Backend | Java 21, Spring Boot 4.1.1, Spring Web MVC |
| Security | Spring Security, GitHub OAuth 2.0 |
| Data Access | Spring Data JPA |
| AI | Google Gemini via Spring AI |
| Embeddings | Google GenAI embedding model |
| Database | PostgreSQL 16 |
| Vector Database | pgvector |
| Vector Index | HNSW |
| Similarity | Cosine similarity |
| Streaming | Server-Sent Events (SSE) |
| Build Tools | Maven, npm |
| CI | GitHub Actions |
| Infrastructure | Docker Compose |
| Testing | JUnit 5, Spring MVC test slices, MockRestServiceServer, Node test runner |

---

## Project Structure

```text
DevPilot/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/devPilot/backend/
│   │   │   │   ├── config/
│   │   │   │   ├── controllers/
│   │   │   │   ├── entity/
│   │   │   │   ├── repository/
│   │   │   │   ├── security/
│   │   │   │   └── services/
│   │   │   └── resources/
│   │   │       └── application-example.properties
│   │   └── test/java/devPilot/backend/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
├── client/
│   ├── app/
│   ├── components/
│   ├── hooks/
│   ├── lib/
│   ├── public/
│   ├── package.json
│   ├── next.config.ts
│   └── tsconfig.json
│
├── docker/
│   └── postgres/init-extensions.sql
│
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .gitignore
└── README.md
```

### Important Directories

- `backend/` contains the Spring Boot API, GitHub integration, indexing logic,
  vector retrieval, and AI orchestration.
- `client/` contains the Next.js application, repository dashboard, chat UI,
  and authentication flow.
- `docker/` contains PostgreSQL extension initialization scripts.
- `docker-compose.yml` provisions the PostgreSQL + pgvector database.

---

## RAG Pipeline

DevPilot uses a repository-scoped retrieval pipeline.

```text
GitHub Repository
       │
       ▼
Repository File Tree
       │
       ▼
File Filtering
       │
       ▼
Code Chunking
       │
       ▼
Gemini Embeddings
       │
       ▼
PostgreSQL + pgvector
       │
       ▼
User Question
       │
       ▼
Question Embedding
       │
       ▼
HNSW Similarity Search
       │
       ▼
Relevant Code Chunks
       │
       ▼
Gemini Chat Model
       │
       ▼
SSE Streaming
       │
       ▼
Next.js UI
```

The pipeline works as follows:

1. The user signs in with GitHub and selects a repository.
2. The backend fetches repository metadata and the file tree from GitHub.
3. Eligible source files are filtered and downloaded.
4. Files are split into manageable chunks using Spring AI's token-based
   text splitter.
5. Each chunk is converted into an embedding using the Gemini embedding model.
6. Embeddings are stored in PostgreSQL using pgvector.
7. A user question is embedded using the same embedding model.
8. The backend performs similarity search within the selected repository.
9. Relevant chunks are retrieved as context.
10. The retrieved context is provided to the Gemini chat model.
11. The generated response is streamed to the frontend over SSE.

This is a repository-scoped RAG flow: retrieval is limited to the selected
repository, and the retrieved code is provided to the chat model as context
for generating the response.

---

## Example Questions

DevPilot is designed to answer questions such as:

- "What is the architecture of this repository?"
- "Where is authentication implemented?"
- "Explain the request flow for creating a user."
- "Where is GitHub integration handled?"
- "Which files manage repository indexing and status updates?"
- "How is chat session state stored?"
- "Where are code chunks and vector metadata created?"
- "How does the indexing pipeline work?"
- "Where is the database configuration defined?"

---

## Getting Started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 21+ | The build targets Java 21 (`--release 21`); newer JDKs such as 25 also work. |
| Node.js | 20.9+ (24 used in CI) | Includes npm. |
| Docker Desktop | recent | Runs PostgreSQL + pgvector. |
| Git | any | |
| GitHub OAuth App | — | Client ID + secret (see below). |
| Gemini API key | — | From Google AI Studio. |

Maven does not need to be installed: use the bundled wrapper (`mvnw` / `mvnw.cmd`).

### Clone the Repository

```bash
git clone https://github.com/Amit23ds/DevPilot.git
cd DevPilot
```

---

## Configuration

All secrets are read from environment variables. Nothing secret is committed.

### 1. Obtain credentials

- **GitHub OAuth App**: GitHub → Settings → Developer settings → OAuth Apps → *New OAuth App*
  - Homepage URL: `http://localhost:3000`
  - Authorization callback URL: **`http://localhost:8080/login/oauth2/code/github`**
- **Gemini API key**: https://aistudio.google.com/app/apikey

### 2. Backend configuration

macOS / Linux:

```bash
# from the repository root
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties
cp backend/.env.example backend/.env      # then edit backend/.env
```

Windows PowerShell:

```powershell
Copy-Item backend\src\main\resources\application-example.properties backend\src\main\resources\application.properties
Copy-Item backend\.env.example backend\.env   # then edit backend\.env
```

`application.properties` and `.env` are git-ignored. `application.properties`
imports `backend/.env` automatically when the backend is started from the
`backend/` folder; real environment variables take precedence over it.

| Variable | Required | Description |
|---|---|---|
| `DB_URL` | no (default `jdbc:postgresql://localhost:5433/devpilot`) | JDBC URL |
| `DB_USERNAME` | no (default `postgres`) | DB user |
| `DB_PASSWORD` | yes | DB password (`postgres` for docker-compose) |
| `GEMINI_API_KEY` | yes | Gemini key for chat + embeddings |
| `GEMINI_CHAT_MODEL` | no | Overrides the chat model name |
| `GEMINI_EMBEDDING_MODEL` | no | Overrides the embedding model (must produce 768 dimensions) |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | yes | GitHub OAuth App credentials |
| `FRONTEND_URL` | no (default `http://localhost:3000`) | Where OAuth redirects back to |
| `CORS_ALLOWED_ORIGINS` | no (default `http://localhost:3000`) | Comma-separated allowed origins |
| `TOKEN_ENCRYPTOR_PASSWORD` | yes | Secret used to encrypt stored GitHub tokens |
| `TOKEN_ENCRYPTOR_SALT` | yes | **Hex-encoded** salt, e.g. `openssl rand -hex 16` |
| `SESSION_COOKIE_SECURE` | no (default `false`) | Set `true` when served over HTTPS |

### 3. Frontend configuration (optional)

```bash
cp client/.env.example client/.env.local
```

`NEXT_PUBLIC_API_BASE_URL` defaults to `http://localhost:8080`. It is public
(embedded in the browser bundle), so never put secrets in it.

---

## Database Setup

```bash
docker compose up -d
docker compose ps          # wait until devpilot-postgres is "healthy"
```

| Setting | Value |
|---|---|
| Container | `devpilot-postgres` |
| Image | `pgvector/pgvector:pg16` |
| Database / user / password | `devpilot` / `postgres` / `postgres` (local development only) |
| Host port → container port | `5433` → `5432` |
| Volume | `devpilot_pg_data` (data persists across restarts) |

`docker/postgres/init-extensions.sql` enables `vector`, `hstore` and `uuid-ossp`
on first start. Application tables are created by Hibernate (`ddl-auto=update`)
and the `vector_store` table and HNSW index by Spring AI (`initialize-schema=true`).

> `docker compose down -v` **deletes** the volume and all indexed data. Use
> `docker compose down` (without `-v`) to keep it.

---

## Running the Backend

macOS / Linux:

```bash
cd backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The API runs on `http://localhost:8080`.

## Running the Frontend

```bash
cd client
npm ci
npm run dev
```

Open `http://localhost:3000` and choose **Continue with GitHub**.

---

## Security

- Never commit API keys, OAuth secrets, or database credentials.
- Keep Gemini and GitHub OAuth secrets server-side.
- Do not expose secrets through frontend code or browser-accessible bundles.
- Use environment variables for sensitive local configuration.
- Keep the real `application.properties` file out of version control.
- Use `application-example.properties` as the safe configuration reference.
- Rotate credentials immediately if a secret is accidentally exposed.

---

## API / Backend Overview

The backend exposes the following main endpoints:

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/auth/login-url` | Returns the GitHub OAuth redirect URL |
| `GET` | `/api/auth/me` | Returns the authenticated user profile |
| `POST` | `/api/auth/logout` | Ends the session (204 No Content) |
| `GET` | `/oauth2/authorization/github` | Spring Security GitHub OAuth entry point |
| `GET` | `/api/repos` | Lists synchronized repositories for the authenticated user |
| `GET` | `/api/repos/{id}` | Returns repository details |
| `POST` | `/api/repos/{id}/index` | Starts repository indexing |
| `GET` | `/api/repos/{id}/status` | Returns current indexing status |
| `POST` | `/api/chat/sessions` | Creates a chat session for a repository |
| `GET` | `/api/chat/sessions` | Lists chat sessions for a repository |
| `GET` | `/api/chat/sessions/{id}` | Returns existing chat messages |
| `POST` | `/api/chat/sessions/{id}/messages` | Streams a model response for a user question using SSE |

---

## Challenges & Engineering Decisions

### GitHub Repository Ingestion

The application retrieves repository metadata and file trees from the GitHub API
and filters files before indexing.

### Code Chunking

Repository files are split into manageable chunks using
`TokenTextSplitter`. Chunk metadata includes information such as file path,
language, chunk index, and repository ID.

### Vector Storage

Embeddings are persisted in PostgreSQL through pgvector.

Retrieval is filtered by repository so that a question for one repository does
not retrieve chunks from another repository.

### Embedding Dimensionality

The project uses **768-dimensional embeddings**, matching the configured Gemini
embedding dimensions and the pgvector schema.

### Semantic Retrieval

The vector store uses an **HNSW index with cosine similarity** for semantic
nearest-neighbor retrieval.

### Streaming AI Responses

`SseEmitter` is used to stream model output back to the frontend without waiting
for the entire response to complete.

### Asynchronous Indexing

Repository indexing runs asynchronously so that the ingestion process does not
block the API request lifecycle.

### OAuth Integration

Spring Security handles GitHub OAuth authentication and maintains the
authenticated application session.

### GitHub API Pacing

A rate limiter is used during repository indexing to reduce pressure on the
GitHub API.

---

## Current Indexing Metrics

The current development database was inspected directly to measure the
repository indexing pipeline.

```text
Repositories indexed:     9
Unique files indexed:     219
Vector chunks stored:     994
Embedding dimensions:     768
Vector index:             HNSW
Similarity:               Cosine
```

These measurements demonstrate the current scale of indexed development data.
They are not hard-coded system limits.

---

## Future Improvements

The following are planned improvements rather than currently implemented
features:

- Hybrid keyword + vector search
- Reranking and improved retrieval scoring
- More code-aware chunking strategies
- Incremental indexing for changed repositories
- Stale vector cleanup and repository reindexing policies
- Better evaluation metrics for RAG answer quality
- Repository code explorer and file-level navigation
- Code generation and test creation helpers
- Usage- and dependency-aware code search
- Improved observability and monitoring of indexing and chat latency
- Automated evaluation datasets for retrieval and answer quality

---

## Testing

### Backend

```bash
cd backend
./mvnw test            # Windows: .\mvnw.cmd test
./mvnw verify          # build + tests + jar
```

| Test class | Covers | External services |
|---|---|---|
| `ApiSecurityAndErrorHandlingTest` | 401 for anonymous users, OAuth entry redirect, CORS, validation, error-to-status mapping, no leaked internals | none (services mocked) |
| `ChatStreamHandlerTest` | SSE event protocol (`user_message`, `token`, `assistant_message`, `done`, `error`) and reply persistence | stub chat model (no Gemini) |
| `GithubApiClientTest` | per-request token isolation, paging, base64 decoding | mocked HTTP server (no GitHub) |
| `IndexingServiceTest` | READY / FAILED transitions, skipped files, restart recovery | mocks |
| `CodeFileFilterTest`, `CodeChunkerTest`, `CitationMapperTest` | filtering, chunk metadata, citation JSON | none |
| `BackendApplicationTests` | full application context | **requires PostgreSQL** (`docker compose up -d`) |

### Frontend

```bash
cd client
npm run lint
npm run typecheck      # next typegen && tsc --noEmit
npm test               # Node's built-in test runner (SSE parser)
npm run build
```

### Continuous integration

`.github/workflows/ci.yml` runs both suites on every push and pull request. The
backend job starts a `pgvector/pgvector:pg16` service, so the context test runs too.

### Manual end-to-end checks (need real credentials)

GitHub OAuth sign-in, repository sync, indexing with real Gemini embeddings, and
streamed chat answers are not covered by the automated tests. Check them in the
browser after configuring credentials.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection to localhost:5433 refused` | Start the database with `docker compose up -d` and wait until it is `healthy`. |
| `Could not resolve placeholder 'GEMINI_API_KEY'` (or similar) | `backend/.env` is missing or incomplete, or the backend was not started from the `backend/` folder. |
| `Failed to determine a suitable driver class` | `application.properties` is missing; copy it from `application-example.properties`. |
| GitHub shows *"redirect_uri is not associated with this application"* | The OAuth App callback must be `http://localhost:8080/login/oauth2/code/github`. |
| Login returns to `/login?error=session` | The backend is not reachable from the browser, or `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` do not match the frontend origin. |
| `GitHub access token is invalid or was revoked` | Sign out and sign in again. |
| Repository shows `FAILED` after a backend restart | Interrupted jobs are marked FAILED on startup; click **Retry**. |
| Indexing fails with an API key or quota message | Check `GEMINI_API_KEY` and its quota, then re-run indexing. |
| Startup error mentioning a hex string / `Encryptors` | `TOKEN_ENCRYPTOR_SALT` must be hex (`openssl rand -hex 16`). |
| Port 3000, 8080 or 5433 already in use | Stop the other process or change the port (`next dev -p`, `server.port`, compose mapping). |

---

## Known Limitations

- Citations include file paths but not line ranges (chunk line offsets are not stored yet).
- Indexing runs in-process; a backend restart interrupts running jobs (they are marked FAILED and can be retried).
- Session cookies use `SameSite=Lax`, so the frontend and backend must be on the same site (as with localhost). A cross-site deployment needs a different cookie and CSRF setup.
- CSRF protection is disabled; it relies on `SameSite=Lax` cookies plus a strict CORS allow-list.
- Repositories removed from GitHub remain in the local database.

---

## Deployment

DevPilot is currently intended as a local development and portfolio project.

There is no public hosted deployment at this time.

The application can be run locally using:

- Docker Compose for PostgreSQL + pgvector
- Spring Boot for the backend
- Next.js for the frontend

A production deployment can be added later with appropriate infrastructure,
secret management, OAuth callback configuration, database hosting, monitoring,
and API rate limiting.

---

## Author

**Amit Yadav**

GitHub:  
https://github.com/Amit23ds/DevPilot

---

## Summary

DevPilot converts a GitHub repository into a searchable code knowledge base by
filtering and chunking source files, generating Gemini embeddings, storing
vectors in PostgreSQL/pgvector, retrieving relevant code for each question, and
streaming a grounded response back to the developer.

The project combines GitHub OAuth, asynchronous indexing, vector search,
repository-scoped RAG, Gemini, PostgreSQL/pgvector, Spring Boot, and a Next.js
frontend into an end-to-end developer tool.