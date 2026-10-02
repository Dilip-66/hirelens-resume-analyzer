-- Run this in the Supabase SQL editor before starting the backend.

create extension if not exists vector;

-- ============================================================
-- Users (for self-contained auth)
-- ============================================================
create table if not exists users (
    id            uuid primary key default gen_random_uuid(),
    email         text not null unique,
    password_hash text not null,
    name          text not null,
    created_at    timestamptz not null default now()
);

-- ============================================================
-- Resumes
-- ============================================================
create table if not exists resumes (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    file_name   text not null,
    raw_text    text not null,
    -- Display name for the candidate, extracted from the first line of raw_text
    -- with the file name as fallback. Nullable so older rows still load.
    candidate_name text,
    created_at  timestamptz not null default now()
);

create index if not exists idx_resumes_user_id on resumes(user_id);

-- ============================================================
-- Resume chunks (the unit stored in the vector index)
-- Dimension must match the Ollama embedding model:
--   nomic-embed-text -> 768   (default)
--   mxbai-embed-large -> 1024
--   bge-m3 -> 1024
-- If you change OLLAMA_EMBEDDING_DIMENSIONS, run migrate_embedding_dimension.sql.
-- ============================================================
create table if not exists resume_chunks (
    id           uuid primary key default gen_random_uuid(),
    resume_id    uuid not null references resumes(id) on delete cascade,
    chunk_index  int not null,
    section      text,
    content      text not null,
    embedding    vector(768)
);

create index if not exists idx_resume_chunks_resume_id on resume_chunks(resume_id);

-- Approximate nearest neighbour index for fast cosine-distance search.
--
-- NOTE: per-resume retrieval is intentionally an exact KNN scan (see
-- ChunkVectorRepository.findNearestCandidates), because a single resume is only
-- a few dozen chunks and an ivfflat index with lists = 100 probes a single
-- partition by default, which returns zero rows at that scale. This index only
-- becomes worthwhile once the search is widened across many resumes - and
-- `lists` should then be sized to roughly rows / 1000, not left at 100.
create index if not exists idx_resume_chunks_embedding
    on resume_chunks using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

-- ============================================================
-- Job descriptions
-- ============================================================
create table if not exists job_descriptions (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    title       text,
    company     text,
    raw_text    text not null,
    created_at  timestamptz not null default now()
);

create index if not exists idx_job_descriptions_user_id on job_descriptions(user_id);

-- ============================================================
-- Analyses (persisted RAG output)
-- ============================================================
create table if not exists analyses (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null references users(id) on delete cascade,
    resume_id              uuid not null references resumes(id) on delete cascade,
    job_description_id     uuid not null references job_descriptions(id) on delete cascade,
    match_score            int not null,
    summary                text,
    strengths_json         text,
    gaps_json              text,
    matched_skills_json    text,
    missing_skills_json    text,
    retrieved_chunks_json  text,
    -- The explainable report header: score, label, experience alignment and
    -- advice. Nullable, and null for every analysis run before evidence-based
    -- scoring existed; the API renders those exactly as before. The per-requirement
    -- evidence behind it lives in analysis_requirements / analysis_matches.
    details_json           text,
    created_at             timestamptz not null default now()
);

create index if not exists idx_analyses_user_id on analyses(user_id);

-- ============================================================
-- Evidence-based analysis detail
-- ============================================================
-- The three tables below back the explainable analysis: what the job description
-- asked for, what the resume offered, and how the score was built from the two.
--
-- They are new rather than more JSON columns on `analyses` because a score is only
-- arguable if the reasoning behind it is queryable - which requirement was
-- required, how load-bearing it was, which sentence decided its match. That is
-- also what lets an old analysis be re-explained without re-running the model.
--
-- Nullable by design: `analyses.match_score` stays the single score a report
-- shows, and rows here are absent for analyses created before this change. The API
-- treats their absence as "old analysis" and omits the detail rather than
-- inventing it.

