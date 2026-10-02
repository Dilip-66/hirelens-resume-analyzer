package com.resumerag.analysis.assess;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import com.resumerag.analysis.evidence.ResumeProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A conservative, text-only assessment of how well a resume is likely to be read
 * by an applicant tracking system.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>It does not claim anything about how a document <em>looks</em>. The backend
 * receives extracted text, not a rendering, so a font size, a column layout, a
 * logo, a chart or a text box are all invisible here - and an assessment that
 * asserted "the font is too small" or "the two-column layout may confuse parsers"
 * would be inventing a finding it cannot support. Every factor below is something
 * measurable from the text, and each one reports what it measured so a reader can
 * disagree with it.
 *
 * <p>That constraint is the reason the output is a list of named factors with
 * evidence rather than a single opaque number. A score nobody can interrogate is
 * exactly what a resume tool should not produce.
 */
@Service
public class AtsQualityService {

    private final ScoringConfiguration configuration;

    public AtsQualityService(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    /** The outcome for one check. */
    public record Factor(
            String factor,
            String status,
            double weight,
            String evidence
    ) {
        public boolean isPass() {
            return "PASS".equals(status);
        }

        public boolean isFail() {
            return "FAIL".equals(status);
        }
    }

    /**
     * The whole assessment.
     *
     * @param factors  every check, including the ones that passed
     * @param score    the weighted mean, or {@code null} when there is no text
     * @param assessed false when the resume has too little text to say anything
     */
    public record Assessment(
            boolean assessed,
            Double score,
            List<Factor> factors,
            String note
    ) {
        public Assessment {
            factors = factors == null ? List.of() : List.copyOf(factors);
        }

        public static Assessment notAssessed(String note) {
            return new Assessment(false, null, List.of(), note);
        }
    }

    /** Standard sections an ATS expects to find. */
    private static final Map<String, List<String>> SECTION_CUES = new LinkedHashMap<>();

    static {
        SECTION_CUES.put("summary", List.of("summary", "profile", "objective", "about"));
        SECTION_CUES.put("skills", List.of("skills", "technical skills", "core skills", "competencies", "technologies"));
        SECTION_CUES.put("experience", List.of("experience", "employment", "work history", "career"));
        SECTION_CUES.put("education", List.of("education", "academic", "qualifications"));
    }

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+?\\d{1,3}[\\s.-]?)?(?:\\(?\\d{3,5}\\)?[\\s.-]?)\\d{3,4}[\\s.-]?\\d{3,4}");

    /** A word that is nothing but a long unbroken run: a sign of mangled extraction. */
    private static final Pattern MANGLED = Pattern.compile("\\S{45,}");

    /** Fewer than this many words is too little text to assess. */
    private static final int MIN_WORDS = 40;

    /**
     * Runs every check.
     *
     * @param resumeText the full extracted text
     * @param profile    the section-tagged view of the same text
     * @param requirements the job description's requirements, for keyword coverage
     */
    public Assessment assess(String resumeText, ResumeProfile profile, List<Requirement> requirements) {
        if (resumeText == null || resumeText.isBlank()) {
            return Assessment.notAssessed(
                    "No text could be extracted from this resume, so there is nothing to assess.");
        }
        int wordCount = wordCount(resumeText);
        if (wordCount < MIN_WORDS) {
            return Assessment.notAssessed(
                    "This resume contains only " + wordCount + " words, which is too little text to assess "
                            + "reliably. A short resume is not a badly structured one.");
        }

        List<Factor> factors = new ArrayList<>();
        factors.add(sectionCompleteness(profile));
        factors.add(contactDetails(resumeText));
        factors.add(textExtractability(resumeText, wordCount));
        factors.add(keywordCoverage(profile, requirements));
        factors.add(duplication(resumeText));
        factors.add(keywordStuffing(resumeText, profile));
        factors.add(readingStructure(resumeText));

        double earned = 0;
        double possible = 0;
        for (Factor factor : factors) {
            possible += factor.weight();
            if (factor.isPass()) {
                earned += factor.weight();
            } else if (!factor.isFail()) {
                earned += factor.weight() * 0.5;
            }
        }
        double score = possible <= 0 ? 0 : earned / possible * 100;
        return new Assessment(true, round(score), factors,
                "Measured from the extracted text only. Layout, fonts, columns and images are not assessed, "
                        + "because the backend never sees them.");
    }

    // ---------------------------------------------------------------------
    // Factors
    // ---------------------------------------------------------------------

