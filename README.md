# ResumeRAG — AI Resume Analyzer

A retrieval-augmented generation (RAG) system that scores how well a resume matches a
job description, grounded in the actual retrieved text of the resume (not a free-form
LLM guess).

```
Next.js (Vercel)  →  Java 17 / Spring Boot API (Render)  →  Supabase (Postgres + pgvector)
                                     ↓
                              OpenAI (embeddings + GPT)
```

## RAG pipeline, mapped to files

| Layer | What happens | Where |
|---|---|---|
| **Ingest** | PDF/DOCX → plain text | `service/DocumentParserService.java` (Apache PDFBox / POI) |
| **Chunk** | Split resume into overlapping, section-aware chunks | `service/ChunkingService.java` |
| **Embed** | OpenAI `text-embedding-3-small`, batched | `service/EmbeddingService.java` |
| **Store (vector DB)** | Chunks + 1536-dim vectors in Postgres | `supabase/schema.sql`, `repository/ChunkVectorRepository.java` |
| **Retrieve** | pgvector ANN search for candidates, then a **custom min-heap** re-ranks to the true top-K in O(N log K) | `service/RetrievalService.java`, `dsa/TopKHeap.java` |
| **Augment** | A **Trie** deterministically extracts which required skills actually appear in the retrieved text (auditable, not hallucinated) | `dsa/SkillTrie.java`, `dsa/SkillDictionary.java` |
| **Generate** | Retrieved chunks + skill signal → strict-JSON prompt → GPT | `service/GenerationService.java` |
| **Persist** | Structured result saved for history | `model/Analysis.java` |

The two "DSA" pieces (`TopKHeap`, `SkillTrie`) are deliberately hand-implemented instead
of just calling a library, so this is a legitimate thing to point to in an interview —
`dsa/TopKHeap.java` and `dsa/SkillTrie.java` both have doc comments explaining the
complexity tradeoff.

## Project layout

```
resume-rag-analyzer/
├── backend/    Java 17 / Spring Boot (Maven) — REST API + RAG pipeline
├── frontend/   Next.js 14 (App Router) + Tailwind — the website
└── supabase/   schema.sql — run this first
```

---

## 1. Set up Supabase (5 min)

1. Create a project at [supabase.com](https://supabase.com).
2. **SQL Editor** → paste and run `supabase/schema.sql` (enables `pgvector`, creates all tables + RLS).
3. **Project Settings → API** → copy:
   - Project URL
   - `anon` public key
   - `service_role` is *not* needed — the Java backend authenticates as the end user via JWT.
4. **Project Settings → API → JWT Settings** → copy the JWT Secret (backend needs this to verify Supabase Auth tokens).
5. **Project Settings → Database** → copy the connection string (use the *pooled* connection string on port 6543, or 5432 for direct — either works for this project size).

## 2. Run the backend locally

```bash
cd backend
cp .env.example .env    # fill in the values from step 1 + your OpenAI key
export $(cat .env | xargs)   # or use direnv / your IDE's env-var support
mvn spring-boot:run
```

Open VS Code with the **Extension Pack for Java** + **Spring Boot Extension Pack** installed,
open the `backend/` folder, and `ResumeRagApplication.java` is runnable directly from the
gutter — no CLI needed if you'd rather click "Run".

Backend comes up on `http://localhost:8080`. Confirm with:
```bash
curl http://localhost:8080/api/health
```

## 3. Run the frontend locally

```bash
cd frontend
cp .env.local.example .env.local   # fill in Supabase URL/anon key
npm install
npm run dev
```

Open `http://localhost:3000`.

## 4. Deploy for real

**Backend → Render** (Vercel/Netlify cannot run a JVM app — Render/Railway/Fly.io can):
1. Push this repo to GitHub.
2. Render → New → Blueprint → point at your repo. `backend/render.yaml` is already set up
   (it builds `backend/Dockerfile`, a two-stage Maven build).
3. Fill in the env vars Render prompts for (`SUPABASE_DB_URL`, `SUPABASE_DB_PASSWORD`,
   `SUPABASE_URL`, `SUPABASE_JWT_SECRET`, `OPENAI_API_KEY`, `FRONTEND_URL`).

**Frontend → Vercel**:
1. Vercel → New Project → import the repo, set root directory to `frontend/`.
2. Env vars: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`,
   `NEXT_PUBLIC_API_URL` = your Render backend URL.
3. Deploy. Then go back to Render and set `FRONTEND_URL` to your live Vercel URL (for CORS).

## Tech stack

- **Frontend**: Next.js 14 (App Router), TypeScript, Tailwind, Supabase Auth (`@supabase/ssr`)
- **Backend**: Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, WebClient
- **Database**: Supabase Postgres + `pgvector` extension, Row-Level Security
- **AI**: OpenAI `text-embedding-3-small` (embeddings), `gpt-4o-mini` (generation)
- **Parsing**: Apache PDFBox, Apache POI

## Resume bullet, if you want one

> Built a full-stack RAG resume analyzer (Next.js, Java/Spring Boot, Supabase/pgvector,
> OpenAI). Implemented a custom bounded min-heap for O(N log K) top-K retrieval and a
> Trie-based deterministic skill extractor to ground LLM output and prevent hallucinated
> matches; deployed with CI-ready Docker build on Render + Vercel.
