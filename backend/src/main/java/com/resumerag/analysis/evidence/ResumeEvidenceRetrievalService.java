package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.EvidenceCandidate;
import com.resumerag.analysis.model.EvidenceStrength;
import com.resumerag.analysis.model.MatchProvenance;
import com.resumerag.analysis.model.NormalizedTerm;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Finds the resume lines that bear on each requirement, and rates how well each
 * one supports it.
 *
 * <p>Three sources, in order of how much they can prove:
 *
 * <ol>
 *   <li><b>Direct mention in context</b> - the requirement's own terms appear in
 *       a line, and the line's section sets the ceiling. "Dockerized
 *       microservices" in a role is the strongest thing a resume can offer;
 *       "Docker" in a skills list is a claim, and is capped accordingly.</li>
 *   <li><b>Contextual cues</b> - a related phrase the resume does use, such as
 *       "query optimization" for a "PostgreSQL" requirement. Supporting evidence,
 *       never proof.</li>
 *   <li><b>Semantic neighbours</b> - passages above a similarity threshold, which
 *       is the only way a prose requirement like "Participate in code reviews"
 *       gets examined at all.</li>
 * </ol>
 *
 * <p>Every candidate keeps the sentence it came from, so the report can show the
 * text that decided the outcome rather than asking the user to trust a number.
 *
 * <p>Stateless. The resume profile is a parameter throughout, because a Spring
 * singleton holding the resume in a field would let one user's analysis read
 * another user's evidence.
 */
