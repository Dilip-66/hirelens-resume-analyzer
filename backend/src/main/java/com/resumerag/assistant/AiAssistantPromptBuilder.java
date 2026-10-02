package com.resumerag.assistant;

import com.resumerag.config.AppProperties;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the grounded user prompt for one assistant turn.
 *
 * <p>The shape mirrors the analysis pipeline's prompt: explicit evidence blocks
 * the model is told to rely on, with an explicit statement of what is not
 * available. The difference is that the analysis asks "score this resume"
 * whereas the assistant must answer "what did you say", so the analysis's
 * already-computed, deterministic fields are included verbatim - the model
 * summarises them, it never recalculates or contradicts them.
 */
@Component
public class AiAssistantPromptBuilder {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final QuestionCatalog catalog;

    public AiAssistantPromptBuilder(AppProperties appProperties, ObjectMapper objectMapper, QuestionCatalog catalog) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public String buildSystemPrompt() {
        return appProperties.getAi().getSystemPrompt();
    }

    public String build(String question,
                        List<RetrievedChunk> resumeChunks,
                        List<String> jobPassages,
                        Analysis analysis,
                        Resume resume,
                        JobDescription job,
                        List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();

        sb.append("## CURRENT QUESTION\n\n");
        sb.append(question).append("\n\n");

        sb.append("## AVAILABLE CONTEXT\n\n");

        if (analysis != null) {
            sb.append(buildAnalysisBlock(analysis));
        } else {
            sb.append("### ANALYSIS\n\n")
              .append("No completed analysis is available. Answer general resume and job-search ")
              .append("advice only, and say clearly that you cannot speak to this user's own resume, ")
              .append("skills or score because none has been analysed yet.\n\n");
        }

        if (resume != null) {
            sb.append("### RESUME IDENTITY\n\n")
              .append("Candidate: ").append(orUnknown(resume.getCandidateName())).append('\n')
              .append("File: ").append(orUnknown(resume.getFileName())).append("\n\n");
        }

        sb.append("### RETRIEVED RESUME PASSAGES\n\n");
        if (resumeChunks == null || resumeChunks.isEmpty()) {
            sb.append("No resume passages were retrieved for this question.\n\n");
        } else {
            for (int i = 0; i < resumeChunks.size(); i++) {
                RetrievedChunk chunk = resumeChunks.get(i);
                sb.append("[Excerpt ").append(i + 1)
                  .append(" | section: ").append(chunk.section() == null ? "unknown" : chunk.section())
                  .append("]\n")
                  .append(chunk.content()).append("\n\n");
            }
        }

        sb.append("### JOB DESCRIPTION PASSAGES\n\n");
        if (job == null) {
            sb.append("No job description is attached to this conversation.\n\n");
        } else if (jobPassages == null || jobPassages.isEmpty()) {
            sb.append("Role: ").append(orUnknown(job.getTitle())).append('\n')
              .append("Company: ").append(orUnknown(job.getCompany())).append('\n')
              .append("No specific passage from the job description was retrieved for this question.\n\n");
        } else {
            sb.append("Role: ").append(orUnknown(job.getTitle())).append('\n')
              .append("Company: ").append(orUnknown(job.getCompany())).append("\n\n");
            for (int i = 0; i < jobPassages.size(); i++) {
                sb.append("[JD passage ").append(i + 1).append("]\n")
                  .append(jobPassages.get(i)).append("\n\n");
            }
        }

        sb.append("## RULES FOR THIS ANSWER\n\n");
        sb.append("- Answer only from the context above. If it does not contain what is needed, ")
          .append("say so explicitly instead of guessing.\n");
        sb.append("- When you describe a skill, employer, date, metric or requirement, it must ")
          .append("appear in the context above. Do not infer plausible-sounding details.\n");
        sb.append("- The analysis block is authoritative. If your reading of the resume appears to ")
          .append("contradict it, report the analysis's value rather than overriding it.\n");
        sb.append("- Distinguish clearly between what the resume states, what the job description ")
          .append("requires, and your general advice. Use those three framings explicitly.\n");
        sb.append("- Be concise and concrete. Prefer a specific, actionable answer over general ")
          .append("encouragement.\n");
        sb.append("- End with a short \"suggestedFollowUps\" list of 2-3 questions, each chosen ")
          .append("VERBATIM from the catalogue below so the wording matches what the user would ")
          .append("ask. Do not invent new questions.\n");
        sb.append("- Also include a \"sources\" array listing only the excerpts and passages you ")
          .append("actually used, as objects with \"type\" (resume | jobDescription | analysis) and ")
          .append("\"section\". Omit it entirely if you used nothing.\n");

        sb.append("\n## QUESTION CATALOGUE (copy from this list for suggestedFollowUps)\n\n");
        for (Map.Entry<String, List<String>> entry : catalog.asMap().entrySet()) {
            sb.append(entry.getValue().stream()
                    .map(q -> "- " + q)
                    .collect(java.util.stream.Collectors.joining("\n")))
              .append('\n');
        }

        if (history != null && !history.isEmpty()) {
            sb.append("\n## EARLIER IN THIS CONVERSATION\n\n");
            for (Map<String, String> turn : history) {
                String who = "user".equals(turn.get("role")) ? "User" : "Assistant";
                sb.append(who).append(": ").append(turn.get("content")).append('\n');
            }
            sb.append('\n');
        }

        sb.append("\nRespond with a single JSON object and nothing else (no markdown fences):\n");
        sb.append("{\n");
        sb.append("  \"answer\": \"<string>\",\n");
        sb.append("  \"suggestedFollowUps\": [\"<string>\", ...],\n");
        sb.append("  \"sources\": [{\"type\": \"resume|jobDescription|analysis\", \"section\": \"<string>\"}, ...]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String buildAnalysisBlock(Analysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ANALYSIS (authoritative, produced earlier by the HireLens pipeline)\n\n");
        sb.append("Match score: ").append(analysis.getMatchScore()).append("%\n");
        sb.append("Summary: ").append(orNone(analysis.getSummary())).append('\n');
        sb.append("Matched skills: ").append(joinJsonArray(analysis.getMatchedSkillsJson())).append('\n');
        sb.append("Missing skills: ").append(joinJsonArray(analysis.getMissingSkillsJson())).append('\n');
        sb.append("Strengths: ").append(joinJsonArray(analysis.getStrengthsJson())).append('\n');
        sb.append("Gaps: ").append(joinJsonArray(analysis.getGapsJson())).append("\n\n");
        return sb.toString();
    }

    private String joinJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return "none recorded";
        }
        try {
            List<String> values = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (values == null || values.isEmpty()) {
                return "none recorded";
            }
            return String.join(", ", values);
        } catch (Exception e) {
            // A corrupt column must not break the assistant; say so plainly
            // rather than echoing raw JSON into the prompt.
            return "unavailable";
        }
    }

    private String orNone(String value) {
        return value == null || value.isBlank() ? "not recorded" : value;
    }

    private String orUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
