package com.resumerag.analysis.assess;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.ProjectAssessment;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import com.resumerag.analysis.evidence.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores the projects a resume describes against the role.
 *
 * <p><b>Only assessed when the resume actually describes projects.</b> A resume
 * with no projects section returns no score at all rather than a zero or an
 * average of nothing, because there is no project evidence to assess. Work bullets
 * are deliberately not borrowed for this: RESPONSIBILITIES already scores the role's
 * duties, and re-using the same sentences for a second 5% would count one piece of
 * evidence twice.
 *
 * <p>Three factors, each independently explainable, because a project can match on
 * technology and miss on substance and a single number hides that:
 *
 * <ul>
 *   <li><b>Technology relevance</b> - the share of the role's <em>required</em>
 *       technologies the project actually uses. Weighted most heavily, since
 *       building with the stack is the point.</li>
 *   <li><b>Responsibility relevance</b> - alignment between the project's wording
 *       and the role's duties, which is what separates "used Spring Boot" from
 *       "used Spring Boot to build a REST API for an expense platform".</li>
 *   <li><b>Deliverable evidence</b> - whether the project states an outcome. A
 *       project with a result is applied work; one without is a class exercise.</li>
 * </ul>
 *
 * <p>One generic keyword does not make a relevant project. A Python image
 * classifier against a Java and Spring Boot role shares no required technology, and
 * is scored as unrelated however confidently the words appear.
 */
@Service
public class ProjectRelevanceService {

    private static final Logger log = LoggerFactory.getLogger(ProjectRelevanceService.class);

    /** Section headings that introduce projects, matched on the heading line itself. */
    private static final List<String> PROJECT_HEADINGS = List.of(
            "projects", "personal projects", "selected projects", "side projects",
            "key projects", "portfolio projects", "notable projects", "projects and research");

    /** A line that opens a project inside a projects section: "Ledger Service (2023)". */
    private static final Pattern PROJECT_TITLE = Pattern.compile(
            "^(.{2,80}?)\\s*(?:\\((?:19|20)\\d{2}\\))?\\s*$");

    private final ScoringConfiguration configuration;