    /**
     * Whether the conventional sections are present and findable.
     *
     * <p>Measured from the profile's own section detection rather than by looking
     * for the words, so a document that says "Professional Experience" and one that
     * says "Work History" both count.
     */
    private Factor sectionCompleteness(ResumeProfile profile) {
        Set<ResumeProfile.Line.Section> present = new LinkedHashSet<>();
        profile.lines().forEach(line -> present.add(line.section()));

        List<String> missing = new ArrayList<>();
        if (present.stream().noneMatch(s -> s == ResumeProfile.Line.Section.SUMMARY)) {
            missing.add("summary");
        }
        if (present.stream().noneMatch(s -> s == ResumeProfile.Line.Section.SKILLS)) {
            missing.add("skills");
        }
        if (present.stream().noneMatch(s -> s == ResumeProfile.Line.Section.WORK)) {
            missing.add("experience");
        }
        if (present.stream().noneMatch(s -> s == ResumeProfile.Line.Section.EDUCATION)) {
            missing.add("education");
        }

        if (missing.isEmpty()) {
            return new Factor("SECTION_COMPLETENESS", "PASS", configuration.getAtsSectionWeight(),
                    "Resume contains summary, skills, experience and education sections");
        }
        return new Factor("SECTION_COMPLETENESS", missing.size() > 2 ? "FAIL" : "WARN",
                configuration.getAtsSectionWeight(),
                "No identifiable section for: " + String.join(", ", missing)
                        + ". Sections an applicant tracking system looks for are the ones named.");
    }

    /**
     * Whether contact details are present in the text.
     *
     * <p>A real and cheap ATS failure: a resume that only carries contact details
     * in a header image extracts to text with no way to contact anyone.
     */
    private Factor contactDetails(String text) {
        boolean email = EMAIL.matcher(text).find();
        boolean phone = PHONE.matcher(text).find();
        if (email && phone) {
            return new Factor("CONTACT_DETAILS", "PASS", configuration.getAtsContactWeight(),
                    "Resume text contains both an email address and a phone number");
        }
        return new Factor("CONTACT_DETAILS", "WARN", configuration.getAtsContactWeight(),
                "Resume text is missing " + (email ? "a phone number" : phone ? "an email address" : "both an email address and a phone number")
                        + ". If these are only in a header image, an applicant tracking system will not see them.");
    }

    /**
     * Whether the text looks like readable prose rather than mangled extraction.
     *
     * <p>Measured by word count and by the presence of absurdly long unbroken
     * tokens, which is what a failed PDF text extraction looks like from the
     * inside. Explicitly not a claim about the original document's appearance.
     */
    private Factor textExtractability(String text, int wordCount) {
        int mangled = 0;
        for (String word : text.split("\\s+")) {
            if (MANGLED.matcher(word).matches()) {
                mangled++;
            }
        }
        double ratio = wordCount == 0 ? 1 : (double) mangled / wordCount;
        if (ratio > 0.02) {
            return new Factor("TEXT_EXTRACTABLE", "FAIL", configuration.getAtsExtractionWeight(),
                    mangled + " of " + wordCount + " tokens are unbroken character runs of 45+ characters, "
                            + "which is what a failed text extraction looks like from the text side. "
                            + "This is a property of the extracted text, not of the document's appearance.");
        }
        return new Factor("TEXT_EXTRACTABLE", "PASS", configuration.getAtsExtractionWeight(),
                "Extracted text reads as " + wordCount + " words of ordinary language with no mangled runs");
    }

    /**
     * How much of the role's required vocabulary the resume covers at all.
     *
     * <p>Explicitly "appears somewhere", not "is evidenced well" - the evidence
     * quality is already scored in the required-skills dimension, and counting it
     * again here would weight the same fact twice. This factor asks only the
     * question an ATS asks: can it find the keyword at all?
     */
    private Factor keywordCoverage(ResumeProfile profile, List<Requirement> requirements) {
        Set<String> onResume = new LinkedHashSet<>();
        for (ResumeProfile.Line line : profile.lines()) {
            onResume.addAll(SynonymRegistry.canonicalKeysIn(line.text()));
        }

        Set<String> required = new LinkedHashSet<>();
        for (Requirement requirement : requirements) {
            if (requirement.category() == RequirementCategory.REQUIRED_SKILL) {
                requirement.normalization().terms().forEach(t -> required.add(t.canonicalKey()));
            }
        }
        // Phrase terms are matched literally elsewhere; here only the vocabulary
        // is counted, so that the factor is comparable across every job
        // description.
        required.removeIf(key -> key.startsWith("PHRASE_"));
        if (required.isEmpty()) {
            return new Factor("KEYWORD_COVERAGE", "WARN", configuration.getAtsCoverageWeight(),
                    "The job description states no recognisable required technologies, so keyword coverage "
                            + "cannot be measured");
        }
        long covered = onResume.stream().filter(required::contains).count();
        double ratio = (double) covered / required.size();
        String status = ratio >= 0.6 ? "PASS" : ratio >= 0.3 ? "WARN" : "FAIL";
        return new Factor("KEYWORD_COVERAGE", status, configuration.getAtsCoverageWeight(),
                covered + " of " + required.size() + " required technologies appear anywhere in the resume text ("
                        + Math.round(ratio * 100) + "%)");
    }

