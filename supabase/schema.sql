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
    created_at  timestamptz not null default now()
);

create index if not exists idx_resumes_user_id on resumes(user_id);

-- ============================================================
-- Resume chunks (the unit stored in the vector index)
-- text-embedding-3-small produces 1536-dimensional vectors
-- ============================================================
create table if not exists resume_chunks (
    id           uuid primary key default gen_random_uuid(),
    resume_id    uuid not null references resumes(id) on delete cascade,
    chunk_index  int not null,
    section      text,
    content      text not null,
    embedding    vector(1536)
);

create index if not exists idx_resume_chunks_resume_id on resume_chunks(resume_id);

-- Approximate nearest neighbour index for fast cosine-distance search.
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
    created_at             timestamptz not null default now()
);

create index if not exists idx_analyses_user_id on analyses(user_id);