@Service
public class ResumeEvidenceRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(ResumeEvidenceRetrievalService.class);

    /** A cue match never reaches professional-strength evidence, however the line reads. */
    private static final EvidenceStrength CUE_CEILING = EvidenceStrength.CONTEXTUAL;

    private final SemanticEvidenceMatcher semanticEvidenceMatcher;
    private final ScoringConfiguration scoringConfiguration;

    public ResumeEvidenceRetrievalService(SemanticEvidenceMatcher semanticEvidenceMatcher,
                                          ScoringConfiguration scoringConfiguration) {
        this.semanticEvidenceMatcher = semanticEvidenceMatcher;
        this.scoringConfiguration = scoringConfiguration;
    }

    /**
     * Evidence for every requirement, retrieved in one pass.
     *
     * <p>All requirements are embedded in a single batch. Doing it per requirement
     * would mean one embedding round trip each, which is the difference between a
     * few seconds and a minute for a typical job description.
     */
    public List<List<EvidenceCandidate>> evidenceForAll(ResumeProfile profile,
                                                       List<Requirement> requirements,
                                                       UUID resumeId) {
        List<LineIndex> index = indexLines(profile);
        List<List<SemanticEvidenceMatcher.Index.Hit>> semantic =
                rankSemantically(requirements, resumeId);

        List<List<EvidenceCandidate>> all = new ArrayList<>(requirements.size());
        for (int i = 0; i < requirements.size(); i++) {
            List<SemanticEvidenceMatcher.Index.Hit> hits =
                    i < semantic.size() ? semantic.get(i) : List.of();
            all.add(evidenceFor(profile, index, requirements.get(i), hits));
        }
        return all;
    }

    /**
     * Evidence for one requirement, strongest first.
     *
     * <p>Deliberately untruncated. Truncation is a display concern, and doing it
     * here would mean a requirement's match state depended on how many snippets
     * we chose to keep for the report. The matching service takes the full list
     * and truncates only what it stores.
     */
    public List<EvidenceCandidate> evidenceFor(ResumeProfile profile,
                                               Requirement requirement,
                                               List<SemanticEvidenceMatcher.Index.Hit> semanticHits) {
        return evidenceFor(profile, indexLines(profile), requirement, semanticHits);
    }

    private List<EvidenceCandidate> evidenceFor(ResumeProfile profile,
                                                List<LineIndex> index,
                                                Requirement requirement,
                                                List<SemanticEvidenceMatcher.Index.Hit> semanticHits) {
        List<EvidenceCandidate> candidates = new ArrayList<>();
        for (NormalizedTerm term : requirement.normalization().terms()) {
            candidates.addAll(directEvidence(profile, index, term));
            candidates.addAll(cueEvidenceFor(profile, term));
        }
        candidates.addAll(semanticEvidenceFor(requirement, semanticHits));
        return rank(candidates);
    }

    /**
     * The canonical technologies each resume line names, resolved once per line.
     *
     * <p>Precomputed because it is both the expensive part and the part that has
     * to be right. A pattern scan per requirement would re-derive the same answer
     * once per requirement per line, and - more importantly - a naive scan
     * satisfies a "React" requirement from the words "React Native", because the
     * shorter term matches inside the longer product name. Resolving longest match
     * once per line and then doing set lookups makes that class of false match
     * impossible rather than merely unlikely.
     */
    private List<LineIndex> indexLines(ResumeProfile profile) {
        List<LineIndex> index = new ArrayList<>(profile.lines().size());
        for (ResumeProfile.Line line : profile.lines()) {
            index.add(new LineIndex(
                    line,
                    new LinkedHashSet<>(SynonymRegistry.canonicalKeysIn(line.text())),
                    SynonymRegistry.normalizePhrase(line.text())));
        }
        return index;
    }

    /**
     * One resume line, with its technologies and its normalised text precomputed.
     *
     * <p>Precomputed because it is both the expensive part and the part that has to
     * be right. Scanning per requirement would re-derive the same answer once per
     * requirement per line, and a naive scan satisfies a "React" requirement from
     * the words "React Native" because the shorter term matches inside the longer
     * product name. Resolving longest match once per line makes that class of false
     * match impossible rather than merely unlikely.
     */
    private record LineIndex(ResumeProfile.Line line, Set<String> technologies, String normalized) {}

    private List<List<SemanticEvidenceMatcher.Index.Hit>> rankSemantically(
            List<Requirement> requirements, UUID resumeId) {
        if (requirements.isEmpty()) {
            return List.of();
        }
        try {
            return semanticEvidenceMatcher.indexFor(resumeId).rankAll(
                    requirements.stream().map(Requirement::sourceText).toList(),
                    scoringConfiguration.getSemanticHitLimit());
        } catch (Exception e) {
            // Losing semantic recall costs accuracy and must not cost the analysis.
            log.warn("Semantic evidence unavailable, continuing with lexical matching only: {}",
                    e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------------
    // Lexical
    // ---------------------------------------------------------------------

    /**
     * Evidence from a term actually being named.
     *
     * <p>A category requirement is satisfied by a concrete member: "Deploy and
     * maintain applications on cloud platforms" is met by evidence of AWS EC2, not
     * only by the words "cloud platform". A phrase term with no vocabulary entry
     * still gets looked for, on its own words, so an unrecognised requirement is
     * never silently skipped.
     */
    private List<EvidenceCandidate> directEvidence(ResumeProfile profile,
                                                  List<LineIndex> index,
                                                  NormalizedTerm term) {
        List<EvidenceCandidate> candidates = new ArrayList<>();
        Set<String> acceptable = acceptableKeys(term.canonicalKey());
        List<Pattern> memberSurfaces = SynonymRegistry.isGeneric(term.canonicalKey())
                ? memberSurfacePatterns(term.canonicalKey())
                : List.of();

        for (LineIndex entry : index) {
            ResumeProfile.Line line = entry.line();
            boolean named = namesAny(entry.technologies(), acceptable);
            if (!named && matchesAny(entry.normalized(), memberSurfaces)) {
                named = true;
            }
            if (named) {
                candidates.add(new EvidenceCandidate(
                        line.text(), line.section().name(), line.baseStrength(),
                        MatchProvenance.EXPLICIT, 1.0, term.displayName()));
            } else if (matchesPhraseTerm(entry.normalized(), term)) {
                candidates.add(new EvidenceCandidate(
                        line.text(), line.section().name(),
                        cap(EvidenceStrength.EXPLICIT_SKILL, line.baseStrength()),
                        MatchProvenance.EXPLICIT, 0.9, term.displayName()));
            }
        }
        return candidates;
    }

    /**
     * The surface forms of a category's member technologies.
     *
     * <p>Only for generic categories, and the reason is precision at both ends. A
     * requirement for "cloud platforms" is met by a line saying "deployed to AWS
     * EC2" even though the canonical key for that line resolves to the more
     * specific EC2 - the provider is plainly present. Without this the generic
     * requirement only ever matched a line that spelled out "cloud", which is not
     * how anyone writes a resume.
     *
     * <p>It cannot leak between providers: AWS's surfaces are only ever compared
     * against a CLOUD_PLATFORM requirement, never against an Azure one, because
     * the named providers are separate terms.
     */
    private List<Pattern> memberSurfacePatterns(String canonicalKey) {
        List<Pattern> patterns = new ArrayList<>();
        for (String member : SynonymRegistry.membersOf(canonicalKey)) {
            for (String surface : SynonymRegistry.surfacesFor(member)) {
                String normalized = SynonymRegistry.normalizePhrase(surface);
                if (!normalized.isBlank()) {
                    patterns.add(SynonymRegistry.wholeWordPattern(normalized));
                }
            }
        }
        return patterns;
    }

    private boolean matchesAny(String normalizedText, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(normalizedText).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The keys that count as naming this term: itself, and - for a category - the
     * technologies that are instances of it.
     */
    private Set<String> acceptableKeys(String canonicalKey) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(canonicalKey);
        keys.addAll(SynonymRegistry.membersOf(canonicalKey));
        return keys;
    }

    private boolean namesAny(Set<String> technologiesOnLine, Set<String> acceptable) {
        for (String key : technologiesOnLine) {
            if (acceptable.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact-phrase match for a term the vocabulary does not know.
     *
     * <p>Weaker than a vocabulary hit, because a phrase like "infrastructure as
     * code" says the words but not necessarily the practice. A resume writing the
     * phrase is evidence; it is not the same as a resume deploying Terraform.
     */
    private boolean matchesPhraseTerm(String normalizedLine, NormalizedTerm term) {
        if (!term.canonicalKey().startsWith("PHRASE_")) {
            return false;
        }
        String haystack = normalizedLine;
        for (String pattern : term.patterns()) {
            if (!SynonymRegistry.normalizePhrase(pattern).isBlank()
                    && SynonymRegistry.containsWholeWord(haystack, SynonymRegistry.normalizePhrase(pattern))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evidence from a related phrase rather than the requirement's own.
     *
     * <p>Kept strictly below a direct mention. "Reduced API response time by 30%
     * through caching and query optimization" is real evidence that someone
     * improved performance and no evidence at all about debugging; a cue list loose
     * enough to blur that is how a resume ends up credited with abilities it never
     * mentions. The benchmark removed a tail of bare-noun cues for exactly this
     * reason - "service" was satisfying a required Microservices requirement from
     * the phrase "service layer".
     */
    private List<EvidenceCandidate> cueEvidenceFor(ResumeProfile profile, NormalizedTerm term) {
        List<String> cues = SynonymRegistry.cuesFor(term.canonicalKey());
        if (cues.isEmpty()) {
            return List.of();
        }
        List<EvidenceCandidate> candidates = new ArrayList<>();
        for (ResumeProfile.Line line : profile.lines()) {
            String haystack = SynonymRegistry.normalizePhrase(line.text());
            for (String cue : cues) {
                if (!SynonymRegistry.containsWholeWord(haystack, SynonymRegistry.normalizePhrase(cue))) {
                    continue;
                }
                // A quantified or achieved result is a demonstrated outcome, one
                // step above a bare contextual overlap - and still short of naming
                // the requirement, which is what a direct mention would do.
                EvidenceStrength strength = SynonymRegistry.isOutcomeBearing(line.text())
                        ? EvidenceStrength.EXPLICIT_SKILL
                        : CUE_CEILING;
                candidates.add(new EvidenceCandidate(
                        line.text(), line.section().name(),
                        cap(strength, line.baseStrength()),
                        MatchProvenance.INFERRED, 0.0, term.displayName()));
                break;
            }
        }
        return candidates;
    }

    private EvidenceStrength cap(EvidenceStrength desired, EvidenceStrength ceiling) {
        return desired.level() <= ceiling.level() ? desired : ceiling;
    }

    // ---------------------------------------------------------------------
    // Semantic
    // ---------------------------------------------------------------------

    /**
     * Evidence from passages that mean something similar to the requirement.
     *
     * <p>Thresholded, and a passage below the bar is not evidence at all. Without
     * a threshold the nearest chunk to "Participate in code reviews" is always
     * returned - every resume has chunks, they are all somewhat wordy, and a model
     * handed the closest one will describe it as evidence. Silence has to be an
     * available answer.
     */
    private List<EvidenceCandidate> semanticEvidenceFor(Requirement requirement,
                                                        List<SemanticEvidenceMatcher.Index.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        if (requirement.category() == RequirementCategory.EXPERIENCE) {
            // Years of experience is compared numerically, not by similarity.
            return List.of();
        }
        List<EvidenceCandidate> candidates = new ArrayList<>();
        for (SemanticEvidenceMatcher.Index.Hit hit : hits) {
            double similarity = hit.similarity();
            if (similarity < scoringConfiguration.getSemanticWeakThreshold()) {
                continue;
            }
            EvidenceStrength strength = similarity >= scoringConfiguration.getSemanticStrongThreshold()
                    ? EvidenceStrength.CONTEXTUAL
                    : EvidenceStrength.WEAK;
            candidates.add(new EvidenceCandidate(
                    hit.text(),
                    hit.section() == null ? "unknown" : hit.section(),
                    strength,
                    MatchProvenance.INFERRED,
                    similarity,
                    "semantic match (" + String.format(Locale.ROOT, "%.2f", similarity) + ")"));
        }
        return candidates;
    }

    // ---------------------------------------------------------------------
    // Ranking
    // ---------------------------------------------------------------------

    /**
     * Strongest first, then most similar, then stable by text.
     *
     * <p>Stable ordering matters: the first candidates are the sentences shown to
     * the user as the reason, so an unstable sort would show different evidence
     * for identical input between runs.
     */
    private List<EvidenceCandidate> rank(List<EvidenceCandidate> candidates) {
        return candidates.stream()
                .distinct()
                .sorted(Comparator
                        .comparingInt((EvidenceCandidate c) -> c.strength().level()).reversed()
                        .thenComparing(Comparator.comparingDouble(EvidenceCandidate::similarity).reversed())
                        .thenComparing(EvidenceCandidate::text))
                .toList();
    }

    /** The strongest candidate, or {@code null} when there is no evidence. */
    public EvidenceCandidate best(List<EvidenceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .max(Comparator.comparingInt(c -> c.strength().level()))
                .orElse(null);
    }
}
