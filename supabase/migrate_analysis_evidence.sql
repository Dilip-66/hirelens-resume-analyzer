-- Migration: evidence-based analysis detail.
--
-- Run this in the Supabase SQL editor for an EXISTING database, then restart the
-- backend. New databases get these tables from supabase/schema.sql directly.
--
-- Safe to run more than once. One column is added to `analyses` (nullable, so
-- existing rows are unaffected) and three tables are created. Nothing existing is
-- altered, truncated or dropped, and no analysis row is touched: analyses created
-- before this migration keep working and simply have no detail rows, which the API
-- treats as "old report" and renders without the richer explanation rather than
-- inventing one.
--
-- The backend runs with spring.jpa.hibernate.ddl-auto=validate, so it will fail
-- to start until these tables exist. That is intentional: validate exists to catch
-- schema drift instead of quietly creating tables with wrong column types.

-- The report header. Nullable, so every existing analysis row is unaffected and
-- keeps loading: a null here means "analysed before evidence-based scoring", and
-- the API renders those reports exactly as it always did.
alter table analyses add column if not exists details_json text;

create table if not exists analysis_requirements (
    id                   uuid primary key default gen_random_uuid(),
    analysis_id          uuid not null references analyses(id) on delete cascade,
    requirement_index    int not null,
    name                 text not null,
    normalized_name      text not null,
    category             text not null,
    importance           text not null,
    demand               text not null,
    source_text          text not null,
    synonyms_json        text,
    explicit_requirement boolean not null default true,
    created_at           timestamptz not null default now()
);

create index if not exists idx_analysis_requirements_analysis
    on analysis_requirements(analysis_id);

create table if not exists analysis_matches (
    id                     uuid primary key default gen_random_uuid(),
    analysis_requirement_id uuid not null references analysis_requirements(id) on delete cascade,
    status                 text not null,
    confidence             double precision not null,
    evidence_strength      int not null,
    provenance             text not null,
    resume_evidence_json   text,
    jd_evidence_json       text,
    explanation            text,
    created_at             timestamptz not null default now()
);

create index if not exists idx_analysis_matches_requirement
    on analysis_matches(analysis_requirement_id);

create table if not exists analysis_score_breakdowns (
    id                      uuid primary key default gen_random_uuid(),
    analysis_id             uuid not null references analyses(id) on delete cascade,
    category                text not null,
    score                   double precision,
    weight                  double precision not null,
    weighted_score          double precision,
    requirements_considered int not null default 0,
    note                    text,
    created_at              timestamptz not null default now()
);

create index if not exists idx_analysis_score_breakdowns_analysis
    on analysis_score_breakdowns(analysis_id);

-- ============================================================
-- Row Level Security
-- ============================================================
-- These tables hold resume evidence - the sentences a candidate wrote - and are
-- PII. They get the same treatment as resumes: locked down, and scoped through the
-- parent analysis's owner because they carry no user_id of their own.
--
-- Inert while the backend uses its own BCrypt + JWT auth (it connects over JDBC
-- as the table owner, and owners bypass RLS). Here so that switching to Supabase
-- Auth later cannot silently expose a candidate's resume text.

alter table analysis_requirements     enable row level security;
alter table analysis_matches         enable row level security;
alter table analysis_score_breakdowns enable row level security;

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