-- One requirement, as stated by the job description.
create table if not exists analysis_requirements (
    id                   uuid primary key default gen_random_uuid(),
    analysis_id          uuid not null references analyses(id) on delete cascade,
    -- Ordinal within the analysis: requirement 7 is the same one on re-read.
    requirement_index    int not null,
    name                 text not null,
    -- Stable identity across the JD's wording, e.g. ANY_OF_AWS_CLOUD_PLATFORM.
    -- This is what stops "REST APIs" and "RESTful APIs" being counted twice.
    normalized_name      text not null,
    category             text not null,
    importance           text not null,
    -- How much capability the JD demanded: BASIC | PROFESSIONAL | ADVANCED.
    demand               text not null,
    -- The job description line, verbatim.
    source_text          text not null,
    synonyms_json        text,
    explicit_requirement boolean not null default true,
    created_at           timestamptz not null default now()
);

create index if not exists idx_analysis_requirements_analysis
    on analysis_requirements(analysis_id);

-- How the resume measured up to one requirement, with the evidence on both sides.
create table if not exists analysis_matches (
    id                     uuid primary key default gen_random_uuid(),
    analysis_requirement_id uuid not null references analysis_requirements(id) on delete cascade,
    -- EXPLICIT_MATCH | STRONG_CONTEXTUAL_MATCH | PARTIAL_MATCH
    -- | NOT_EXPLICITLY_MENTIONED | CONFLICT
    status                 text not null,
    confidence             double precision not null,
    -- Evidence quality 0-4, where 4 means the technology was used in
    -- professional work and 3 means it was listed as a skill. This is the column
    -- that stops a keyword hit and five years of shipping it looking identical.
    evidence_strength      int not null,
    -- EXPLICIT | INFERRED | NOT_EXPLICIT: how the connection was drawn. An
    -- inferred match is the engine's reading, not the resume's statement, and the
    -- report says which it is.
    provenance             text not null,
    resume_evidence_json   text,
    jd_evidence_json       text,
    explanation            text,
    created_at             timestamptz not null default now()
);

create index if not exists idx_analysis_matches_requirement
    on analysis_matches(analysis_requirement_id);

-- One row per score dimension, including the dimensions that were not assessed.
create table if not exists analysis_score_breakdowns (
    id                      uuid primary key default gen_random_uuid(),
    analysis_id             uuid not null references analyses(id) on delete cascade,
    category                text not null,
    -- Null when the dimension could not be measured. Deliberately never faked: an
    -- invented 80% for education that was never assessed is worse than admitting
    -- the dimension was skipped, and the overall score renormalises over the
    -- dimensions that were assessed.
    score                   double precision,
    weight                  double precision not null,
    weighted_score          double precision,
    requirements_considered int not null default 0,
    -- Why this dimension is, or is not, scored.
    note                    text,
    created_at              timestamptz not null default now()
);

create index if not exists idx_analysis_score_breakdowns_analysis
    on analysis_score_breakdowns(analysis_id);

-- ============================================================
-- Row Level Security
-- ============================================================
-- Without this, anyone holding the project's anon key can read every resume and
-- analysis through Supabase's auto-exposed PostgREST API (https://<ref>.supabase.co/rest/v1/).
-- Resumes are PII, so lock every table down by default.
--
-- This does NOT affect the Spring Boot backend: it connects over JDBC as the table
-- owner, and owners bypass RLS. The frontend only ever talks to our own API
-- (see frontend/src/lib/api-client.ts), never to Supabase directly.
--
-- These policies are scoped to Supabase Auth and are inert while the backend uses
-- its own BCrypt + JWT auth. They are here so that switching to Supabase Auth
-- later (see .env.example) does not silently expose data.

-- Resolve the caller to a row in `users` from their Supabase JWT.
-- SECURITY DEFINER avoids infinite recursion: reading `users` to resolve the id
-- would otherwise re-enter the `users` RLS policy on every call.
create or replace function app_current_user_id() returns uuid
    language sql
    stable
    security definer
    set search_path = public
as $$
    select u.id
    from public.users u
    where u.id = auth.uid()
       or (auth.jwt() ->> 'email') is not null
          and u.email = lower(auth.jwt() ->> 'email')
    limit 1
$$;

