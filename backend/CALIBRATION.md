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

---

# Third round: evidence classification, and the model in the score path

This round did not tune anything. Every weight, credit and threshold is where the
previous two rounds left it, and the required-skills, experience and preferred-skills
numbers for the reference pair are unchanged to two decimal places. What changed is
which evidence is allowed to produce which match state.

The trigger was a single wrong line on one candidate's report:

```
"Code reviews and software development activities"  ->  PARTIAL_MATCH, 41% confidence
```

The resume does not mention code reviews. Nothing in it supports the requirement.

## F12 - A requirement's shape was manufacturing evidence (critical)

`RequirementMatchingService.classify` treated "this is a compound requirement and
none of its terms was named" as an independent reason to award `PARTIAL_MATCH`. It
did not ask what the evidence was. Anything that got past the empty-evidence check
was enough, because the compound rule ran first and short-circuited.

That check only fires on an *empty* candidate list. A real embedding index returns
its nearest chunks whether or not they are relevant, so the list is essentially
never empty once Ollama is running - which is why the defect was invisible with the
semantic layer stubbed and present in production. Reproduced by standing in a
0.64-similarity hit for every requirement:

| Requirement | Before | After |
|---|---|---|
| Code reviews and software development activities | PARTIAL 41% | NOT EXPLICITLY MENTIONED 0% |
| Artificial intelligence or Machine learning | PARTIAL 41% | NOT EXPLICITLY MENTIONED 0% |
| Ideal candidate | PARTIAL 41% | NOT EXPLICITLY MENTIONED 0% |
| Understand business requirements | PARTIAL 41% | NOT EXPLICITLY MENTIONED 0% |
| reliable code | PARTIAL 41% | NOT EXPLICITLY MENTIONED 0% |

41% is `partialMatchConfidence` (0.45) times the 0.9 discount applied to inferred
evidence - that is, the number was a product of two constants and had nothing to do
with the resume.

Fix: evidence is now filtered before classification, and only evidence that survives
is classified. A candidate at `WEAK` strength arriving as `INFERRED` is not
supporting anything: `WEAK` is defined as "adjacent at best... never enough to claim
a match", and only semantic hits reach it, because a cue is capped at contextual by
construction and a direct mention is at least a listed skill. The filter is on
strength and provenance, not on any particular vocabulary, so it applies to any
resume and any job description.

The adjacency passage is dropped rather than scored as zero on purpose. Otherwise
the report renders it as the "evidence" for a requirement the same report says was
never mentioned, and a user has no way to tell that apart from a real finding. The
recommendation list is where an unevidenced requirement still surfaces as something
to add.

## F13 - Conjunction and disjunction were treated as the same thing (critical)

`NormalizedRequirement.requiresAllTerms()` existed, and was tested at the extraction
layer, but the classifier never called it. Every compound requirement was scored as
if the job description had written "and". Two consequences:

- **Disjunctions under-claimed.** "JavaScript and/or TypeScript" was a conjunction,
  so a candidate with only JavaScript was reported as a partial match on a
  requirement the employer had explicitly written as "one of these is enough".
- **Inci﻿dent terms discounted a real match.** "Git/GitHub collaboration" normalises
  to `GIT or GITHUB or COLLABORATION`. Git and GitHub were both directly evidenced;
  `COLLABORATION` was not, because the resume never mentions working with anyone. The
  disjunction was scored 2-of-3 and reported as PARTIAL at 30% confidence against
  professional evidence for both terms the requirement was actually written about.

Fix: the classifier reads the operator. A disjunction is met by its first evidenced
term. A conjunction still requires all of them.

## F14 - A conjunction with one silent half is not the same as one with none (high)

The natural over-correction to F12 is to classify an unevidenced conjunction purely on
its best evidence, which makes it `STRONG_CONTEXTUAL_MATCH` - and that over-claims a
conjunction just as badly, because the cue that fired supports one term and says
nothing about the others.

`Debugging and application performance` is the case that settles it. The resume's
"Reduced API response time by 30% through caching and query optimization" is real
evidence about performance. It is no evidence at all about debugging. So:

- conjunction, some terms named and others not -> PARTIAL (unchanged, and correct)
- conjunction, no term named but one supported by genuine indirect evidence -> PARTIAL
- conjunction, nothing supported at all -> silence
- disjunction, one term named -> met

The compound shape can now only ever *lower* a match state on evidence that exists.
It can no longer create one.

## F15 - "scalable" satisfied a requirement about code quality (medium)

