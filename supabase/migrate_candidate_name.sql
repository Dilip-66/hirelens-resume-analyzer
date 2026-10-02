-- Run this ONCE in the Supabase SQL editor.
--
-- Adds the candidate's name to a resume so an analysis report can say whose
-- resume it is about, instead of every row reading "Analysis Report".
--
-- Additive and non-destructive: new nullable column, no existing data touched.
-- Safe to re-run.

alter table resumes add column if not exists candidate_name text;

-- Backfill from the existing rows: resumes in this project put the candidate's
-- name on the first non-empty line, with the file name as the fallback shape
-- that extractFileName() in CandidateNameExtractor mirrors.
--
-- COALESCE picks the first non-blank candidate, so a resume that already has a
-- good extracted name is never overwritten by the cruder file-name guess.
update resumes
set candidate_name = COALESCE(
    nullif(trim(split_part(raw_text, E'\n', 1)), ''),
    nullif(trim(regexp_replace(file_name, '\.[A-Za-z0-9]+$', '')), ''),
    'Unknown Candidate'
)
where candidate_name is null or btrim(candidate_name) = '';
