package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.DemandLevel;
import com.resumerag.analysis.model.ExperienceRequirement;
import com.resumerag.analysis.model.NormalizedRequirement;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a job description into a list of classified, weighted requirements.
 *
 * <p>The previous extraction returned a flat {@code Set<String>} of skills found
 * by a trie scan, which could not distinguish a must-have from a nice-to-have and
 * could not see a responsibility at all. A "Backend Engineer" JD asking for
 * "Microservices" as a bonus and "Java" as a requirement produced the same two
 * strings, so the two were interchangeable in the score and a candidate missing
 * the core skill lost exactly as much as one missing the bonus.
 *
 * <p>So this reads the JD's structure, not just its vocabulary: which list a line
 * came from, how the line is qualified ("Strong programming skills in Java" vs
 * "Familiarity with Git"), and whether the line is a duty ("Design and integrate
 * RESTful APIs") rather than a capability. A duty is scored separately from a
 * skill, because a candidate can be strong on a technology and still never have
 * done the job.
 *
 * <p>Deterministic and rule-based. A model asked to enumerate requirements
 * invents them, and an invented requirement becomes a fabricated gap on someone's
 * report.
 */
@Service
public class JobDescriptionExtractionService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionExtractionService.class);

    /**
     * Section headers, matched against the whole line or the part before a colon.
     *
     * <p>Order matters: "Preferred Skills" has to be tested before the generic
     * "Skills", or every preferred line is classified as required - which is the
     * single most damaging misclassification available here.
     */
    private static final List<Header> HEADERS = List.of(
            new Header("required qualifications|required skills|required experience|minimum qualifications|"
                    + "minimum requirements|must haves|must-haves|essential (skills|requirements)|"
                    + "required|requirements", JdSection.REQUIRED),
            new Header("preferred qualifications|preferred skills|nice to have|nice-to-have|good to have|"
                    + "desirable|bonus|pluses|it would be great|added advantage|optional", JdSection.PREFERRED),
            new Header("primary responsibilities|key responsibilities|main responsibilities|responsibilities|"
                    + "what you will do|what you'll do|your responsibilities|day to day|day-to-day|"
                    + "duties|job duties|the role", JdSection.RESPONSIBILITY),
            // A candidate profile is a set of duties, not a separate kind of
            // requirement, so it is read as a responsibility section. What matters
            // here is only that the *label* is recognised: an unrecognised label
            // falls through to the line classifier and becomes a requirement in its
            // own right - "What We're Looking For" scored 0 against every resume,
            // was reported as a gap, and was recommended to the candidate as
            // something to go and add. A section heading is not a requirement.
            new Header("what we are looking for|what we're looking for|who we are looking for|"
                    + "who we're looking for|the ideal candidate|ideal candidate|ideal profile|"
                    + "candidate profile|person specification|person spec|about the candidate|"
                    + "who we need", JdSection.RESPONSIBILITY),
            new Header("experience|years of experience|seniority", JdSection.EXPERIENCE),
            new Header("education|educational requirements|qualifications|degree", JdSection.EDUCATION),
            new Header("soft skills|personal skills|qualities|personality|attributes", JdSection.SOFT_SKILL),
            new Header("technical skills|core skills|skills|tech stack|technologies|tools|"
                    + "key competencies|competencies|what you bring", JdSection.REQUIRED),
            new Header("about (the )?(role|company|us|team)|overview|summary|introduction|who you are|"
                    + "the opportunity", JdSection.IGNORED)
    );

    /** Bullet and numbering prefixes to strip before reading a line. */
    private static final Pattern BULLET = Pattern.compile(
            "^\\s*(?:[-*\\u2022\\u25cf\\u25aa\\u00b7\\u2013\\u2014>+]|\\d+[.)]|step\\s+\\d+[.)])\\s*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EDUCATION_TERMS = Pattern.compile(
            "\\b(bachelor|master|b\\.?(sc|tech|s|eng)|m\\.?(sc|tech|s|ca|ba|eng)|ph\\.?d|doctorate|"
                    + "degree|diploma|certification|certified|associate degree|mbbs|mba)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Verbs that make a line a duty rather than a capability. */
    private static final List<String> RESPONSIBILITY_VERBS = List.of(
            "develop", "build", "design", "implement", "maintain", "create", "write", "work with",
            "collaborate", "containerize", "deploy", "debug", "participate", "contribute", "help",
            "manage", "lead", "deliver", "support", "own", "drive", "plan", "review", "mentor",
            "analyze", "analyse", "optimize", "optimise", "improve", "integrate", "configure",
            "communicate", "partner", "ensure", "translate", "identify", "monitor", "troubleshoot"
    );

    /** Strong qualifiers promote a required skill to CRITICAL. */
    private static final List<String> CRITICAL_QUALIFIERS = List.of(
            "strong", "expert", "expertise", "deep", "extensive", "advanced", "proven", "solid",
            "must", "excellent", "proficient", "mastery", "demonstrated", "exceptional", "outstanding"
    );

    /** Lead-indicators promote a responsibility to HIGH. */
    private static final List<String> LEAD_VERBS = List.of(
            "lead", "own", "drive", "architect", "mentor", "set", "establish", "head"
    );

    private static final List<String> ADVANCED_WORDS = List.of(
            "advanced", "expert", "expertise", "deep", "extensive", "mastery", "expert-level",
            "expert level", "in-depth", "in depth", "highly proficient", "strong grasp", "aspiring"
    );

    private static final List<String> BASIC_WORDS = List.of(
            "basic", "familiarity", "familiar with", "basic knowledge", "basic understanding",
            "knowledge of", "knowledge in", "understanding of", "understanding in",
            "exposure to", "awareness of", "nice to have", "desirable", "a plus", "plus",
            "workings of", "concept of", "interest in", "some exposure", "basic experience"
    );

    private final RequirementNormalizationService normalizationService;
    private final ExperienceRequirementParser experienceParser;

    public JobDescriptionExtractionService(RequirementNormalizationService normalizationService,
                                           ExperienceRequirementParser experienceParser) {
        this.normalizationService = normalizationService;
        this.experienceParser = experienceParser;
    }

    /**
     * Extracts every requirement the job description states.
     *
     * @return the profile, or one with no requirements when the text states
     *         none. Never {@code null}, and never containing a requirement the
     *         job description did not state.
     */
    public JobDescriptionProfile extract(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            return new JobDescriptionProfile(List.of(), null);
        }

        JdSection section = JdSection.IGNORED;
        Map<String, Requirement> byDedupeKey = new LinkedHashMap<>();
        List<ExperienceRequirement> experienceStatements = new ArrayList<>();

        for (String rawLine : jdText.split("\\R")) {
            String line = BULLET.matcher(rawLine).replaceAll("").trim();
            if (line.isEmpty()) {
                continue;
            }

            HeaderMatch header = matchHeader(line);
            if (header != null) {
                section = header.section();
                String remainder = header.remainder();
                if (remainder.isBlank()) {
                    continue;
                }
                line = remainder;
            }

            // An experience band is a range over a number, not a matchable phrase,
            // and the same band is typically written twice - once in the header,
            // once in the skills list. Collected separately and emitted once, so a
            // single number cannot weigh twice and cannot be reported as two gaps.
            if (section == JdSection.EXPERIENCE || looksLikeExperienceConstraint(line)) {
                ExperienceRequirement stated = experienceParser.parsePrimary(line);
                if (stated != null) {
                    experienceStatements.add(stated);
                    continue;
                }
            }

            Requirement requirement = toRequirement(line, section);
            if (requirement == null) {
                continue;
            }
            // Deduplicate on the normalised identity, keeping the stronger
            // statement. "REST API development" and "Experience developing REST
            // APIs" are one requirement; a JD that says both must not be able to
            // make REST weigh twice.
            String key = section.name() + '|' + normalizationService.dedupeKey(requirement.normalization());
            Requirement existing = byDedupeKey.get(key);
            byDedupeKey.put(key, existing == null ? requirement : stronger(existing, requirement));
        }

        List<Requirement> requirements = new ArrayList<>();
        int index = 0;
        for (Requirement requirement : byDedupeKey.values()) {
            requirements.add(withIndex(requirement, index++));
        }

        // Prefer a band stated in the job description's own words; fall back to
        // anything the parser recovered from the body.
        ExperienceRequirement experience = experienceStatements.isEmpty()
                ? experienceParser.parsePrimary(jdText)
                : experienceParser.parsePrimary(experienceStatements.stream()
                        .map(ExperienceRequirement::sourceText)
                        .reduce((a, b) -> a + " " + b)
                        .orElse(null));
        if (experience == null) {
            experience = experienceParser.parsePrimary(jdText);
        }
        if (experience != null) {
            requirements.add(experienceRequirement(index, experience));
        }

        logRequirements(requirements);
        return new JobDescriptionProfile(requirements, experience);
    }

    // ---------------------------------------------------------------------
    // Line classification
    // ---------------------------------------------------------------------

    private Requirement toRequirement(String line, JdSection section) {
        if (line.length() > MAX_LINE_CHARS) {
            // A paragraph is prose, not a requirement. Truncating it would invent
            // a requirement nobody wrote.
            return null;
        }

        // An experience constraint is a range, not a phrase, and is scored on its
        // own axis. The caller handles those separately so the same band written
        // twice stays one requirement.
        if (section == JdSection.EDUCATION || EDUCATION_TERMS.matcher(line).find()) {
            return requirementFor(line, RequirementCategory.EDUCATION, RequirementImportance.MEDIUM,
                    DemandLevel.BASIC, JdSection.EDUCATION);
        }

        if (section == JdSection.RESPONSIBILITY) {
            return requirementFor(line, RequirementCategory.RESPONSIBILITY, responsibilityImportance(line),
                    demandLevel(line), JdSection.RESPONSIBILITY);
        }

        if (section == JdSection.PREFERRED) {
            return requirementFor(line, RequirementCategory.PREFERRED_SKILL, RequirementImportance.LOW,
                    demandLevel(line), JdSection.PREFERRED);
        }

        if (section == JdSection.SOFT_SKILL) {
            return requirementFor(line, RequirementCategory.SOFT_SKILL, RequirementImportance.MEDIUM,
                    demandLevel(line), JdSection.SOFT_SKILL);
        }

        if (section == JdSection.REQUIRED) {
            return requirementFor(line, RequirementCategory.REQUIRED_SKILL, requiredImportance(line),
                    demandLevel(line), JdSection.REQUIRED);
        }

        // Outside any recognised section. A leading verb means the line is a duty;
        // anything else has to name something the vocabulary recognises before it
        // is allowed to become a requirement. Without that bar a job title line -
        // "Full-Stack Software Developer" - becomes a required skill and shows up
        // as a gap in front of the candidate.
        if (startsWithResponsibilityVerb(line)) {
            return requirementFor(line, RequirementCategory.RESPONSIBILITY, responsibilityImportance(line),
                    demandLevel(line), JdSection.RESPONSIBILITY);
        }
        return requirementFor(line, RequirementCategory.REQUIRED_SKILL, requiredImportance(line),
                demandLevel(line), JdSection.IGNORED, true);
    }

    private Requirement requirementFor(String line,
                                        RequirementCategory category,
                                        RequirementImportance importance,
                                        DemandLevel demand,
                                        JdSection section) {
        return requirementFor(line, category, importance, demand, section, false);
    }

    /**
     * @param knownTermsOnly when true, a line that normalises only to a bare
     *        phrase is rejected. Used outside any labelled section, where a
     *        recognisable technology is the only evidence that a line is a
     *        requirement rather than prose.
     */
    private Requirement requirementFor(String line,
                                        RequirementCategory category,
                                        RequirementImportance importance,
                                        DemandLevel demand,
                                        JdSection section,
                                        boolean knownTermsOnly) {
        NormalizedRequirement normalized = normalizationService.normalize(line);
        if (normalized == null) {
            return null;
        }
        if (knownTermsOnly && normalized.terms().stream().anyMatch(t -> isPhraseTerm(t.canonicalKey()))) {
            return null;
        }
        List<String> synonyms = new ArrayList<>(normalized.terms().stream()
                .flatMap(t -> t.patterns().stream())
                .filter(p -> !p.isBlank())
                .distinct()
                .toList());
        synonyms.addAll(normalized.terms().stream().map(t -> t.displayName()).toList());

        return new Requirement(
                0,
                normalized.displayName(),
                category,
                importance,
                demand,
                normalized.canonicalKey(),
                synonyms,
                line,
                true,
                normalized
        );
    }

    /** True for terms the normalizer had to keep as a phrase, with no equivalence. */
    private boolean isPhraseTerm(String canonicalKey) {
        return canonicalKey != null && canonicalKey.startsWith("PHRASE_");
    }

    private Requirement experienceRequirement(int index, ExperienceRequirement experience) {
        String name = experience.describe();
        NormalizedRequirement normalized = NormalizedRequirement.of(
                "EXPERIENCE_" + experience.key(), name,
                new com.resumerag.analysis.model.NormalizedTerm(
                        "EXPERIENCE_" + experience.key(), name,
                        List.of(experience.describe())));
        return new Requirement(
                index,
                name,
                RequirementCategory.EXPERIENCE,
                RequirementImportance.HIGH,
                DemandLevel.PROFESSIONAL,
                normalized.canonicalKey(),
                List.of(name),
                experience.sourceText(),
                true,
                normalized
        );
    }

    private Requirement withIndex(Requirement requirement, int index) {
        return new Requirement(index, requirement.name(), requirement.category(), requirement.importance(),
                requirement.demand(), requirement.normalizedName(), requirement.synonyms(), requirement.sourceText(),
                requirement.explicitRequirement(), requirement.normalization());
    }

    /** Keeps whichever statement of a duplicated requirement asks for more. */
    private Requirement stronger(Requirement a, Requirement b) {
        if (a.demand().ordinal() != b.demand().ordinal()) {
            return a.demand().ordinal() > b.demand().ordinal() ? a : b;
        }
        if (a.importance().ordinal() != b.importance().ordinal()) {
            return a.importance().ordinal() > b.importance().ordinal() ? a : b;
        }
        return a;
    }

    // ---------------------------------------------------------------------
    // Signals
    // ---------------------------------------------------------------------

    /**
     * How much the job description demands, read off its own wording.
     *
     * <p>"Basic" and "familiarity" are tested before the default, because
     * "Basic experience with AWS" also contains "experience" - and defaulting
     * that to professional demand is what makes a listed skill look like
     * sufficient for an "Advanced AWS deployment" requirement.
     */
    private DemandLevel demandLevel(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String word : ADVANCED_WORDS) {
            if (lower.contains(word)) {
                return DemandLevel.ADVANCED;
            }
        }
        for (String word : BASIC_WORDS) {
            if (lower.contains(word)) {
                return DemandLevel.BASIC;
            }
        }
        return DemandLevel.PROFESSIONAL;
    }

    /**
     * "Strong programming skills in Java" and "Familiarity with Git" are both
     * required, but they are not equally load-bearing. Averaging them as equals
     * understates what losing the first one costs.
     */
    private RequirementImportance requiredImportance(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String qualifier : CRITICAL_QUALIFIERS) {
            if (lower.contains(qualifier)) {
                return RequirementImportance.CRITICAL;
            }
        }
        return RequirementImportance.HIGH;
    }

    private RequirementImportance responsibilityImportance(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String verb : LEAD_VERBS) {
            if (lower.contains(verb)) {
                return RequirementImportance.HIGH;
            }
        }
        return RequirementImportance.MEDIUM;
    }

    private boolean startsWithResponsibilityVerb(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String verb : RESPONSIBILITY_VERBS) {
            if (lower.startsWith(verb + " ") || lower.startsWith(verb + ",")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a line is a years-of-experience constraint wherever it appears.
     *
     * <p>Job descriptions state the band in the skills list as often as in the
     * header, and both are the same requirement - so this is checked outside the
     * section too. The "years" test is what keeps "Experience with Spring Boot"
     * from being parsed as an experience requirement.
     */
    private boolean looksLikeExperienceConstraint(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return (lower.contains("year") || lower.contains("yr"))
                && !experienceParser.parseAll(line).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Headers
    // ---------------------------------------------------------------------

    /**
     * Matches a section header, returning any text that followed the colon.
     *
     * <p>"Experience: 1-3 years" is both a header and a requirement, so the
     * trailing content is handed back to be classified as a line in the new
     * section. Dropping it would lose the single most load-bearing number in
     * most job descriptions.
     */
    private HeaderMatch matchHeader(String line) {
        String candidate = line;
        String remainder = "";

        int colon = line.indexOf(':');
        if (colon > 0) {
            candidate = line.substring(0, colon).trim();
            remainder = colon + 1 < line.length()
                    ? BULLET.matcher(line.substring(colon + 1)).replaceAll("").trim()
                    : "";
        }

        // Only a short line can be a header. Without this, a responsibility
        // containing the word "requirements" would flip the whole rest of the
        // document into the wrong section.
        if (candidate.isEmpty() || candidate.length() > MAX_HEADER_CHARS) {
            return null;
        }
        for (Header header : HEADERS) {
            if (header.matches(candidate)) {
                return new HeaderMatch(header.section(), remainder);
            }
        }
        return null;
    }

    private void logRequirements(List<Requirement> requirements) {
        if (!log.isDebugEnabled()) {
            return;
        }
        long required = requirements.stream().filter(r -> r.category() == RequirementCategory.REQUIRED_SKILL).count();
        long preferred = requirements.stream().filter(r -> r.category() == RequirementCategory.PREFERRED_SKILL).count();
        long responsibilities = requirements.stream().filter(r -> r.category() == RequirementCategory.RESPONSIBILITY).count();
        log.debug("JD requirements extracted: {} (required: {}, preferred: {}, responsibilities: {}, "
                        + "experience: {}, education: {})",
                requirements.size(), required, preferred, responsibilities,
                requirements.stream().filter(r -> r.category() == RequirementCategory.EXPERIENCE).count(),
                requirements.stream().filter(r -> r.category() == RequirementCategory.EDUCATION).count());
    }

    private static final int MAX_LINE_CHARS = 300;
    private static final int MAX_HEADER_CHARS = 60;

    /** Which part of a job description a line was written in. */
    private enum JdSection {
        REQUIRED, PREFERRED, RESPONSIBILITY, EXPERIENCE, EDUCATION, SOFT_SKILL, IGNORED
    }

    private record Header(String regex, JdSection section) {
        boolean matches(String candidate) {
            Matcher matcher = patternFor(regex).matcher(candidate);
            if (!matcher.matches()) {
                return false;
            }
            // Guard against a bare word that is also a common requirement word:
            // "Experience" is a header, "Experience with Docker" is not.
            return !(regex.contains("experience") && containsWord(candidate, "experience with"));
        }

        private static Pattern patternFor(String regex) {
            return HEADER_PATTERNS.computeIfAbsent(regex,
                    r -> Pattern.compile("^\\s*(?:the\\s+|a\\s+|an\\s+)?" + r + "\\s*$", Pattern.CASE_INSENSITIVE));
        }
    }

    private static final Map<String, Pattern> HEADER_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean containsWord(String text, String word) {
        return SynonymRegistry.normalizePhrase(text).contains(SynonymRegistry.normalizePhrase(word));
    }

    private record HeaderMatch(JdSection section, String remainder) {}
}