`CLEAN_CODE` carried the bare adjectives "scalable", "maintainable", "readable" and
"best practices" as surface forms. The reference resume's summary line reads "building
scalable full-stack web applications", so `Write clean, maintainable, and scalable
code` came back as `EXPLICIT_MATCH` at 85% confidence with that line as its evidence.
The word described the applications. It said nothing about the code.

This is F4's shape one level up: a cue list that fires on ordinary prose. A candidate
is not credited with writing maintainable code because the adjective appears somewhere
else in the summary.

Fix: the bare adjectives are removed as direct surface forms and kept as what they
are - contextual cues, where they still register as SUPPORTED. The multi-word forms
that are actually about code stay direct: "clean code", "maintainable code",
"scalable code", "code quality", "readable code". "Clean maintainable" is added so
the job description's own phrasing still resolves to the one term rather than
splitting into two.

## F16 - The score moved when Ollama started or stopped (critical)

This is the one that mattered most, and it was found while looking for the others.
With the semantic layer returning the same weakly-similar passage for every
requirement, the reference pair scored **91**. With the semantic layer stubbed out,
**90**. Same resume, same job description, two different verdicts, decided by whether
a local embedding model happened to be running.

Turning the model on made the candidate look better. Every requirement gained
partial credit from a passage retrieved by similarity alone, and the score moved up.

The cause is F12 - partial credit for evidence that supports nothing. With that
fixed, both runs produce identical numbers, in every category. There is now a test
(`theScoreIsIndependentOfSemanticNoise`) that runs the reference pair twice, once
with a noisy index and once with none, and asserts the category scores match.

## What this round changed about the reference pair

Overall 90 -> 89, and it is entirely F15:

| | before | after | why |
|---|---|---|---|
| required skills | 89.9 | 89.9 | unchanged |
| experience | 100 | 100 | unchanged |
| preferred skills | 85.6 | 85.6 | unchanged |
| ATS | 89 | 89 | unchanged |
| responsibilities | 81.0 | 72.5 | F15: "Write clean, maintainable, and scalable code" stopped being met by the word "scalable" in a summary sentence |
| **overall** | **90** | **89** | the one responsibility above |

F12, F13 and F14 moved no score at all on this pair. They changed *why* each
requirement was classified rather than what it scored - the reference resume happens
to evidence the disputed requirements either way. They are not cosmetic: the same
three defects produced a two-point swing on a different job description, and F16
means two of them were silently active or inactive depending on a running process.

The label moves from EXCELLENT_MATCH to STRONG_MATCH, which is the correct reading
of a candidate whose report claimed demonstrated clean-code practice on the strength
of the word "scalable".

## What is deliberately not changing

- **Category weights and credits.** Untouched. `ScoringConfiguration` has no edits.
- **The semantic layer itself.** Still the only source of evidence for a prose
  requirement that the vocabulary cannot name, still thresholded, still able to
  reach SUPPORTED when it clears the strong threshold. What changed is that failing
  to clear it now means silence instead of a haircut.
- **Determinism, explainability, the API contract, persistence.** All preserved.
- **The LLM's role.** Still narrative-only. It still cannot reach the score.

---

# Fourth round: the candidate-profile paragraph

This round came from one wrong line on a report: `What We're Looking For`, scored
PARTIAL at 23%. Three of the four reported symptoms turned out to be F12-F15
already fixed, and are listed at the end as verified rather than changed. The
profile section was a genuinely new defect, and it had four independent causes.

The section reads, in almost every posting of this shape:

```
What We're Looking For:
The ideal candidate should be able to understand business requirements, design
technical solutions, write reliable code, troubleshoot problems, and work
effectively across frontend and backend technologies.
```

## F17 - A section heading was scored as a requirement (critical)

`HEADERS` recognised `what you will do` and `what you bring`, but not
`what we're looking for`, `ideal candidate`, `candidate profile` or any of their
spellings. An unrecognised heading does not fall through to nothing - it falls
through to the line classifier, which is built to be generous, and became a
requirement.

The result was a requirement that no resume can ever evidence:

```
"What We're Looking For"  ->  NOT_EXPLICITLY_MENTIONED, 0% confidence
  reported as a gap: "What We're Looking For - not explicitly mentioned in the resume"
  recommended:      "Add a concrete line for What We're Looking For - the job
                     description asks for it and the resume does not mention it."
```

This is the report's worst failure mode in miniature. The candidate is told the
resume lacks something, and advised to go and add it, and there is no edit to
their resume that can ever satisfy it - because the thing missing is a heading in
the employer's document. It also flatters nothing: it can only subtract.

Fix: the labels are registered as headers mapping to the responsibility section,
which is what a candidate profile is - a list of duties. A header with no
remainder produces no requirement.

## F18 - The split depth limit silently deleted a requirement (critical)

`splitRecursively` stopped at `depth >= MAX_SPLIT_DEPTH` and returned the
fragment **unsplit**. The profile sentence nests its connectors more deeply than
three levels: it splits on `" and "`, each side then splits on `","`, and one of
those was still a comma list when the limit hit.

So `"write reliable code, troubleshoot problems,"` arrived as one fragment. The
vocabulary then matched `"troubleshoot"` against that fragment and resolved the
whole thing to `DEBUGGING` - discarding `reliable code`, which was sitting in the
same words. A requirement present in the job description did not exist in the
analysis, and nothing reported the loss.

The depth limit was defending against a real thing - a pathological connector
chain turning into dozens of terms - but it defended by truncation, which is the
one failure mode worse than the one it prevented.

