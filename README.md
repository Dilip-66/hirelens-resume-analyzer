# ResumeRAG — AI Resume Analyzer

A retrieval-augmented generation (RAG) system that scores how well a resume matches a
job description, grounded in the actual retrieved text of the resume (not a free-form
LLM guess).

Runs entirely on a **local Ollama daemon** — no API key, no per-token cost, no data
leaving the machine.

```
React (Vite)  →  Java 17 / Spring Boot API  →  Supabase (Postgres + pgvector)
                                      ↓
                            Ollama (local embeddings + chat)
```

## RAG pipeline, mapped to files

| Layer | What happens | Where |
|---|---|---|
| **Ingest** | PDF/DOCX → plain text | `parser/DocumentParserService.java` (Apache PDFBox / POI) |
| **Chunk** | Split resume into overlapping, section-aware chunks | `chunking/ChunkingService.java` |
| **Embed** | Ollama `nomic-embed-text`, batched over `/v1/embeddings` | `embedding/OllamaEmbeddingProvider.java` |
| **Store (vector DB)** | Chunks + 768-dim vectors in Postgres | `supabase/schema.sql`, `repository/ChunkVectorRepository.java` |
| **Retrieve** | pgvector KNN search for candidates, then a **custom min-heap** re-ranks to the true top-K in O(N log K) | `retrieval/RetrievalService.java`, `algorithm/TopKHeap.java` |
| **Augment** | A **Trie** deterministically extracts which required skills actually appear in the retrieved text (auditable, not hallucinated) | `algorithm/SkillTrie.java`, `algorithm/SkillDictionary.java` |
| **Generate** | Retrieved chunks + skill signal → strict-JSON prompt → local model | `generation/GenerationService.java` |
| **Persist** | Structured result saved for history | `model/Analysis.java` |

The two "DSA" pieces (`TopKHeap`, `SkillTrie`) are deliberately hand-implemented instead
of just calling a library, so this is a legitimate thing to point to in an interview —
`algorithm/TopKHeap.java` and `algorithm/SkillTrie.java` both have doc comments explaining
the complexity tradeoff.

## Project layout

```
hirelens-resume-analyzer/
├── backend/    Java 17 / Spring Boot (Maven) — REST API + RAG pipeline
├── frontend/   React 18 + Vite + TypeScript + Tailwind — the website
└── supabase/   schema.sql — run this first
```

---

## 1. Set up Supabase (5 min)

