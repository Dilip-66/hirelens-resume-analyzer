package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.model.NormalizedRequirement;
import com.resumerag.analysis.model.NormalizedTerm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reduces a job description line to canonical terms plus the logic that combines
 * them.
 *
 * <p>Two failure modes this exists to prevent, both of which show a user a wrong
 * answer about their own resume:
 *
 * <ul>
 *   <li><b>Duplication.</b> "REST APIs" and "RESTful APIs" in one JD are one
 *       requirement. Counted separately, a candidate with REST experience is
 *       reported as half-matching a section they fully satisfy - and
 *       "JavaScript and/or TypeScript" becomes two mandatory languages, one of
 *       which the employer explicitly said was optional.</li>
 *   <li><b>Wording drift.</b> "Postgres" on a resume has to satisfy
 *       "PostgreSQL" in the job description, or the engine reports a false gap
 *       against a candidate who plainly has it.</li>
 * </ul>
 *
 * <p>A requirement whose terms are not in the vocabulary is kept as a phrase and
 * matched on that phrase. The vocabulary widens what counts as equivalent; it is
 * not the only way a requirement can be satisfied, so nothing is dropped merely
 * for being unrecognised.
 *
 * <p>Deterministic and free of Spring state, so the whole extraction layer is
 * testable without a context.
 */
@Service
public class RequirementNormalizationService {

    /**
     * How a job description talks about a requirement, rather than about the
     * technology. Stripped first so "Experience with Spring Boot" reduces to the
     * same term as "Spring Boot" - otherwise the qualifier ends up inside the
     * term and no canonical lookup can ever hit.
     */
    private static final List<String> QUALIFIER_PREFIXES = List.of(
            "strong programming skills in", "strong programming skills",
            "strong knowledge of", "strong understanding of", "strong experience with",
            "strong professional experience in", "strong hands-on experience with",
            "strong grasp of", "good grasp of", "solid grasp of",
            "solid understanding of", "solid knowledge of", "solid experience with",
            "expert knowledge of", "expert level knowledge of", "expert level in", "expert in",
            "expertise in", "deep expertise in", "deep knowledge of", "deep understanding of",
            "extensive experience in", "extensive knowledge of", "extensive experience with",
            "advanced knowledge of", "advanced experience with", "advanced proficiency in",
            "proficiency in", "proficient in", "working knowledge of",
            "hands-on experience with", "hands-on experience in", "hands-on",
            "practical experience with", "practical experience in",
            "professional experience in", "demonstrated experience in", "proven experience in",
            "basic experience with", "basic experience in", "basic knowledge of", "basic understanding of",
            "experience in", "experience with", "experience developing", "experience building",
            "experience designing", "experience working with", "experience using",
            "familiarity with", "familiarity in", "familiar with",
            "knowledge of", "knowledge in", "understanding of", "understanding in",
            "exposure to", "awareness of", "ability to", "capability in",
            "must have", "must-have",
            "participate in", "participating in", "taking part in",
            "collaborate using", "collaborating using",
            "work with", "working with", "contribute to", "contributing to",
            "build", "building", "design", "designing", "develop", "developing",
            "implement", "implementing", "maintain", "maintaining", "write", "writing",
            "create", "creating", "apply", "applying", "manage", "managing",
            "use", "uses", "using", "have", "has",
            "skills", "skill", "tools", "tool", "technologies", "technology", "stack",
            // Bare intensity qualifiers. "Advanced Kubernetes" has to reduce to
            // "Kubernetes" so the requirement is named after the capability, and so
            // the demand is read from the qualifier instead of being swallowed into
            // the name.
            "highly proficient", "highly", "expert-level", "expert level",
            "advanced", "intermediate", "expert", "strong", "basic", "proficient",
            "excellent", "demonstrated", "proven", "solid", "extensive", "deep", "well"
    );

    /**
     * Connectors that mean "one of these is enough".
     *
     * <p>Checked before the bare conjunctions, because splitting on "and" first
     * would turn "JavaScript and/or TypeScript" into a requirement for both
     * languages - the exact over-claiming the compound handling exists to avoid.
     *
     * <p>A comma is not here. "React, JavaScript, and TypeScript" is a list, and
     * in that construction the technologies are jointly required; only an
     * explicit disjunction word or slash makes a requirement optional.
     */
    private static final List<String> OR_MARKERS = List.of(" and/or ", " and / or ", " or ", " / ", " & ", "/");

