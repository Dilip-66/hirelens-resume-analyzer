# Scoring calibration: findings from the first benchmark run

30 scenarios, run through the real `AnalysisEngine` with semantic matching stubbed.
Reproduce with `mvn test -Dtest=AnalysisBenchmarkTest`; measurements land in
`target/benchmark-report.md` and `target/benchmark-results.tsv`.

Baseline: the engine as it stood before this calibration pass scored Arjun Rao at
**90%** (required 89.9, experience 100, responsibilities 81, preferred 85.6), which
is the reference point every change below is measured against.

## Summary

| # | Finding | Severity | Evidence |
|---|---|---|---|
| F1 | "Strong experience with Azure" is satisfied by AWS | **Critical** | `negative_synonym_pairs` |
| F2 | "React Native" reduces to "React" and is satisfied by React experience | **Critical** | `negative_synonym_pairs` |
| F3 | "Deep Learning" reduces to "Learning" | **High** | `negative_synonym_pairs` |
| F4 | A mention of "service layer" satisfies a Microservices requirement | **High** | `partial_spring_without_microservices` |
| F5 | The experience dimension is silently dropped when the resume states no figure | **High** | 17 of 30 scenarios |
| F6 | "AI/ML" conflates machine learning with artificial intelligence | **Medium** | `weak_junior_for_ml_engineer` |
| F7 | The vocabulary is much narrower than the JD and resume domain it is asked to read | **Medium** | `strong_platform_synonyms`, `strong_frontend` |
| F8 | Responsibility names are rendered with a leading conjunction | **Low** | `poor_data_for_marketing` |

## F1 - Azure is satisfied by AWS (critical)

`SynonymRegistry` modelled cloud providers as *surface forms of a generic
`CLOUD_PLATFORM` term*, and `CATEGORY_MEMBERS` then let any provider satisfy that
generic term. The chain:

```
"Strong experience with Azure"  -> CLOUD_PLATFORM
resume contains "AWS"           -> satisfies CLOUD_PLATFORM
requirement                     -> EXPLICIT_MATCH
```

A systems role asking specifically for Azure was reported as met by a candidate
who has only ever used AWS. The report would show a green tick and an evidence
line mentioning the wrong provider, which is worse than a false negative because
the user has no reason to check.