    /**
     * Whether the same line appears more than once.
     *
     * <p>Repetition is worth reporting on its own terms - duplicated content is
     * both a writing problem and a signal that a template was filled in carelessly.
     */
    private Factor duplication(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (String rawLine : text.split("\\R")) {
            String line = SynonymRegistry.normalizePhrase(rawLine);
            if (line.isBlank()) {
                continue;
            }
            total++;
            counts.merge(line, 1, Integer::sum);
        }
        int duplicated = counts.values().stream().filter(c -> c > 1).mapToInt(c -> c - 1).sum();
        if (total == 0) {
            return new Factor("DUPLICATION", "WARN", configuration.getAtsDuplicationWeight(),
                    "No lines to compare");
        }
        double ratio = (double) duplicated / total;
        if (ratio > 0.1) {
            return new Factor("DUPLICATION", "FAIL", configuration.getAtsDuplicationWeight(),
                    duplicated + " of " + total + " lines are repeated verbatim");
        }
        return new Factor("DUPLICATION", "PASS", configuration.getAtsDuplicationWeight(),
                "No repeated lines among " + total + " lines of text");
    }

    /**
     * Whether a single technology is repeated implausibly often.
     *
     * <p>Keyword stuffing is the failure mode this whole report format exists to
     * resist, so the engine should be able to notice when a resume does it. A
     * technology repeated across a genuine skills list plus two roles is normal;
     * one repeated many times in a short document is a different claim.
     */
    private Factor keywordStuffing(String text, ResumeProfile profile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ResumeProfile.Line line : profile.lines()) {
            for (String key : SynonymRegistry.canonicalKeysIn(line.text())) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return new Factor("KEYWORD_STUFFING", "WARN", configuration.getAtsStuffingWeight(),
                    "No recognisable technologies to check for repetition");
        }
        int wordCount = Math.max(1, wordCount(text));
        String worstKey = null;
        int worst = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > worst) {
                worst = entry.getValue();
                worstKey = entry.getKey();
            }
        }
        // Ten mentions across a full CV is unremarkable; a tenth of the document
        // being one technology is not.
        double density = (double) worst / wordCount;
        if (worst >= 12 || density > 0.02) {
            return new Factor("KEYWORD_STUFFING", "WARN", configuration.getAtsStuffingWeight(),
                    SynonymRegistry.displayName(worstKey) + " is named " + worst + " times across "
                            + wordCount + " words");
        }
        return new Factor("KEYWORD_STUFFING", "PASS", configuration.getAtsStuffingWeight(),
                "No technology is repeated more than " + worst + " times across " + wordCount + " words");
    }

    /**
     * Whether the text is broken into readable lines.
     *
     * <p>One long unbroken block of text is genuinely harder for a parser to
     * segment, and that is measurable. What is not measurable - and therefore not
     * claimed - is anything about column layouts, which produce exactly the same
     * text.
     */
    private Factor readingStructure(String text) {
        String[] lines = text.split("\\R");
        int nonEmpty = 0;
        int overlyLong = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (trimmed.length() > 400) {
                overlyLong++;
            }
        }
        if (nonEmpty == 0) {
            return new Factor("READABLE_STRUCTURE", "FAIL", configuration.getAtsStructureWeight(),
                    "No line breaks found in the extracted text");
        }
        if (overlyLong > nonEmpty / 4) {
            return new Factor("READABLE_STRUCTURE", "WARN", configuration.getAtsStructureWeight(),
                    overlyLong + " of " + nonEmpty + " lines run past 400 characters, which gives a parser "
                            + "little to segment on");
        }
        return new Factor("READABLE_STRUCTURE", "PASS", configuration.getAtsStructureWeight(),
                nonEmpty + " separate lines, none longer than 400 characters");
    }

    private int wordCount(String text) {
        int count = 0;
        for (String word : text.split("\\s+")) {
            if (!word.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
