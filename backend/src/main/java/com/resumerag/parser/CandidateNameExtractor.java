package com.resumerag.parser;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Derives the candidate's display name for an analysis report.
 *
 * <p>The report list is read by people, so every row needs to say whose resume
 * it is about. The backend stored nothing but a file name and the raw text, so
 * the list previously hardcoded the same "Analysis Report" label on every row.
 *
 * <p>Strategy is first-non-empty-line, because that is overwhelmingly where a
 * resume puts the name ("Priya Sharma"), with the file name as a fallback for
 * documents that lead with a heading such as "RESUME" instead.
 */
@Component
public class CandidateNameExtractor {

    /**
     * Lines that are a document title or contact detail rather than a name.
     * Matching any of these means we should keep looking, not that the resume
     * has no name.
     */
    private static final Pattern NOT_A_NAME = Pattern.compile(
            "(?i)^(resume|curriculum\\s+vitae|cv|profile|about|contact|personal\\s+details|"
                    + "professional\\s+summary|summary|objective|experience|education|skills|"
                    + "employment|history|references|page\\s*\\d+|confidential)\\b.*$");

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern PHONE = Pattern.compile(".*\\+?\\d[\\d\\s().-]{6,}\\d.*");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");

    /**
     * A name is letters plus the punctuation that legitimately appears in one
     * (hyphen, apostrophe, the period in "Jr."). Anything else - "&amp;", "/", a
     * digit-heavy line, a trailing sentence period - means the line is prose or a
     * heading rather than a person.
     */
    private static final Pattern NAME_CHARS = Pattern.compile("^[A-Za-z][A-Za-z .'-]*$");

    /** "Name: Priya Sharma" / "Candidate - Arjun Rao" */
    private static final Pattern LABELLED = Pattern.compile("(?i)^(?:name|candidate)\\s*[:\\-–]\\s*(.+)$");

    private static final int MAX_NAME_LENGTH = 60;
    private static final int MIN_NAME_WORDS = 2;
    private static final int MAX_NAME_WORDS = 4;
    private static final int MAX_LINES_SCANNED = 6;

    /**
     * @param rawText  parsed resume text; may be null or blank
     * @param fileName original upload name; may be null
     * @return a human-readable name, or the file name, or {@code "Unknown Candidate"}
     */
    public String extract(String rawText, String fileName) {
        String fromText = fromFirstLines(rawText);
        if (fromText != null) {
            return fromText;
        }
        String fromFile = fromFileName(fileName);
        if (fromFile != null) {
            return fromFile;
        }
        return "Unknown Candidate";
    }

    private String fromFirstLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String[] lines = rawText.split("\\R");
        int scanned = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (++scanned > MAX_LINES_SCANNED) {
                return null;
            }
            // Names can wrap onto their own line, so only accept a decisive
            // disqualifier here rather than any header-looking line.
            if (NOT_A_NAME.matcher(line).matches()) {
                continue;
            }
            if (EMAIL.matcher(line).find() || URL.matcher(line).find() || PHONE.matcher(line).matches()) {
                continue;
            }
            if (line.length() > MAX_NAME_LENGTH || !HAS_LETTER.matcher(line).find()) {
                continue;
            }
            var labelled = LABELLED.matcher(line);
            if (labelled.matches()) {
                // An explicit "Name:" label is a strong signal, so a single word
                // is still acceptable here even though it would be rejected bare.
                String value = clean(labelled.group(1));
                if (value != null && NAME_CHARS.matcher(value).matches()) {
                    return value;
                }
                continue;
            }
            if (!looksLikeName(line)) {
                continue;
            }
            return clean(line);
        }
        return null;
    }

    /**
     * Rejects prose that happens to sit on an early line. "Built things." is a
     * sentence, not a person, and accepting it labels a whole report with it.
     *
     * <p>Requires at least two words. A lone capitalised word on an early line is
     * almost always a heading ("Engineering", "Kubernetes") rather than a
     * surname, and a one-word real name is recoverable from the file name.
     */
    private boolean looksLikeName(String line) {
        if (!NAME_CHARS.matcher(line).matches()) {
            return false;
        }
        // A trailing period means a sentence ("Built things."), not an initial.
        char last = line.charAt(line.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return false;
        }
        int words = line.split("\\s+").length;
        return words >= MIN_NAME_WORDS && words <= MAX_NAME_WORDS;
    }

    private String clean(String candidate) {
        if (candidate == null) {
            return null;
        }
        // Collapse internal whitespace; drop any trailing separator the layout left.
        String value = candidate.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("[•|]+$", "").trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * "Priya_Sharma_Resume.pdf" -&gt; "Priya Sharma".
     *
     * <p>Only used when the text has no usable name, so it favours being
     * recognisable over being clever: it strips the extension, common
     * document-title suffixes, and separators.
     */
    /**
     * File names that carry no person: "resume.pdf", "cv.pdf", "final_v2.pdf".
     * After the suffix strip these can be left holding only a document title, and
     * reporting a candidate as "Resume" is worse than admitting we don't know.
     */
    private static final Set<String> NOT_A_PERSON = Set.of(
            "resume", "cv", "curriculum vitae", "profile", "final", "updated", "copy", "document");

    private String fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String value = fileName;
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        value = value.replaceAll("(?i)[\\s_\\-]+(resume|cv|curriculum\\s+vitae|final|updated|v\\d+)$", "");
        value = value.replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ").trim();
        if (value.isEmpty() || NOT_A_PERSON.contains(value.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return toTitleCase(value);
    }

    private String toTitleCase(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean startOfWord = true;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            } else {
                // Preserve the separator. Dropping it turned "Arjun Rao" into
                // "ArjunRao", which is not a name anyone goes by.
                sb.append(c);
                startOfWord = true;
            }
        }
        return sb.toString().trim();
    }
}