The generic-category mechanism is sound for *categories* (`FRONTEND` is genuinely
satisfied by React) and wrong for *siblings* (Azure is not a kind of AWS, it is a
different product). Fix: named providers become their own canonical terms;
`CLOUD_PLATFORM` keeps only genuinely open wording ("cloud platforms", "cloud
services") and keeps the member list so that open wording is still satisfied by a
named provider.

## F2 - React Native reduces to React (critical)

There is no `REACT_NATIVE` term, so "React Native" is normalised by
longest-substring scan and the `react` pattern inside it matches. A candidate with
React and no React Native is reported as meeting a React Native requirement.

Same shape of defect: any longer technology name built from a shorter registered
one (`Spring Boot` over `Spring`, `Machine Learning` over nothing, `Deep Learning`
over nothing) is a chance to produce a false match. Note that `SkillTrie` already
guards this with longest-match-wins; `SynonymRegistry`'s substring scan did not
have the equivalent protection at the *term* level.

## F3 - Deep Learning reduces to Learning (high)

A regression introduced when bare intensity qualifiers were added to the
`RequirementNormalizationService` prefix strip list: "deep" was treated as a
qualifier and stripped, leaving "Learning".

The general defect is the one F2 is an instance of. The stripper runs before the
vocabulary lookup, so any word that is both an intensity qualifier and part of a
technology name is destroyed. Fix: resolve the full phrase against the vocabulary
*first*, and only strip qualifiers if that fails.

## F4 - "service layer" satisfies a Microservices requirement (high)

`MICROSERVICES` contextual cues include the bare word "service". Owen's resume
contains "Wrote JUnit tests for service layer code", which is a single-module
layered application - the opposite of the requirement - and it produced a
`STRONG_CONTEXTUAL_MATCH` on a **required** skill.

This is "generic keyword matches are overvalued" in its clearest form. A cue list
has to be specific enough that a hit means the capability, not merely that a
related word of two syllables appeared. Cues made of generic nouns are removed;
cues are now required to be either multi-word phrases or unambiguous technical
terms.

## F5 - The experience dimension is silently dropped (high)

17 of 30 scenarios report `CANDIDATE_UNKNOWN`, and in every one of them the
experience dimension returns `null`. Because the overall score renormalises over
assessed dimensions, the 20% weight disappears rather than penalising anyone.

The effect is the worst version of "experience mismatch barely affects the score":
it affects it *not at all*. `experience_underqualified` is a candidate with one
year against a three-to-five-year band, and the scenario returns an experience
score of `null` - a perfect illustration, because a reader seeing `null` would
reasonably assume the engine had no opinion, when in fact the single most
predictive signal in screening was dropped on the floor.

The engine is being *honest* here, and the honesty is correct: a resume that
states no figure really does not state one, and deriving a number from employment
dates is an inference. But the consequence of the current design is that a
20%-weighted dimension is unavailable for most real resumes, which is a design
problem rather than a correctness one.

Fix: derive years from **stated** date ranges as a fallback, and only when no
explicit figure exists. Two properties keep this inside the accuracy rules:
the intervals are unioned, so concurrent roles are not double counted, and the
result is labelled inferred with a confidence discount, so a report can never
present it as something the candidate said. Absence of dates still yields `null`.

## F6 - AI/ML conflates two distinct concepts (medium)

`AI_ML` covers "ai", "ml", "machine learning" and "artificial intelligence" as one
term, so a JD asking for machine learning and a resume offering only artificial
intelligence are treated as the same requirement. The task's own rules list them
as separate entries. `weak_junior_for_ml_engineer` could not even assert on
"Machine Learning" because the requirement was extracted under the name "AI/ML".

Fix: separate terms, and let the literal "AI/ML" in a job description normalise to
a disjunction of the two - which is what that slash means.

## F7 - Vocabulary narrower than its domain (medium)

`K8s` is not a registered synonym for Kubernetes, so `strong_platform_synonyms`
reported a Kubernetes requirement as unmet for a resume that says "Kubernetes"
three times. Same for accessibility (a candidate who wrote "accessible" did not
satisfy "web accessibility"), Terraform, Linux, MySQL, Kafka, Node.js, Angular,
SEO and Google Analytics.

Unrecognised terms do still work - the normaliser falls back to exact phrase
matching - which is why this shows up as a missed synonym rather than a missing
requirement. The cost is that they gain no cross-wording equivalence, so a
candidate who writes the right thing in a different way is reported as a gap.

## F8 - Responsibility names render with a leading conjunction (low)

"Develop and maintain the content strategy" has its leading verb stripped, leaving
"and maintain the content strategy" as the requirement's display name. Cosmetic,
but it is the string a user reads on their own report.

## Benchmark expectations that were wrong, not the engine

Six scenario bands were written from intuition and are demonstrably
miscalibrated. They are corrected in the corpus, with the reasoning recorded
per scenario, and the engine is **not** changed to fit them:

- `experience_overqualified` (45% actual): expected 55-100. A seven-year backend
  engineer against a full-stack JD genuinely has no React, JavaScript or
  TypeScript. 45 is right.
- `moderate_data_analyst_for_fullstack` (11%): expected 20-60. A data analyst
  meeting 2 of 11 required Java and React skills is not a moderate match.
- `moderate_devops_for_backend` (16%): expected 20-60, for the same reason.
- `overqualified_sre_for_junior_jd` (23%): expected 25-65. Close, and the honest
  conclusion is that an SRE CV is a weak match for a product role that happens to
  share some infrastructure vocabulary.
- `preferred_only_gap_full_preferred` (27% required): expected 40%. A frontend CV
  meeting 3 of 11 required full-stack skills is below 40%.
- `strong_platform_synonyms` (33% required): expected 85%. The JD is an
  unreasonable grab bag spanning backend, frontend and cloud; the candidate
  legitimately lacks Java, React, PostgreSQL and REST entirely. The synonym
  assertions are kept and moved to explicit per-requirement expectations, which
  is what actually tests them.

## What is deliberately not changing

- **Category weights.** No benchmark evidence showed preferred skills dominating
  or required skills being outweighed. `strong_but_no_ai` scores 81 with two
  preferred skills missing, which is the intended asymmetry.
- **Determinism, explainability, evidence-based matching, persistence, the API
  contract.** All preserved.
- **The LLM's role.** Still narrative-only, and its output is still validated
  against the engine's findings.

---

# Second round: projects, education, ATS, and the tokenizer

The first round fixed what the vocabulary meant. This round added three
dimensions that were not measured at all, and found that two of them were only
safe to add once the matching underneath them was trustworthy.

## F9 - Projects: the first project of a section was silently dropped (critical)

Adding project relevance surfaced a bug that could not have been found without
it. A resume's projects section is a contiguous run of lines; the extractor
treated the first line as a section marker and consumed it with a `continue`,
so the project it named never became a project. A resume whose strongest work
was listed first scored as though that work did not exist.

The symptom was easy to misread as a calibration problem - two projects in, one
project out, and the surviving one scored low. It was an off-by-one in the state
machine, and no score band would have revealed it.

Fix: a section transition sets state and *then* processes the line. The regression
test is `theFirstProjectIsNotLost`, which asserts on the count of both projects
and on the number of lines attached to the first - a project that keeps its title
but loses two of its three descriptions is the same failure wearing a different hat.

## F10 - Requirement phrases needed a real tokenizer, not an overlap rule (high)

Widening the vocabulary (F7) exposed how job descriptions phrase things.
Requirements are read left to right, taking the longest registered term at each
position and stepping past it. Three cases forced this:

| Job description wording | Correct reading | Naive reading |
| --- | --- | --- |
| `AWS EC2` | EC2 | AWS + EC2 |
| `React Native` | React Native | React + Native |
| `RESTful API development` | REST APIs | REST APIs + API design |

An earlier attempt dropped any match overlapping a longer one, which fixed the
first row and broke the third: "API development" is longer than "RESTful API" and
starts later, so it won, and the requirement that the rest of the job description
also names as "REST APIs" lost half its meaning. The corrected attempt - keep a
shorter match only when the longer one's *name* contains it - fixed the third row
and broke "AWS EC2 or AWS S3", where the vendor appeared in both halves.

Scanning left to right needs no special cases for any of the three, because a
token that has been consumed is never re-read from the inside. It also stops
"TypeScript" registering as Java, which the global-longest-match version did not.

The evidence side deliberately does *not* work this way: a line saying
"PostgreSQL and SQL" is evidence of both, so it is matched with
`canonicalKeysIn`, which reports every hit. The asymmetry is the point - a job
description naming EC2 is asking for EC2, while a CV offering EC2 has offered AWS.

### Known limitation: a service name does not credit its platform

A CV listing only "S3" does not credit AWS on evidence alone, though it earns
partial credit through the cloud-platform path. Inferring the platform from the
product needs an explicit service-to-platform relation rather than a rule that
adds every parent term, because React Native must not imply React and a blanket
"add the parent" rule reintroduces F2. Left as a known gap rather than fixed late
and under-tested.

## F11 - Projects, education and ATS, and when each may be scored

All three are new dimensions at 5% each. The weight is small on purpose: 5% is
enough for a dimension to matter and small enough that a weak measurement cannot
carry a decision.

**Projects (5%)** - assessed only when the resume has an explicit projects
section. Work described under responsibilities is already scored there, so
counting it again as a project would let one bullet be paid for twice, which is
the kind of error that flatters exactly the resumes written by people who write
two versions of everything.

**Education (5%)** - assessed only when the job description states an education
requirement. Scoring a resume down for a line the employer never wrote would
penalise the candidate for the employer's silence. A missing degree against a
stated requirement is a shortfall, not an error.

**ATS (5%)** - text-only, and its note says so. This engine never sees the PDF,
so it cannot know whether a resume uses two columns or hides a phone number in a
header image. It assesses what extraction can see: contact details, section
completeness, text extractability, quantified outcomes and keyword coverage. The
factor weights sum to one, and there is a test that keeps them summing to one,
because weights that drift make the score move as factors are added.

Both projects and education return `null` with an explanatory note when they are
not measured - the reference pair does exactly that, and a reader should be able
to tell the difference between "no opinion" and "measured, and it was zero".

## What this round changed about the reference pair

Nothing. `strong_full_stack` still returns 90 / 89.9 / 100 / 81 / 85.6, because
the resume has no projects section and the job description asks for no degree.
The two new `null` categories are covered by `ExperienceCalibrationTest` and
`AnalysisResponseCompatibilityTest`, and the ATS score of 89 is now serialised
through the API rather than omitted.
