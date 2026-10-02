package com.resumerag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.analysis.AnalysisDetailResponse;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A single analysis report as the client consumes it.
 *
 * <p>Carries the candidate name and the target role alongside the result so a
 * report is self-describing: a list row can say "Priya Sharma - Frontend
 * Developer" without the client having to join resumes and job descriptions
 * itself. The list previously hardcoded the same label on every row, because
 * none of this was available here.
 *
 * <p><b>On the two halves of this record.</b> The first fifteen components are the
 * original contract and are unchanged - same names, same types, same meaning - so
 * an existing client keeps working against an existing report, including one
 * analysed before evidence-based scoring existed. {@code detail} is new and
 * nullable: it is populated for analyses run by the current engine and
 * {@code null} for older ones, which is what lets a report created last month
 * still load rather than failing to deserialise.
 *
 * <p>{@code recommendations} is likewise additive and nullable, and is only
 * present when the detail is - it is derived from the same evidence.
 */
public record AnalysisResponse(
        UUID id,
        UUID resumeId,
        UUID jobDescriptionId,
        String candidateName,
        String resumeFileName,
        String jobTitle,
        String jobCompany,
        int matchScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> matchedSkills,
        List<String> missingSkills,
        Instant createdAt,
        AnalysisDetailResponse detail,
        List<String> recommendations
) {

    /** The original fifteen-field shape, for callers that do not need the detail. */
    public AnalysisResponse(UUID id, UUID resumeId, UUID jobDescriptionId, String candidateName,
                            String resumeFileName, String jobTitle, String jobCompany, int matchScore,
                            String summary, List<String> strengths, List<String> gaps,
                            List<String> matchedSkills, List<String> missingSkills, Instant createdAt) {
        this(id, resumeId, jobDescriptionId, candidateName, resumeFileName, jobTitle, jobCompany,
                matchScore, summary, strengths, gaps, matchedSkills, missingSkills, createdAt, null, null);
    }

    public static AnalysisResponse from(Analysis a, Resume resume, JobDescription job, ObjectMapper mapper) {
        return from(a, resume, job, mapper, null, null);
    }

    public static AnalysisResponse from(Analysis a, Resume resume, JobDescription job, ObjectMapper mapper,
                                        AnalysisDetailResponse detail, List<String> recommendations) {
        return new AnalysisResponse(
                a.getId(),
                a.getResumeId(),
                a.getJobDescriptionId(),
                candidateName(resume, a),
                resume == null ? null : resume.getFileName(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                a.getMatchScore(),
                a.getSummary() == null ? "" : a.getSummary(),
                readList(mapper, a.getStrengthsJson()),
                readList(mapper, a.getGapsJson()),
                readList(mapper, a.getMatchedSkillsJson()),
                readList(mapper, a.getMissingSkillsJson()),
                a.getCreatedAt(),
                detail,
                recommendations
        );
    }

    /**
     * Prefers the stored name, then anything recoverable from the raw text, then
     * the file name, so a row created before candidate_name existed is still
     * labelled with a person rather than a filename.
     */
    private static String candidateName(Resume resume, Analysis a) {
        if (resume == null) {
            return null;
        }
        if (resume.getCandidateName() != null && !resume.getCandidateName().isBlank()) {
            return resume.getCandidateName().trim();
        }
        if (resume.getRawText() != null && !resume.getRawText().isBlank()) {
            String firstLine = resume.getRawText().split("\\R")[0].trim();
            if (!firstLine.isEmpty() && firstLine.length() <= 60) {
                return firstLine;
            }
        }
        return resume.getFileName();
    }

    /**
     * The list columns are stored as raw JSON text, so a null/blank column (older
     * rows, or a partially written analysis) must degrade to an empty list rather
     * than NPE inside List.of(...).
     */
    private static List<String> readList(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String[] values = mapper.readValue(json, String[].class);
            return values == null ? List.of() : List.of(values);
        } catch (Exception e) {
            return List.of();
        }
    }
}
