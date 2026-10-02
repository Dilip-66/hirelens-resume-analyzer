package com.resumerag.analysis.assess;

import com.resumerag.analysis.evidence.ResumeProfile;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the education a resume states against what the job description asks
 * for.
 *
 * <p><b>Assessed only when the job description states an education
 * requirement.</b> That is the asymmetry the category needs: most job
 * descriptions say nothing about a degree, and for those there is no requirement
 * to measure against, so the honest result is no score. Reporting a percentage
 * derived from a degree nobody asked for would be inventing a dimension.
 *
 * <p>The same applies to a resume that states no education. A missing credential
 * against a role that requires one is a real finding and is reported as a
 * shortfall; a missing credential against a role that says nothing about it is
 * not a finding at all, and the dimension is simply not assessed.
 *
 * <p>Only what the document states is compared. Nothing is inferred from a
 * university name, a country, or an institution's reputation.
 */
@Service
public class EducationAssessmentService {

    /** Ordered so a higher level satisfies a lower requirement. */
    public enum DegreeLevel {
        NONE(0),
        HIGH_SCHOOL(1),
        ASSOCIATE(2),
        BACHELOR(3),
        MASTER(4),
        DOCTORATE(5);

        private final int rank;

        DegreeLevel(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    /**
     * What the resume states about education.
     *
     * <p>{@code fieldFamily} is a broad grouping rather than a discipline, because
     * job descriptions ask for "Computer Science or related field" and a candidate
     * with a Software Engineering or Information Systems degree is answering that
     * question. Treating those as a mismatch would be a false gap.
     */
    public record ResumeEducation(
            DegreeLevel level,
            String field,
            String fieldFamily,
            String institution,
            Integer year,
            List<String> evidence
    ) {
        public ResumeEducation {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        public static ResumeEducation none() {
            return new ResumeEducation(DegreeLevel.NONE, null, null, null, null, List.of());
        }
    }

    /** The outcome for one education requirement. */
    public record RequirementOutcome(
            String requirement,
            DegreeLevel requiredLevel,
            DegreeLevel heldLevel,
            String status,
            double confidence,
            List<String> resumeEvidence,
            List<String> jdEvidence,
            String explanation
    ) {}

    /**
     * The assessment for the whole category.
     *
     * <p>Either every requirement is reported, or the category is not assessed.
     * There is no partial state, because a score over "some of the education
     * requirements" would be an average over an arbitrary subset.
     */
    public record Assessment(
            boolean assessed,
            Double score,
            List<RequirementOutcome> outcomes,
            String note
    ) {
        public static Assessment notAssessed(String note) {
            return new Assessment(false, null, List.of(), note);
        }
    }

    private static final Pattern HIGH_SCHOOL = Pattern.compile(
            "\\b(secondary|high school|a[- ]levels?|gcse|gce|diploma)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSOCIATE = Pattern.compile(
            "\\b(associate'?s?|foundation degree|diploma)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACHELOR = Pattern.compile(
            "\\b(bachelor'?s?|bsc|bs |ba |b\\.?(sc|s|tech|eng|ba|com)|beng|btech|b\\.e)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MASTER = Pattern.compile(
            "\\b(master'?s?|msc|ms |ma |m\\.?(sc|s|tech|eng|ba|com)|meng|phd\\b.*(not|pending))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTORATE = Pattern.compile(
            "\\b(phd|doctorate|ph\\.d|dphil|doctoral)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_THE_FIELD = Pattern.compile(
            "\\bin ([a-z ]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    /** The broad field groupings a job description's "related field" can mean. */
    private static final List<String[]> FAMILIES = List.of(
            new String[]{"STEM", "computer", "software", "information technology", "information systems",
                    "electronics", "engineering", "mathematics", "physics", "data", "statistics"},
            new String[]{"BUSINESS", "business", "accounting", "finance", "marketing", "management",
                    "economics", "commerce"},
            new String[]{"HEALTH", "medicine", "medical", "nursing", "health", "pharmacy", "dentistry"},
            new String[]{"HUMANITIES", "english", "history", "philosophy", "languages", "literature",
                    "communications", "design"},
            new String[]{"LAW", "law", "legal"});

    /**
     * Extracts what the resume states, and compares it with the job description.
     *
     * @return an assessment, or an unassessed result when the JD states no
     *         education requirement
     */
    public Assessment assess(ResumeProfile profile, List<Requirement> requirements) {
        List<Requirement> educationRequirements = requirements.stream()
                .filter(r -> r.category() == RequirementCategory.EDUCATION)
                .toList();
        if (educationRequirements.isEmpty()) {
            return Assessment.notAssessed(
                    "No education requirement was found in this job description, so there is nothing to "
                            + "measure the resume's education against.");
        }

        ResumeEducation held = extract(profile);
        List<RequirementOutcome> outcomes = new ArrayList<>();
        double earned = 0;
        double possible = 0;

        for (Requirement requirement : educationRequirements) {
            DegreeLevel required = requiredLevel(requirement.sourceText());
            String requiredField = requestedField(requirement.sourceText());
            boolean anyLevel = acceptsAnyLevel(requirement.sourceText());

            double levelScore = levelScore(required, anyLevel, held.level());
            double fieldScore = fieldScore(requiredField, held);

            // Level dominates. A candidate with the right degree in an adjacent
            // field is a far better answer than one with the right field at the
            // wrong level, and averaging them equally would let a master's in an
            // unrelated subject beat a bachelor's in the required one.
            double score = clamp01(levelScore * 0.65 + fieldScore * 0.35);

            possible += 1.0;
            earned += score;

            outcomes.add(new RequirementOutcome(
                    requirement.name(), required, held.level(), statusFor(score), round(score),
                    held.evidence(), List.of(requirement.sourceText()),
                    explain(requirement, required, requiredField, held, levelScore, fieldScore)));
        }

        if (possible <= 0) {
            return Assessment.notAssessed("The education requirements could not be interpreted.");
        }
        return new Assessment(true, round(earned / possible * 100), outcomes, null);
    }

    /**
     * What the resume states, read from its education section.
     *
     * <p>Deliberately literal. The level and the field are read from the words
     * used, and nothing is inferred from an institution's name or a country's
     * education system - a degree from an unfamiliar institution is still a degree.
     */
    public ResumeEducation extract(ResumeProfile profile) {
        List<String> evidence = new ArrayList<>();
        for (ResumeProfile.Line line : profile.lines()) {
            if (line.section() == ResumeProfile.Line.Section.EDUCATION) {
                evidence.add(line.text());
            }
        }
        if (evidence.isEmpty()) {
            return ResumeEducation.none();
        }

        String joined = String.join(" \n ", evidence);
        DegreeLevel level = degreeLevel(joined);
        String field = fieldOf(joined);
        Integer year = yearOf(joined);
        String institution = institutionOf(evidence);

        return new ResumeEducation(level, field, familyOf(field), institution, year, evidence);
    }

    private DegreeLevel degreeLevel(String text) {
        // Most specific first: a doctorate contains the letters that a master's
        // regex would also match, and a bachelor's degree is not a foundation.
        if (DOCTORATE.matcher(text).find() && !BACHELOR.matcher(text).find()) {
            return DegreeLevel.DOCTORATE;
        }
        if (DOCTORATE.matcher(text).find() && MASTER.matcher(text).find()) {
            return DegreeLevel.DOCTORATE;
        }
        if (MASTER.matcher(text).find()) {
            return DegreeLevel.MASTER;
        }
        if (BACHELOR.matcher(text).find()) {
            return DegreeLevel.BACHELOR;
        }
        if (ASSOCIATE.matcher(text).find()) {
            return DegreeLevel.ASSOCIATE;
        }
        if (HIGH_SCHOOL.matcher(text).find()) {
            return DegreeLevel.HIGH_SCHOOL;
        }
        return DegreeLevel.NONE;
    }

    /**
     * The field of study, from the phrase the document uses.
     *
     * <p>"Bachelor's Degree - Computer Science" and "BSc Computer Science, University
     * of Manchester" both reduce to something containing "computer science". Where
     * the document says only "Bachelor's Degree", the field is left unknown rather
     * than guessed - which is the point: an unstated field is not a wrong one.
     */
    private String fieldOf(String text) {
        Matcher matcher = IN_THE_FIELD.matcher(text.trim());
        if (matcher.find()) {
            return clean(matcher.group(1));
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String[] family : FAMILIES) {
            for (int i = 1; i < family.length; i++) {
                if (lower.contains(family[i])) {
                    // Prefer the full phrase when it is present.
                    for (String[] candidate : List.of(
                            new String[]{"computer science", "software engineering"},
                            new String[]{"information technology"}, new String[]{"information systems"},
                            new String[]{"data science"}, new String[]{"electrical engineering"})) {
                        if (lower.contains(candidate[0])) {
                            return candidate[0];
                        }
                    }
                    return family[i];
                }
            }
        }
        return null;
    }

    private String familyOf(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        for (String[] family : FAMILIES) {
            for (int i = 1; i < family.length; i++) {
                if (lower.contains(family[i])) {
                    return family[0];
                }
            }
        }
        return null;
    }

    private Integer yearOf(String text) {
        Matcher matcher = YEAR.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    private String institutionOf(List<String> evidence) {
        for (String line : evidence) {
            if (line.contains(",")) {
                for (String part : line.split(",")) {
                    String cleaned = clean(part);
                    if (cleaned.matches(".*(university|college|institute|school|academy|polytechnic).*")) {
                        return cleaned;
                    }
                }
            }
            String cleaned = clean(line);
            if (cleaned.matches(".*(university|college|institute|school|academy|polytechnic).*")) {
                return cleaned;
            }
        }
        return null;
    }

    private DegreeLevel requiredLevel(String sourceText) {
        if (DOCTORATE.matcher(sourceText).find() || sourceText.toLowerCase(Locale.ROOT).contains("doctorate")) {
            return DegreeLevel.DOCTORATE;
        }
        if (MASTER.matcher(sourceText).find()
                || sourceText.toLowerCase(Locale.ROOT).contains("postgraduate")
                || sourceText.toLowerCase(Locale.ROOT).contains("post graduate")) {
            return DegreeLevel.MASTER;
        }
        if (BACHELOR.matcher(sourceText).find()
                || sourceText.toLowerCase(Locale.ROOT).contains("undergraduate")) {
            return DegreeLevel.BACHELOR;
        }
        if (ASSOCIATE.matcher(sourceText).find()) {
            return DegreeLevel.ASSOCIATE;
        }
        if (HIGH_SCHOOL.matcher(sourceText).find()) {
            return DegreeLevel.HIGH_SCHOOL;
        }
        return DegreeLevel.NONE;
    }

    /** The field the job description names, or {@code null} if it names none. */
    private String requestedField(String sourceText) {
        String lower = sourceText.toLowerCase(Locale.ROOT);
        if (lower.contains("computer science")) {
            return "computer science";
        }
        if (lower.contains("software engineering")) {
            return "software engineering";
        }
        if (lower.contains("information technology")) {
            return "information technology";
        }
        if (lower.contains("information systems")) {
            return "information systems";
        }
        if (lower.contains("data science")) {
            return "data science";
        }
        for (String[] family : FAMILIES) {
            for (int i = 1; i < family.length; i++) {
                if (lower.contains(family[i])) {
                    return family[i];
                }
            }
        }
        return null;
    }

    /** Whether the job description accepts any level of education. */
    private boolean acceptsAnyLevel(String sourceText) {
        String lower = sourceText.toLowerCase(Locale.ROOT);
        return lower.contains("any degree") || lower.contains("no degree")
                || lower.contains("degree or equivalent") || lower.contains("no formal requirement");
    }

    private double levelScore(DegreeLevel required, boolean anyLevel, DegreeLevel held) {
        if (anyLevel || required == DegreeLevel.NONE) {
            return held == DegreeLevel.NONE ? 0.5 : 1.0;
        }
        if (held == DegreeLevel.NONE) {
            return 0.0;
        }
        if (held.rank() >= required.rank()) {
            return 1.0;
        }
        // Below the bar, in proportion to how far below. Two levels short is a
        // different situation from one, and collapsing them to zero would say a
        // foundation degree is as far from a master's as no qualification at all.
        return Math.max(0.0, 1.0 - (double) (required.rank() - held.rank()) / required.rank());
    }

    private double fieldScore(String requestedField, ResumeEducation held) {
        if (requestedField == null) {
            // The job description names no field, so the field cannot be a reason to
            // mark someone down.
            return 1.0;
        }
        if (held.field() == null || held.field().isBlank()) {
            // Unstated, not wrong. Half credit, and the explanation says so.
            return 0.5;
        }
        String requestedFamily = familyOf(requestedField);
        if (requestedFamily != null && requestedFamily.equals(held.fieldFamily())) {
            return 1.0;
        }
        String requestedLower = requestedField.toLowerCase(Locale.ROOT);
        String heldLower = held.field().toLowerCase(Locale.ROOT);
        if (heldLower.contains(requestedLower) || requestedLower.contains(heldLower)) {
            return 1.0;
        }
        return 0.35;
    }

    private String statusFor(double score) {
        if (score >= 0.85) {
            return "MET";
        }
        if (score >= 0.5) {
            return "PARTIALLY_MET";
        }
        return "NOT_MET";
    }

    private String explain(Requirement requirement, DegreeLevel required, String requestedField,
                           ResumeEducation held, double levelScore, double fieldScore) {
        StringBuilder sb = new StringBuilder();
        if (held.level() == DegreeLevel.NONE) {
            sb.append("The resume states no degree, so this requirement is not evidenced. ")
              .append("That is a statement about the document, not about the candidate's ability.");
            return sb.toString();
        }
        sb.append("The resume states a ").append(label(held.level())).append(" level qualification");
        if (held.field() != null) {
            sb.append(" in ").append(held.field());
        } else {
            sb.append(" with no field of study named");
        }
        if (held.institution() != null) {
            sb.append(" (").append(held.institution()).append(")");
        }
        sb.append(". ");

        if (levelScore >= 1.0) {
            sb.append("This meets or exceeds the ").append(label(required)).append(" level the role asks for");
        } else {
            sb.append("The role asks for a ").append(label(required)).append(" level, which this is below");
        }
        if (requestedField != null) {
            if (fieldScore >= 1.0) {
                sb.append(", and the field is a recognised match for ").append(requestedField);
            } else if (fieldScore > 0.5) {
                sb.append(". The job description asks for ").append(requestedField)
                  .append(" and the resume does not name a field of study, so the field cannot be matched");
            } else {
                sb.append(". The job description asks for ").append(requestedField)
                  .append(" and the resume names a different field");
            }
        }
        return sb.toString();
    }

    private String label(DegreeLevel level) {
        return switch (level) {
            case NONE -> "no";
            case HIGH_SCHOOL -> "secondary school";
            case ASSOCIATE -> "foundation";
            case BACHELOR -> "bachelor's";
            case MASTER -> "master's";
            case DOCTORATE -> "doctorate";
        };
    }

    private String clean(String text) {
        return text == null ? null : text.replaceAll("[^A-Za-z0-9 &'/-]", "").trim();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