Fix: bound the number of terms instead of the depth. Each accepted split strictly
increases the count, so recursion still cannot exceed the cap, and a fragment is
never returned unsplit. `MAX_TERMS = 8`. The bound is tested
(`aPathologicalConnectorChainIsStillBounded`) because losing the guard entirely
would let a long list manufacture a dozen phantom gaps - the same class of defect
as F17, in the opposite direction.

## F19 - Profile grammar was baked into a requirement's identity (high)

`significantContent` removes JD framing from a phrase's canonical key, and its
stop-word list covered "strong", "solid", "the" and "role" - but not the grammar
of a candidate-profile sentence. So:

```
"The ideal candidate should be able to understand business requirements"
  ->  PHRASE_IDEAL_SHOULD_BE_ABLE_UNDERSTAND_BUSINESS_REQUIREMENTS
```

The concept being asked for is `business requirements`. Seven words of framing
were welded to it. Because a phrase requirement is matched on its own words, those
seven words became seven words of evidence no resume could ever contain: a CV
writing "gathered business requirements from stakeholders" answers the question
plainly and still fails the match.

Fix: the function words of a profile sentence - should, shall, be, been, was,
were, able, can, could, would, may, might, look, looking, ideal, someone, person,
want, willing, ensure - joined the stop-word list. This is F8's defect one level
up: F8 left a leading conjunction in a display name, this left a whole clause in
a canonical key.

## F20 - A phrase requirement was matched only on the employer's full sentence (high)

F19 changed the *key* but not the *pattern*, which made it cosmetic. `toTerms`
built a phrase term's `patterns` from the cleaned display text - the entire
sentence including the framing - while the key came from the content words. The
two must agree, and a requirement whose identity says `business requirements`
cannot have a pattern that requires all twelve words.

Fix: a phrase term is matched on both. The employer's full wording is kept,
because that is the phrasing most likely to appear verbatim in a matching bullet,
and the content words are added, because that is how the requirement is normally
answered.

## What this round changed about the reference pair

Nothing. The reference job description has no candidate-profile section, so F17
does not touch it; and F18-F20 only affect lines whose connectors nest deeper than
three levels, which none of its ten responsibility bullets do.

```
strong_full_stack   89 overall   89.9 required   100 experience
                    72.5 responsibilities   85.6 preferred
```

Identical before and after, in every category. The 30 benchmark scenarios are
unchanged, which is the check that matters here: none of them regressed into a
phantom gap or into a silently dropped requirement.

On the full-stack posting, responsibilities go 12 -> 11: the phantom requirement
from F17 is gone, and the profile paragraph is assessed as the six capabilities
it actually names rather than as one opaque phrase.

## Already fixed, verified rather than changed

Three of the four symptoms in this round's report were the F12-F15 defects, already
fixed. They were re-verified end to end against the full-stack posting and are now
pinned by tests:

| Requirement | Reported | Now | Pinned by |
|---|---|---|---|
| Code reviews + software development activities | PARTIAL 41% | NOT_EXPLICIT 0% | `anUnevidencedCompoundIsNotPartial` |
| Clean, maintainable code | DEMONSTRATED 85% | NOT_EXPLICIT 0% | `genericProseIsNotCleanCode` |
| Artificial intelligence or Machine learning | NOT_EXPLICIT 0% | NOT_EXPLICIT 0% | `pythonAloneIsNotMachineLearning` |

The score no longer moves when Ollama starts or stops: identical in every category
with a noisy semantic index and with none, pinned by
`theScoreIsIndependentOfSemanticNoise`.

## F21 - "debugged" was not a registered form of "debugging" (low)

Found by a new test rather than by a report. `DEBUGGING` carried `debugging` and
`debug` but not `debugged`, and a resume bullet almost always opens with the verb
form - "Debugged production incidents". The word-boundary matcher allows one
trailing plural `s`, which covers `debugging` -> `debuggings` and nothing useful
here. Same class as F7: a real gap that reads as a gap in the report.

## Known limitations after this round

- The profile paragraph is still one conjunction of six terms rather than six
  separate scored requirements. Each term carries its own evidence state and the
  classification is computed from them component by component, so the finding is
  accurate; but the report shows one line, and a reader wanting to know which of
  the six are met has to read the explanation. Making it six requirements would
  change the requirement count, the denominators, and every pinned count in
  `AnalysisEngineRegressionTest` - a bigger change than an accuracy pass should
  make.
- A phrase requirement still cannot be evidenced by a paraphrase. "gathered
  business requirements from stakeholders" matches on the words, and only on the
  words. Semantic evidence can reach SUPPORTED but never DEMONSTRATED, which is
  the intended asymmetry, but it does mean an unusually-worded answer is reported
  weaker than it is.
- `GenerationService.analyze()`, `PromptBuilder.buildUserPrompt`,
  `ResponseParser.parse` and `AnalysisResult.matchScore` remain as dead code. They
  are the last place in the codebase where a model could produce a score, and they
  have no callers. Deliberately left in place until the scoring engine is signed
  off.