1. Create a project at [supabase.com](https://supabase.com).
2. **SQL Editor** → paste and run `supabase/schema.sql` (enables `pgvector`, creates all
   tables, and enables row-level security — see [Security](#security) below).
3. **Project Settings → Database** → reset and copy the connection string into `.env` as
   three separate variables:
   ```
   SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
   SUPABASE_DB_USER=postgres
   SUPABASE_DB_PASSWORD=<the password you just reset>
   ```
   Two traps here, both of which look like an unreachable database rather than a config error:
   - The URL **must** start with `jdbc:postgresql://`. A bare `postgresql://` aborts startup
     with `URL must start with 'jdbc'`.
   - **Do not put the credentials inline in the URL.** `jdbc:postgresql://user:pass@host/db`
     makes the Postgres driver mis-split the authority and fail DNS resolution on a "hostname"
     that actually contains your password — the symptom is `UnknownHostException` /
     "The connection attempt failed", not an auth error. The driver also percent-decodes, so
     an unencoded `@` in the password breaks parsing. Spring Boot reads `url`, `username` and
     `password` independently, so the separate-variable form above is both correct and safer.
4. Auth is self-contained in the backend (BCrypt + its own JWT), so the Supabase Auth keys
   are optional. `SUPABASE_URL` / `SUPABASE_JWT_SECRET` are only read if you later switch
   to Supabase Auth.

## 2. Set up Ollama (5 min)

```bash
# macOS
brew install ollama

# start the daemon (it also runs as a login item after install)
ollama serve

# in another shell - pull the two models
ollama pull nomic-embed-text     # embeddings, 768 dims
ollama pull qwen2.5:7b          # chat / generation
```

Verify both are up:
```bash
curl http://localhost:11434/api/tags
```

If `ollama serve` is already running as a background service you can skip it.

## 3. Run the backend locally

```bash
cd backend
cp .env.example .env    # fill in the Supabase values from step 1
mvn spring-boot:run
```

`.env` is read automatically — `application.yml` imports it via
`spring.config.import: "optional:file:.env[.properties]"`, so no `export` step is needed.
The import is *optional*, so a missing `.env` doesn't abort startup, and real environment
variables (Docker, Render, CI) still take precedence over the file.

The Ollama defaults in `.env.example` are already correct for a local daemon — you do not
need to change them.

**Model choice matters more than anything else here.** A 3B model
(`llama3.2:3b`) reliably contradicts its own input: it will list `Terraform` under
matched skills and simultaneously write *"no experience with infrastructure as code"*
into the gaps. `qwen2.5:7b` is the default for that reason — if you swap in a
smaller model, expect false gaps.

### How much of a resume the model actually sees

`app.rag.top-k` (default 20) is an **upper bound**, not the number of excerpts
sent. `GenerationService` derives a real character budget from `OLLAMA_NUM_CTX` and
`PromptBuilder` drops the least-relevant excerpts until the prompt fits, logging a
WARN each time it does. This matters because silently overrunning the context
window is worse than trimming: Ollama evicts from the middle of the prompt, so the
model answers confidently from evidence that is no longer in front of it and
nothing reports an error.

Tune with `RAG_TOP_K`, `RAG_CHUNK_SIZE`, `RAG_CHUNK_OVERLAP` in `.env`.

### Skills are computed, not generated

`matchedSkills` / `missingSkills` come from a deterministic trie scan
(`SkillDictionary` + `SkillTrie`) over the **full** resume text, not from the
model. The prompt deliberately does not ask the model to restate them, and any
gap that asserts the absence of a matched skill is dropped in `ResponseParser`.

The dictionary is a closed vocabulary — that is what makes the skill signal
auditable, but anything absent from it is structurally invisible. When adding
entries, observe two rules enforced by `SkillDictionaryTest`:
- Never list both a term and a longer term starting with it (`Tailwind` and
  `Tailwind CSS`). The trie resolves this with longest-match-wins, but keeping the
  vocabulary clean keeps scores honest.
- List the shorter form where it is a subset of a longer phrase (`Spark` matches
  inside `Apache Spark` at the word boundary).

Backend comes up on `http://localhost:8080`. Confirm with:
```bash
curl http://localhost:8080/api/health
```

## 4. Run the frontend locally

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000` (the dev server port is pinned in `vite.config.ts`). If the
backend runs somewhere else, set `VITE_API_BASE_URL` in `frontend/.env.local`.

If you change the dev port, change `FRONTEND_URL` in `backend/.env` to match. The backend also
auto-accepts the `127.0.0.1` and `[::1]` equivalents of any loopback origin, so serving the
frontend on `http://127.0.0.1:3000` instead of `http://localhost:3000` is fine. A mismatch on
a **non-loopback** origin is still rejected, and the browser hides the 403 from JavaScript —
the symptom is a failed request with no readable error, so check the browser console.

## 5. AI Assistance ("HireLens AI")

A grounded Q&A surface over the analysis you already have. It is **not** a second
scoring pipeline and **not** a generic chatbot: every answer is assembled from
retrieved resume chunks, selected job-description passages, and the stored
analysis, then generated by the same local Ollama model.

```
question
  → pgvector retrieval (existing RetrievalService, queried with the question)
  → job-description passage selection (lexical, no new pipeline)
  → analysis block (deterministic fields, passed verbatim)
  → grounded prompt → Ollama → answer + follow-ups + sources
```

| Piece | Where |
|---|---|
| Endpoints | `POST /api/ai-assistance/chat`, `GET /api/ai-assistance/questions` |
| Orchestration + ownership checks | `assistant/AiAssistantService.java` |
| Prompt assembly | `assistant/AiAssistantPromptBuilder.java` |
| System prompt | `application.yml` → `app.ai.system-prompt` (configurable, not hardcoded) |
| Question catalogue + score-band rules | `assistant/QuestionCatalog.java` |
| Hallucination guards | `assistant/AiAssistantResponseParser.java` |
| UI | `frontend/src/components/ai-assistance/` |

**Grounding and safety.** The client sends only `{ question, analysisId, history }` —
never resume text. The backend resolves the resume, job description and analysis
from `analysisId` and filters each by `user_id`, so a tampered id yields 404 rather
than another user's report. The model is instructed to answer only from supplied
evidence, and its `sources` array is intersected with the context actually sent,
so a citation of a section it never saw is dropped rather than displayed. When it
cannot answer from context, the turn returns an explicit "not enough information"
reply instead of a guess. `grounded: false` marks general-mode answers, which the
UI labels as general advice.

**Cost control.** Each turn retrieves a bounded number of chunks
(`AI_RETRIEVAL_TOP_K`, default 4) and job passages (`AI_JOB_PASSAGE_LIMIT`, default 3)
— never the whole resume — and carries at most `AI_MAX_HISTORY_TURNS` prior turns,
each truncated server-side. Closing the panel aborts the in-flight request.

| Env var | Default | Notes |
|---|---|---|
| `AI_RETRIEVAL_TOP_K` | `4` | Resume chunks per question |
| `AI_JOB_PASSAGE_LIMIT` | `3` | Job-description passages per question |
| `AI_MAX_HISTORY_TURNS` | `4` | Follow-up turns carried into the prompt |
| `AI_TIMEOUT_SECONDS` | `180` | Assistant-specific Ollama timeout |

## Running the tests

```bash
cd backend
mvn test
```

The suite covers the hand-written pieces that are easiest to get subtly wrong: `TopKHeap`
(retention and eviction order, plus a cross-check against a full sort on 2,000 random
scores), `SkillTrie` (whole-word boundaries, prefix collisions like `Java` vs `JavaScript`,
canonical casing), `CosineSimilarity`, both chunking strategies, `ResponseParser`
(code-fence stripping, score clamping, and that the deterministic trie skills override
whatever the model hallucinated), and the assistant's grounding guards
(`AiAssistantTest`: unseen citations are dropped, instruction-shaped follow-ups are
rejected, and the analysis reaches the prompt as authoritative facts).

## Security

`supabase/schema.sql` enables **row-level security** on all five tables. This matters
because Supabase auto-exposes every table over PostgREST at `https://<ref>.supabase.co/rest/v1/`
— without RLS, anyone holding the project's public anon key can read every uploaded resume
and analysis. Resumes are PII.

This does not affect the backend: it connects over JDBC as the table owner, and owners bypass
RLS. The frontend only ever calls our own API, never Supabase directly. The policies are
written against Supabase Auth and are therefore inert while the backend uses its own
BCrypt + JWT — they exist so that switching to Supabase Auth later cannot silently expose data.

Note that RLS is deliberately **not** declared with `FORCE`: `FORCE` extends RLS to the table
owner, which would deny the backend's own queries. Plain `ENABLE` is the correct choice here.

## Configuration reference

All of these live in `backend/src/main/resources/application.yml` under `app.llm` and are
overridable via env vars in `backend/.env`:

| Env var | Default | Notes |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434/v1` | Ollama's OpenAI-compatible surface |
| `OLLAMA_API_KEY` | *(blank)* | Only needed if Ollama sits behind a gateway/proxy |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | |
| `OLLAMA_EMBEDDING_DIMENSIONS` | `768` | **Must** match the pgvector column |
| `OLLAMA_CHAT_MODEL` | `qwen2.5:7b` | |
| `OLLAMA_CHAT_TIMEOUT_SECONDS` | `300` | Raise for CPU-only inference |
| `OLLAMA_EMBEDDING_TIMEOUT_SECONDS` | `120` | |
| `OLLAMA_NUM_CTX` | `8192` | Ollama's default 4096 truncates long JDs |
| `OLLAMA_JSON_MODE` | `true` | Constrains output to JSON; can be turned off for weaker models |

### Changing the embedding model

The vector column width and the model must agree, or Postgres rejects the write with a
dimension mismatch. Known widths: `nomic-embed-text` → 768, `mxbai-embed-large` → 1024,
`bge-m3` → 1024.

To switch an **existing** database, run `supabase/migrate_embedding_dimension.sql` in the
SQL editor. It also nulls out the old vectors, since embeddings from a different model are
meaningless — affected resumes have to be re-uploaded to be re-embedded.

For a **fresh** database, edit the `vector(...)` width in `supabase/schema.sql` to match.

### Choosing a chat model

Larger is more accurate but slower. `qwen2.5:7b` is a reasonable default on a modern laptop.
On CPU-only or low-RAM machines drop to `qwen2.5:3b` or `llama3.2:3b` and raise
`OLLAMA_CHAT_TIMEOUT_SECONDS` if you see timeouts. Models with reliable JSON-mode support
(`qwen2.5`, `llama3.1`, `mistral-nemo`) work best with `OLLAMA_JSON_MODE=true`.

## Deploying

Ollama has to run on the same machine as the backend — `localhost:11434` inside a container
is not your laptop. So the current setup is local-first:

- **Backend**: `mvn spring-boot:run`, or `docker build -t hirelens-backend backend/` and run
  the container with `--network host` (or `host.docker.internal` on Mac/Windows) so it can
  reach Ollama.
- **Frontend**: `npm run build` in `frontend/`, serve `frontend/dist/` from any static host.

`backend/render.yaml` is retained for a hosted deployment, but there you would need to
point `OLLAMA_BASE_URL` at a reachable Ollama host (a GPU box or a tunnel to one) rather than
localhost.

## Tech stack

- **Frontend**: React 18, Vite, TypeScript, Tailwind, React Router, axios
- **Backend**: Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, WebClient
- **Database**: Supabase Postgres + `pgvector`
- **AI**: Ollama — `nomic-embed-text` (embeddings), `qwen2.5:7b` (generation)
- **Parsing**: Apache PDFBox, Apache POI

## Resume bullet, if you want one

> Built a full-stack RAG resume analyzer (React, Java/Spring Boot, Supabase/pgvector) running
> fully local on Ollama with zero external API dependencies. Implemented a custom bounded
> min-heap for O(N log K) top-K retrieval and a Trie-based deterministic skill extractor to
> ground LLM output and prevent hallucinated matches; packaged with a CI-ready Docker build.
>
> Added **AI Assistance**: a resume coach that answers questions about a completed analysis
> using the same retrieval stack rather than a generic chatbot — question-scoped pgvector
> retrieval, bounded context budgets, server-enforced document ownership, and a citation
> layer that intersects the model's claimed sources with the evidence actually supplied.
