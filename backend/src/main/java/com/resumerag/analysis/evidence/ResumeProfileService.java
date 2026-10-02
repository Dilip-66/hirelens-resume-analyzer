package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.model.EvidenceStrength;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a resume into tagged lines and the candidate's stated years of
 * experience.
 *
 * <p>This is the piece that makes evidence strength possible. Whether "Docker"
 * appears in a skills list or in a sentence describing shipped work changes what
 * it proves, and a resume that has been flattened to a single string has already
 * lost the only signal that distinguishes them.
 *
 * <p>Section detection is heuristic and deliberately shallow: it looks for
 * conventional headings and for dated role lines, and anything it cannot place
 * falls into {@code OTHER} with a lower ceiling. Guessing wrong in that direction
 * costs a fraction of a match; guessing right would hand out professional-strength
 * evidence from a heading that merely looks like one.
 */
@Service
public class ResumeProfileService {

    /** Conventional resume headings, checked in order (most specific first). */
    private static final List<String[]> HEADINGS = List.of(
            new String[]{"professional experience", "work experience", "employment history",
                    "career history", "relevant experience", "experience", "employment", "career history"},
            new String[]{"technical skills", "core skills", "key skills", "skills & tools", "skills and tools",
                    "technical proficiencies", "technologies", "tech stack", "competencies", "skills",
                    "toolkit", "expertise"},
            new String[]{"summary", "professional summary", "profile", "objective", "about me", "overview"},
            new String[]{"personal projects", "projects", "side projects", "portfolio", "selected work"},
            new String[]{"education", "academic background", "qualifications"},
            new String[]{"certifications", "certificates", "licenses", "training", "courses"},
            new String[]{"awards", "achievements", "honors", "publications", "interests", "activities",
                    "languages", "references", "volunteering"});

    private static final Pattern BULLET = Pattern.compile(
            "^\\s*(?:[-*\\u2022\\u25cf\\u25aa\\u00b7\\u2013\\u2014>+]|\\d+[.)]|\\(?[a-z]\\))\\s*",
            Pattern.CASE_INSENSITIVE);

    /** A dated role heading: "Software Developer - Acme | 2021 - Present". */
    private static final Pattern DATED_ROLE = Pattern.compile(
            "\\b(19|20)\\d{2}\\b\\s*(?:-|–|—|to|until)\\s*"
                    + "(?:(19|20)\\d{2}\\b|present|current|now|ongoing|today)",
            Pattern.CASE_INSENSITIVE);

    private final ExperienceRequirementParser experienceParser;

    public ResumeProfileService(ExperienceRequirementParser experienceParser) {
        this.experienceParser = experienceParser;
    }

    public ResumeProfile build(String resumeText) {
        List<ResumeProfile.Line> lines = new ArrayList<>();
        ResumeProfile.Line.Section section = ResumeProfile.Line.Section.OTHER;

        if (resumeText == null || resumeText.isBlank()) {
            return new ResumeProfile(lines, null, null);
        }

        for (String rawLine : resumeText.split("\\R")) {
            String line = BULLET.matcher(rawLine).replaceAll("").trim();
            if (line.isEmpty()) {
                continue;
            }

            ResumeProfile.Line.Section heading = matchHeading(line);
            if (heading != null) {
                section = heading;
                continue;
            }
            // A dated line opens a work block when nothing else has claimed the
            // section. Deliberately not allowed to override a Projects heading:
            // "Ledger Service (2023)" looks exactly like a dated role line, and
            // letting it flip the section silently reclassified every project as
            // employment - which both inflated the inferred experience and made
            // the projects dimension permanently unassessable.
            if ((section == ResumeProfile.Line.Section.WORK
                    || section == ResumeProfile.Line.Section.OTHER)
                    && DATED_ROLE.matcher(line).find() && line.length() <= 140) {
                section = ResumeProfile.Line.Section.WORK;
            }
            lines.add(new ResumeProfile.Line(line, section, baseStrength(section)));
        }

        return new ResumeProfile(lines,
                experienceParser.parseCandidateYears(resumeText),
                experienceParser.parseYearsFromDates(resumeText));
    }