    public ProjectRelevanceService(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The projects a resume describes, scored against the requirements.
     *
     * @return the assessments, or an empty list when the resume describes none
     */
    public List<ProjectAssessment> assess(ResumeProfile profile, List<Requirement> requirements) {
        List<ProjectBlock> projects = extractProjects(profile);
        if (projects.isEmpty()) {
            return List.of();
        }

        Set<String> requiredTechnologies = requiredTechnologies(requirements);
        List<String> responsibilities = responsibilityTexts(requirements);

        List<ProjectAssessment> assessments = new ArrayList<>(projects.size());
        for (ProjectBlock project : projects) {
            Set<String> technologies = new LinkedHashSet<>();
            for (String line : project.lines()) {
                technologies.addAll(SynonymRegistry.canonicalKeysIn(line));
            }

            double technologyRelevance = overlap(technologies, requiredTechnologies);
            double responsibilityRelevance = responsibilityAlignment(project.lines(), responsibilities);
            boolean hasOutcome = project.lines().stream().anyMatch(SynonymRegistry::isOutcomeBearing);

            double score = technologyRelevance * configuration.getProjectTechnologyWeight()
                    + responsibilityRelevance * configuration.getProjectResponsibilityWeight()
                    + (hasOutcome ? 1.0 : 0.0) * configuration.getProjectOutcomeWeight();

            assessments.add(new ProjectAssessment(
                    project.title(), project.lines(), technologies.stream().sorted().toList(),
                    hasOutcome, technologyRelevance, responsibilityRelevance,
                    round(score * 100)));
        }
        return assessments;
    }

    /**
     * The projects a resume describes, as title-plus-description blocks.
     *
     * <p>A line in the projects section that is short and carries no technology and
     * no full stop is treated as the next project's title, because that is the
     * conventional layout: a heading, then bullets.
     */
    private List<ProjectBlock> extractProjects(ResumeProfile profile) {
        List<ProjectBlock> projects = new ArrayList<>();
        ProjectBlock current = null;
        boolean inProjectsSection = false;

        for (ResumeProfile.Line line : profile.lines()) {
            boolean inProjects = line.section() == ResumeProfile.Line.Section.PROJECTS;
            if (inProjects && !inProjectsSection) {
                // Entering the section. The line itself is still processed below -
                // consuming it here is what silently dropped a resume's first
                // project.
                inProjectsSection = true;
                current = null;
            } else if (!inProjects && inProjectsSection) {
                // The section has ended; a projects list is contiguous in practice.
                break;
            }
            if (!inProjects) {
                continue;
            }

            if (isLikelyTitle(line.text())) {
                current = new ProjectBlock(line.text());
                projects.add(current);
            } else if (current == null) {
                current = new ProjectBlock("Project");
                projects.add(current);
            } else {
                current.lines().add(line.text());
            }
        }
        return projects;
    }

    /**
     * Whether a line reads as a project heading rather than a description.
     *
     * <p>Short, no terminal punctuation, and nothing that says it is work: a
     * heading names a thing, a description explains it.
     */
    private boolean isLikelyTitle(String text) {
        String trimmed = text.trim();
        if (trimmed.length() > 80 || trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.endsWith(".") || trimmed.endsWith(";")) {
            return false;
        }
        // A description with a verb in it is not a heading.
        if (SynonymRegistry.canonicalKeysIn(trimmed).size() >= 2) {
            return false;
        }
        if (trimmed.split("\\s+").length > 12) {
            return false;
        }
        return PROJECT_TITLE.matcher(trimmed).matches();
    }

    /**
     * The technologies the role requires.
     *
     * <p>Only required skills, deliberately. Measuring a project against the
     * preferred list would let a candidate score well on project relevance by
     * demonstrating precisely the technologies that are optional.
     */
    private Set<String> requiredTechnologies(List<Requirement> requirements) {
        Set<String> keys = new LinkedHashSet<>();
        for (Requirement requirement : requirements) {
            if (requirement.category() == RequirementCategory.REQUIRED_SKILL
                    || requirement.category() == RequirementCategory.PREFERRED_SKILL) {
                requirement.normalization().terms().forEach(t -> keys.add(t.canonicalKey()));
            }
        }
        return keys;
    }

    private List<String> responsibilityTexts(List<Requirement> requirements) {
        return requirements.stream()
                .filter(r -> r.category() == RequirementCategory.RESPONSIBILITY)
                .map(r -> SynonymRegistry.normalizePhrase(r.sourceText()))
                .toList();
    }

    private double overlap(Set<String> technologies, Set<String> required) {
        if (required.isEmpty() || technologies.isEmpty()) {
            return 0.0;
        }
        long shared = technologies.stream().filter(required::contains).count();
        return (double) shared / required.size();
    }

    /**
     * How much of the project's wording lines up with the role's duties.
     *
     * <p>Token overlap against the job description's own responsibility lines,
     * which is why "Built a Spring Boot REST API for an expense management
     * platform" scores higher against a role that asks to "Design and integrate
     * RESTful APIs" than one that merely lists Spring Boot.
     */
    private double responsibilityAlignment(List<String> projectLines, List<String> responsibilities) {
        if (responsibilities.isEmpty() || projectLines.isEmpty()) {
            return 0.0;
        }
        Set<String> projectTokens = contentTokens(String.join(" ", projectLines));
        if (projectTokens.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (String responsibility : responsibilities) {
            Set<String> tokens = contentTokens(responsibility);
            if (tokens.isEmpty()) {
                continue;
            }
            long shared = tokens.stream().filter(projectTokens::contains).count();
            best = Math.max(best, (double) shared / tokens.size());
        }
        return best;
    }

    private Set<String> contentTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : text.split("[^a-z0-9+#.]+")) {
            String cleaned = word.replaceAll("[.#]", "").toLowerCase(Locale.ROOT);
            if (cleaned.length() > 2) {
                tokens.add(cleaned);
            }
        }
        return tokens;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** A project as the resume presents it: a heading and the lines beneath it. */
    private static final class ProjectBlock {
        private final String title;
        private final List<String> lines = new ArrayList<>();

        ProjectBlock(String title) {
            this.title = title;
        }

        String title() { return title; }
        List<String> lines() { return lines; }
    }
}
