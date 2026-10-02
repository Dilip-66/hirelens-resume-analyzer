package com.resumerag.assistant;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The suggested-question catalogue served to the client, plus the rules for
 * generating questions that reflect a specific analysis.
 *
 * <p>Backend-owned on purpose. These strings shape what the model is asked and
 * what the user is offered, so they belong next to the system prompt where they
 * can be reviewed and changed in one place, rather than scattered through React
 * components where a UI refactor could silently alter assistant behaviour.
 */
@Component
public class QuestionCatalog {

    public record QuestionCategory(String id, String label, List<String> questions) {}

    private static final List<QuestionCategory> CATEGORIES = List.of(
            new QuestionCategory("score", "Understand my score", List.of(
                    "Why did I get this score?",
                    "What is lowering my match score?",
                    "What parts of my resume matched the job?",
                    "What parts of my resume did not match?",
                    "How can I increase my match score?",
                    "Explain my score in simple terms.")),
            new QuestionCategory("skills", "Skills", List.of(
                    "Which skills am I missing?",
                    "Which skills are already strong on my resume?",
                    "Which skills are most important for this job?",
                    "Which skills should I highlight more?",
                    "Are my technical skills relevant to this role?",
                    "What keywords should I consider adding?")),
            new QuestionCategory("experience", "Experience", List.of(
                    "Is my experience relevant to this job?",
                    "What experience gaps do I have?",
                    "How can I present my experience better?",
                    "Which projects should I highlight?",
                    "Does my experience level match the job requirement?")),
            new QuestionCategory("improvement", "Resume improvement", List.of(
                    "How can I improve my resume?",
                    "What should I change first?",
                    "How can I make my resume more ATS-friendly?",
                    "Which sections need improvement?",
                    "How can I improve my project descriptions?",
                    "How can I improve my experience descriptions?",
                    "Are my achievements strong enough?")),
            new QuestionCategory("job", "Job description", List.of(
                    "What are the most important requirements in this job?",
                    "What skills does this company appear to prioritize?",
                    "What keywords are important in this job description?",
                    "Which requirements do I already satisfy?",
                    "Which requirements do I not satisfy?")),
            new QuestionCategory("general", "General", List.of(
                    "How does ATS scoring work?",
                    "What makes a resume ATS-friendly?",
                    "How should I tailor my resume for different jobs?",
                    "What makes a strong resume summary?",
                    "How can I write better achievement bullets?"))
    );

    public List<QuestionCategory> categories() {
        return CATEGORIES;
    }

    /**
     * Headline question shown on the post-analysis prompt.
     *
     * <p>Derived from the actual stored score - these are UI prompts, not scoring
     * logic, and the score itself is never altered or re-derived here.
     */
    public String contextualQuestion(int matchScore) {
        if (matchScore >= 90) {
            return "Want to know what makes your resume a strong match?";
        }
        if (matchScore >= 70) {
            return "Want to know what is keeping your score from being higher?";
        }
        if (matchScore >= 40) {
            return "Want to know which skills are holding your score back?";
        }
        if (matchScore >= 1) {
            return "Want to know which skills are missing from your resume?";
        }
        return "Want to understand why your resume didn't match this job?";
    }

    /**
     * The question the post-analysis CTA actually asks.
     *
     * <p>Distinct from {@link #contextualQuestion(int)}: that string is UI copy
     * shown to the user, and sending it to the model verbatim would arrive as a
     * statement rather than a question. This returns a real catalogue question
     * for the same score band, which the model is always equipped to ground.
     */
    public String catalogueQuestion(int matchScore) {
        if (matchScore >= 90) {
            return "What parts of my resume matched the job?";
        }
        if (matchScore >= 70) {
            return "What is lowering my match score?";
        }
        if (matchScore >= 1) {
            return "Which skills am I missing?";
        }
        return "Why did I get this score?";
    }

    /**
     * Follow-ups ordered for a specific analysis result.
     *
     * <p>Everything returned is an existing catalogue question, so a follow-up
     * can never be a prompt the model has not been given a grounded way to
     * answer.
     */
    public List<String> prioritizedQuestions(int matchScore, int missingSkillCount) {
        List<String> prioritized = new java.util.ArrayList<>();

        if (matchScore <= 0) {
            prioritized.add("Why did I get this score?");
            prioritized.add("Which skills am I missing?");
            prioritized.add("What are the most important requirements in this job?");
        } else if (matchScore < 70) {
            prioritized.add("What is lowering my match score?");
            prioritized.add("Which skills am I missing?");
            prioritized.add("What should I change first?");
        } else {
            prioritized.add("What is lowering my match score?");
            if (missingSkillCount > 0) {
                prioritized.add("Which skills am I missing?");
            } else {
                prioritized.add("How can I increase my match score?");
            }
            prioritized.add("How can I improve my resume?");
        }

        return prioritized.stream()
                .filter(q -> findCategory(q) != null)
                .distinct()
                .limit(3)
                .toList();
    }

    /** Category a question belongs to, or null if it is not from the catalogue. */
    public String findCategory(String question) {
        if (question == null) {
            return null;
        }
        for (QuestionCategory category : CATEGORIES) {
            if (category.questions().contains(question)) {
                return category.id();
            }
        }
        return null;
    }

    /**
     * Read-only view of the catalogue for logging/diagnostics. Never returned to
     * clients, which receive the structured list instead.
     */
    public Map<String, List<String>> asMap() {
        return CATEGORIES.stream().collect(
                java.util.stream.Collectors.toMap(QuestionCategory::id, QuestionCategory::questions));
    }
}