    /**
     * Leading conjunctions left behind once a leading verb is stripped.
     *
     * <p>"Develop and maintain the content strategy" has its verb removed, and
     * without this the requirement would be displayed to the user as "and
     * maintain the content strategy" - which reads as a parsing bug on their own
     * report even though the matching underneath it is correct.
     */
    private static final List<String> LEADING_CONJUNCTIONS = List.of(
            "and ", "or ", "with ", "plus ", "as well as ", "to ");

    /**
     * Every split point, in priority order.
     *
     * <p>Disjunction markers come first so "and/or" is recognised before "and"
     * gets a chance to split it.
     */
    private static final List<String> SPLIT_MARKERS = List.of(
            " and/or ", " and / or ", " or ", " / ", " & ", " and ", ",", " + ", " with ",
            // Unspaced slash last, and only when both sides are real requirements.
            // "AI/ML" is a disjunction; "CI/CD" is not, because neither "CI" nor
            // "CD" is a requirement, so it stays a single term.
            "/");

    /** Guards against a pathological connector chain turning into dozens of terms. */
    private static final int MAX_SPLIT_DEPTH = 3;


    /**
     * Words that are never a requirement on their own.
     *
     * <p>"and" and "or" appear here as well as in the marker lists: a fragment
     * must be able to survive on its own for a split to be legitimate.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "skills", "skill", "experience", "experiences", "knowledge", "understanding",
            "ability", "abilities", "familiarity", "exposure", "awareness", "expertise",
            "proficiency", "working", "hands", "on", "solid", "strong", "good", "basic",
            "professional", "practical", "demonstrated", "proven", "deep", "extensive",
            "advanced", "expert", "level", "must", "have", "has", "the", "a", "an",
            "and", "or", "of", "in", "with", "to", "for", "using", "use", "used", "uses",
            "at", "is", "are", "will", "we", "you", "our", "their", "them", "this",
            "that", "it", "other", "others", "plus", "familiar", "familiarize",
            "environment", "environments", "tool", "tools", "technologies", "technology",
            "stack", "programming", "program", "development", "developing", "developer",
            "excellent", "great", "team", "teams", "including", "include", "includes",
            "such", "like", "etc", "new", "role", "candidate", "candidates"
    );

    public RequirementNormalizationService() {
    }

    /**
     * Normalises one requirement line.
     *
     * @return the normalised requirement, or {@code null} when the line contains
     *         nothing that can be treated as a requirement. Null is the correct
     *         answer for a JD preamble such as "Full-Stack Software Developer" or
     *         "About the role" - inventing a requirement there would put a
     *         fabricated gap in front of a user.
     */
    public NormalizedRequirement normalize(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }
        String cleaned = stripQualifiers(sourceText);
        if (cleaned.isBlank()) {
            return null;
        }

        List<String> fragments = splitFragments(cleaned);
        if (fragments.isEmpty()) {
            return null;
        }

        List<NormalizedTerm> terms = new ArrayList<>();
        for (String fragment : fragments) {
            for (NormalizedTerm term : toTerms(fragment)) {
                boolean duplicate = terms.stream()
                        .anyMatch(t -> t.canonicalKey().equals(term.canonicalKey()));
                if (!duplicate) {
                    terms.add(term);
                }
            }
        }
        if (terms.isEmpty()) {
            return null;
        }
        if (terms.size() == 1) {
            NormalizedTerm only = terms.get(0);
            return NormalizedRequirement.of(only.canonicalKey(), only.displayName(), only);
        }