    /**
     * The strongest evidence a line in this section can support.
     *
     * <p>SKILLS caps at "explicit skill" and never reaches "direct professional
     * evidence" - a list of technologies is a claim about a person, and no amount
     * of phrasing in a skills list turns it into a description of work. That cap
     * is the whole reason Docker in a skills list and "Dockerized microservices"
     * in a role score differently.
     */
    private EvidenceStrength baseStrength(ResumeProfile.Line.Section section) {
        return switch (section) {
            case WORK, PROJECTS -> EvidenceStrength.DIRECT_PROFESSIONAL;
            case SKILLS, SUMMARY -> EvidenceStrength.EXPLICIT_SKILL;
            case EDUCATION, CERTIFICATIONS -> EvidenceStrength.CONTEXTUAL;
            case OTHER -> EvidenceStrength.CONTEXTUAL;
        };
    }

    /**
     * Matches a conventional heading.
     *
     * <p>Requires the line to be short and unpunctuated, so a sentence that merely
     * mentions "experience" ("2.5 years of experience building scalable apps")
     * is not mistaken for the heading and does not swallow the rest of the
     * document into the WORK section - which would silently upgrade every line
     * below it to professional-strength evidence.
     */
    private ResumeProfile.Line.Section matchHeading(String line) {
        if (line.length() > 48) {
            return null;
        }
        String lower = line.toLowerCase(Locale.ROOT).trim();
        if (lower.endsWith(".") || lower.endsWith(",") || lower.contains(";")) {
            return null;
        }
        for (String[] group : HEADINGS) {
            for (String heading : group) {
                if (matchesHeading(lower, heading)) {
                    return sectionFor(heading);
                }
            }
        }
        return null;
    }

    /**
     * "Experience", "Work Experience", "Professional Experience:" - and not
     * "Experience with Kotlin", which is a statement, not a heading.
     */
    private boolean matchesHeading(String lowerLine, String heading) {
        if (!lowerLine.equals(heading) && !lowerLine.equals(heading + ":")) {
            // Allow a small trailing qualifier such as "Experience (3 years)".
            if (!lowerLine.startsWith(heading)) {
                return false;
            }
            String rest = lowerLine.substring(heading.length()).trim();
            return rest.isEmpty()
                    || rest.startsWith(":")
                    || rest.startsWith("(")
                    || rest.startsWith("-");
        }
        return true;
    }

    private ResumeProfile.Line.Section sectionFor(String heading) {
        if (heading.contains("experience") || heading.contains("employment") || heading.contains("career")) {
            return ResumeProfile.Line.Section.WORK;
        }
        if (heading.contains("skill") || heading.contains("technolog") || heading.contains("competenc")
                || heading.contains("toolkit") || heading.contains("expertise") || heading.equals("skills")
                || heading.contains("stack")) {
            return ResumeProfile.Line.Section.SKILLS;
        }
        if (heading.contains("summary") || heading.contains("profile") || heading.contains("objective")
                || heading.contains("about me") || heading.equals("overview")) {
            return ResumeProfile.Line.Section.SUMMARY;
        }
        if (heading.contains("project") || heading.contains("portfolio") || heading.contains("work")) {
            return ResumeProfile.Line.Section.PROJECTS;
        }
        if (heading.contains("education") || heading.contains("academic") || heading.contains("qualification")) {
            return ResumeProfile.Line.Section.EDUCATION;
        }
        if (heading.contains("certif") || heading.contains("license") || heading.contains("training")
                || heading.contains("course")) {
            return ResumeProfile.Line.Section.CERTIFICATIONS;
        }
        return ResumeProfile.Line.Section.OTHER;
    }
}
