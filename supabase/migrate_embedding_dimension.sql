-- Run this ONCE in the Supabase SQL editor if you already created resume_chunks
-- with the old OpenAI 1536-dimension column and are switching to a local
-- Ollama embedding model.
--
-- The column type must match the embedding model exactly. A mismatch surfaces
-- at query time as a Postgres "expected 768 dimensions, not 1536" error.
--
-- Pick ONE target block below and run only that one. Drop the others.

-- ============================================================
-- Option A: nomic-embed-text (768 dims) - matches the default schema
-- ============================================================
drop index if exists idx_resume_chunks_embedding;
alter table resume_chunks alter column embedding type vector(768);

-- Existing vectors were produced by a different model, so they are meaningless
-- against the new one. Clear them: affected resumes must be re-uploaded to be
-- re-chunked and re-embedded.
update resume_chunks set embedding = null;

-- Recreate the index. `lists` is sized to roughly rows / 1000; bump it if the
-- table grows, otherwise the exact per-resume KNN scan will dominate anyway.
create index if not exists idx_resume_chunks_embedding
    on resume_chunks using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

-- ============================================================
-- Option B: mxbai-embed-large (1024 dims) or bge-m3 (1024 dims)
-- ============================================================
-- drop index if exists idx_resume_chunks_embedding;
-- alter table resume_chunks alter column embedding type vector(1024);
-- update resume_chunks set embedding = null;
-- create index if not exists idx_resume_chunks_embedding
--     on resume_chunks using ivfflat (embedding vector_cosine_ops)
--     with (lists = 100);