        CompoundOperator operator = fragments.size() > 1 ? detectedOperator(cleaned) : CompoundOperator.AND;
        return new NormalizedRequirement(canonicalKeyFor(terms, operator), displayNameFor(terms, operator), operator, terms);
    }

    /**
     * The key two requirements are deduplicated by.
     *
     * <p>Order-independent, so "AWS or cloud platforms" and "cloud platforms or
     * AWS" collapse to one. The same requirement stated twice must never read as
     * two separate gaps.
     */
    public String dedupeKey(NormalizedRequirement normalized) {
        if (normalized == null) {
            return null;
        }
        List<String> keys = normalized.terms().stream()
                .map(NormalizedTerm::canonicalKey)
                .sorted()
                .toList();
        return normalized.operator() + "|" + String.join("|", keys);
    }

    /** Strips the JD's "strong experience with" framing, keeping the technology. */
    public String stripQualifiers(String text) {
        if (text == null) {
            return "";
        }
        String working = text.trim();
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            String lower = working.toLowerCase(Locale.ROOT);
            for (String prefix : QUALIFIER_PREFIXES) {
                if (lower.startsWith(prefix + " ")) {
                    working = working.substring(prefix.length() + 1).trim();
                    stripped = true;
                    break;
                }
            }
        }
        return working.replaceAll("\\s+", " ").trim();
    }

    /**
     * The content words of a fragment: stop words and JD framing removed, so
     * "Strong programming skills in Java" leaves "java" and
     * "Basic experience with AWS or cloud platforms" leaves "aws cloud platforms".
     */
    public String significantContent(String text) {
        if (text == null) {
            return "";
        }
        String normalized = SynonymRegistry.normalizePhrase(text);
        if (normalized.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (String word : normalized.split(" ")) {
            if (word.isBlank() || STOP_WORDS.contains(word) || !seen.add(word)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Compound splitting
    // ---------------------------------------------------------------------

    /**
     * Splits a compound requirement into its parts, keeping the operator.
     *
     * <p>Recursive, because requirements nest: "Build responsive web interfaces
     * using React, JavaScript, and TypeScript" is four terms, not two.
     *
     * <p>A split is only accepted when both sides are genuinely requirements. A
     * connector that is part of a phrase rather than between two requirements
     * would otherwise shred the meaning: "CI/CD pipelines" is one requirement,
     * and "Debug issues and improve application performance" is one
     * responsibility, not two.
     */
    private List<String> splitFragments(String cleaned) {
        return splitRecursively(cleaned, 0);
    }

    private List<String> splitRecursively(String text, int depth) {
        if (depth >= MAX_SPLIT_DEPTH || text == null || text.isBlank()) {
            return List.of(text == null ? "" : text.trim());
        }
        String padded = " " + text + " ";
        for (String marker : SPLIT_MARKERS) {
            int from = 0;
            int index;
            while ((index = padded.indexOf(marker, from)) >= 0) {
                from = index + marker.length();
                String left = padded.substring(0, index);
                String right = padded.substring(from);
                if (left.indexOf("and/or") >= 0 || !isRequirementLike(left) || !isRequirementLike(right)) {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                parts.addAll(splitRecursively(left.trim(), depth + 1));
                parts.addAll(splitRecursively(right.trim(), depth + 1));
                return parts;
            }
        }
        return List.of(text.trim());
    }

    /**
     * A fragment counts as a requirement only if it is either a known term or
     * carries at least two content words. "Design" alone is a verb, not a
     * requirement; "Design and integrate RESTful APIs" is. Requiring the stronger
     * of the two is what keeps leading verbs from becoming phantom gaps.
     */
    private boolean isRequirementLike(String fragment) {
        if (fragment == null) {
            return false;
        }
        String content = significantContent(fragment);
        if (content.isBlank()) {
            return false;
        }
        if (content.contains(" ")) {
            return true;
        }
        return SynonymRegistry.lookup(content).isPresent();
    }

    private CompoundOperator detectedOperator(String cleaned) {
        String padded = " " + cleaned.toLowerCase(Locale.ROOT) + " ";
        if (padded.contains(" and/or ") || padded.contains(" and / or ")) {
            return CompoundOperator.AND_OR;
        }
        for (String marker : OR_MARKERS) {
            if (padded.contains(marker)) {
                return CompoundOperator.OR;
            }
        }
        return CompoundOperator.AND;
    }

    // ---------------------------------------------------------------------
    // Term mapping
    // ---------------------------------------------------------------------

    /**
     * Maps a fragment onto every canonical term it names.
     *
     * <p>Usually one. More than one when a line names several technologies
     * ("Backend services using Java and Spring Boot") or when a category and a
     * specific member both appear.
     */
    /**
     * Maps a fragment onto every canonical term it names.
     *
     * <p>Usually one. More than one when a line names several technologies
     * ("Backend services using Java and Spring Boot") or when a category and a
     * specific member both appear.
     *
     * <p>Requirement-side resolution, which is stricter than the evidence side on
     * purpose: "AWS EC2 / S3" asks for EC2 and S3, not also for bare AWS.
     *
     * <p><b>The full phrase is resolved against the vocabulary before any
     * qualifier is stripped.</b> That ordering is the fix for a defect the
     * benchmark caught: with stripping first, "Deep Learning" lost "deep" to the
     * intensity-qualifier list and normalised to the phrase "Learning". A word can
     * be both a qualifier and part of a product name, so the vocabulary gets
     * first refusal.
     */
    private List<NormalizedTerm> toTerms(String fragment) {
        String whole = fragment == null ? "" : fragment.trim();
        if (whole.isEmpty()) {
            return List.of();
        }

        List<NormalizedTerm> fromVocabulary = termsFromVocabulary(whole);
        if (!fromVocabulary.isEmpty()) {
            return fromVocabulary;
        }

        String cleaned = tidy(stripQualifiers(whole));
        if (cleaned.isBlank()) {
            return List.of();
        }
        List<NormalizedTerm> afterStripping = termsFromVocabulary(cleaned);
        if (!afterStripping.isEmpty()) {
            return afterStripping;
        }

        String content = significantContent(cleaned);
        if (content.isBlank()) {
            return List.of();
        }

        // Unrecognised requirement: keep the phrase. It is still a real
        // requirement, it just does not gain cross-wording equivalence, and it is
        // matched on its own words.
        //
        // The display name keeps the job description's own casing. Lowercasing it
        // would put "kubernetes" in front of a user where the job description said
        // "Kubernetes", which reads as a bug in the report even though the match
        // underneath it is right.
        String display = cleaned.replaceAll("[.;:,]+$", "").trim();
        if (display.isEmpty()) {
            display = content;
        }
        String canonicalKey = "PHRASE_" + SynonymRegistry.normalizePhrase(content).toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        return List.of(new NormalizedTerm(canonicalKey, display, List.of(display)));
    }

    /**
     * The canonical terms a fragment names, or empty when it names none.
     *
     * <p>A category is dropped when a specific technology appears alongside it, so
     * "Backend services using Java" is a requirement about Java rather than two
     * requirements. Specificity does real work here: "Frontend development using
     * React" contains both FRONTEND and REACT, and ranking on match length alone
     * would reduce a requirement about React to a claim that the candidate is a
     * frontend developer at all.
     */
    private List<NormalizedTerm> termsFromVocabulary(String phrase) {
        List<String> keys = SynonymRegistry.canonicalKeysForRequirement(phrase);
        if (keys.isEmpty()) {
            return List.of();
        }
        boolean hasSpecific = keys.stream().anyMatch(k -> !SynonymRegistry.isGeneric(k));
        List<String> effective = hasSpecific
                ? keys.stream().filter(k -> !SynonymRegistry.isGeneric(k)).toList()
                : keys;

        List<NormalizedTerm> terms = new ArrayList<>();
        for (String key : effective) {
            if (terms.stream().noneMatch(t -> t.canonicalKey().equals(key))) {
                terms.add(new NormalizedTerm(key, SynonymRegistry.displayName(key),
                        SynonymRegistry.surfacesFor(key)));
            }
        }
        return terms;
    }

    /**
     * Trims a fragment after a qualifier has been removed: leading conjunctions
     * and trailing punctuation.
     */
    private String tidy(String text) {
        String working = text == null ? "" : text.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String lower = working.toLowerCase(Locale.ROOT);
            for (String conjunction : LEADING_CONJUNCTIONS) {
                if (lower.startsWith(conjunction)) {
                    working = working.substring(conjunction.length()).trim();
                    changed = true;
                    break;
                }
            }
            working = working.replaceFirst("^[\\s,;:-]+", "").replaceAll("[\\s,;:-]+$", "").trim();
        }
        return working;
    }

    private NormalizedTerm termFor(SynonymRegistry.NormalizedEntry entry) {
        return new NormalizedTerm(entry.canonicalKey(), entry.displayName(), entry.surfaces());
    }

    private String canonicalKeyFor(List<NormalizedTerm> terms, CompoundOperator operator) {
        String prefix = switch (operator) {
            case OR, AND_OR -> "ANY_OF_";
            case AND -> "ALL_OF_";
            case NONE -> "TERM_";
        };
        String joined = terms.stream().map(NormalizedTerm::canonicalKey)
                .sorted()
                .reduce((a, b) -> a + "_" + b)
                .orElse("UNKNOWN");
        return prefix + joined;
    }

    private String displayNameFor(List<NormalizedTerm> terms, CompoundOperator operator) {
        String joiner = operator == CompoundOperator.AND ? " + " : " or ";
        String joined = terms.stream().map(NormalizedTerm::displayName)
                .reduce((a, b) -> a + joiner + b)
                .orElse("");
        return joined.isBlank() ? terms.get(0).displayName() : joined;
    }

    /** A canonical term found in a fragment, with the surface form that matched. */
    private record CanonicalHit(String key, String matchedText) {}
}