alter table users            enable row level security;
alter table resumes          enable row level security;
alter table resume_chunks    enable row level security;
alter table job_descriptions enable row level security;
alter table analyses         enable row level security;
alter table analysis_requirements    enable row level security;
alter table analysis_matches        enable row level security;
alter table analysis_score_breakdowns enable row level security;

-- Deliberately NOT "force row level security": the backend connects over JDBC as
-- the table owner, and FORCE would make RLS apply to the owner as well, denying
-- every query (the owner has no Supabase JWT, so app_current_user_id() is NULL).
-- Plain ENABLE is enough here - it still covers the anon/authenticated roles that
-- PostgREST exposes.

drop policy if exists "users_select_own" on users;
create policy "users_select_own" on users
    for select to authenticated
    using (id = app_current_user_id());

drop policy if exists "users_update_own" on users;
create policy "users_update_own" on users
    for update to authenticated
    using (id = app_current_user_id())
    with check (id = app_current_user_id());

drop policy if exists "users_delete_own" on users;
create policy "users_delete_own" on users
    for delete to authenticated
    using (id = app_current_user_id());

drop policy if exists "resumes_own" on resumes;
create policy "resumes_own" on resumes
    for all to authenticated
    using (user_id = app_current_user_id())
    with check (user_id = app_current_user_id());

drop policy if exists "job_descriptions_own" on job_descriptions;
create policy "job_descriptions_own" on job_descriptions
    for all to authenticated
    using (user_id = app_current_user_id())
    with check (user_id = app_current_user_id());

drop policy if exists "analyses_own" on analyses;
create policy "analyses_own" on analyses
    for all to authenticated
    using (user_id = app_current_user_id())
    with check (user_id = app_current_user_id());

-- The analysis detail tables carry no user_id, so ownership is derived through the
-- parent analysis, the same way resume_chunks derives it through its resume.
drop policy if exists "analysis_requirements_own" on analysis_requirements;
create policy "analysis_requirements_own" on analysis_requirements
    for all to authenticated
    using (
        exists (
            select 1 from analyses a
            where a.id = analysis_requirements.analysis_id
              and a.user_id = app_current_user_id()
        )
    )
    with check (
        exists (
            select 1 from analyses a
            where a.id = analysis_requirements.analysis_id
              and a.user_id = app_current_user_id()
        )
    );

drop policy if exists "analysis_matches_own" on analysis_matches;
create policy "analysis_matches_own" on analysis_matches
    for all to authenticated
    using (
        exists (
            select 1 from analysis_requirements r
            join analyses a on a.id = r.analysis_id
            where r.id = analysis_matches.analysis_requirement_id
              and a.user_id = app_current_user_id()
        )
    )
    with check (
        exists (
            select 1 from analysis_requirements r
            join analyses a on a.id = r.analysis_id
            where r.id = analysis_matches.analysis_requirement_id
              and a.user_id = app_current_user_id()
        )
    );

drop policy if exists "analysis_score_breakdowns_own" on analysis_score_breakdowns;
create policy "analysis_score_breakdowns_own" on analysis_score_breakdowns
    for all to authenticated
    using (
        exists (
            select 1 from analyses a
            where a.id = analysis_score_breakdowns.analysis_id
              and a.user_id = app_current_user_id()
        )
    )
    with check (
        exists (
            select 1 from analyses a
            where a.id = analysis_score_breakdowns.analysis_id
              and a.user_id = app_current_user_id()
        )
    );

-- resume_chunks has no user_id, so ownership is derived through the parent resume.
drop policy if exists "resume_chunks_own" on resume_chunks;
create policy "resume_chunks_own" on resume_chunks
    for all to authenticated
    using (
        exists (
            select 1 from resumes r
            where r.id = resume_chunks.resume_id
              and r.user_id = app_current_user_id()
        )
    )
    with check (
        exists (
            select 1 from resumes r
            where r.id = resume_chunks.resume_id
              and r.user_id = app_current_user_id()
        )
    );

-- Belt and braces: the anon role (anyone with just the public anon key) gets nothing.
revoke all on all tables in schema public from anon;
grant select, insert, update, delete on all tables in schema public to authenticated;
