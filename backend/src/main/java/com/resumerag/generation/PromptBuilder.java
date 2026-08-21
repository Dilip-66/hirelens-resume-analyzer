package com.resumerag.generation;

import com.resumerag.dto.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String buildUserPrompt(String jobDescriptionText,
                                   List<RetrievedChunk> retrievedChunks,
                                   Set<String> requiredSkills,
                                   Set<String> matchedSkills,
                                   Set<String> missingSkills) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Job Description\n\n");
        sb.append(jobDescriptionText).append("\n\n");

        sb.append("## Retrieved Resume Excerpts\n\n");
        if (retrievedChunks != null && !retrievedChunks.isEmpty()) {
            for (int i = 0; i < retrievedChunks.size(); i++) {
                RetrievedChunk chunk = retrievedChunks.get(i);
                sb.append("### Excerpt ").append(i + 1).append("\n");
                sb.append("[section: ").append(chunk.section()).append(" | similarity: ").append(String.format("%.2f", chunk.similarity())).append("]\n");
                sb.append(chunk.content()).append("\n\n");
            }
        } else {
            sb.append("No relevant resume excerpts found.\n\n");
        }

        sb.append("## Skill Scan Results\n\n");
        sb.append("**Required Skills:** ");
        sb.append(requiredSkills != null && !requiredSkills.isEmpty()
                ? String.join(", ", requiredSkills)
                : "None specified");
        sb.append("\n\n");

        sb.append("**Matched Skills:** ");
        sb.append(matchedSkills != null && !matchedSkills.isEmpty()
                ? String.join(", ", matchedSkills)
                : "None matched");
        sb.append("\n\n");

        sb.append("**Missing Skills:** ");
        sb.append(missingSkills != null && !missingSkills.isEmpty()
                ? String.join(", ", missingSkills)
                : "None missing");
        sb.append("\n\n");

        sb.append("Based on the above, provide a detailed analysis of the candidate's fit for this role. "
                + "Return your response as a JSON object with the following structure:\n");
        sb.append("{\n");
        sb.append("  \"overallScore\": <number>,\n");
        sb.append("  \"summary\": \"<string>\",\n");
        sb.append("  \"strengths\": [\"<string>\", ...],\n");
        sb.append("  \"weaknesses\": [\"<string>\", ...],\n");
        sb.append("  \"recommendations\": [\"<string>\", ...]\n");
        sb.append("}\n");

        return sb.toString();
    }
}
