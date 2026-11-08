# H10 / campus-content: sourced local department guidance

## Anchor and purpose

Branch `campus-content`, worktree `C:/Users/InsuHam/FirstStep-worktrees/h10-campus-content`, based on current clean main containing accepted H06. H07/firebase remains at candidate d05dd6b, configuration-waiting and unaccepted; do not edit, merge or resume that worktree. User authorized useful independent first-version work while setting up Firebase. H10 is the sole active implementation slice. Read AGENTS.md, ROUTING.md, UI_BOUNDARIES.md, BRANCH_COMMITS.md and SOFTWARE_CONTENT_SOURCES.md; do not replay historical H01 work.

Deliver one real APK entry containing the sourced software-department introduction, references and three-question practice. No login/cloud dependency. This is a required content foundation, not an authored field mission or whole-game completion.

## Implementation boundary

1. Bundle a versioned `software_guide_v1` dataset under a new assets namespace. Model source ID/official HTTPS URL/checked date, claim provenance, introduction and quiz questions/answer IDs. Keep creative connective text distinct from supported facts. Coordinates remain absent/unverified; do not invent room numbers, missions or campus facts.
2. Load only a registered built-in dataset through typed Java contracts; external intents cannot choose arbitrary assets/files/databases. Reject malformed required types, duplicate IDs, missing claim/source links, invalid answers, unknown verification states, unsupported versions and unsafe URLs. Failure displays a readable recoverable unavailable state, never fixture/default fallback. Keep scope small; no generic CMS or remote importer.
3. Add a visible “소프트웨어학과 안내·연습” entry from the existing runnable legacy/main home. A minimal H05-home entry is permitted if useful, without changing its context/progress behavior. Dedicated Java/XML presentation must be replaceable without changing content/quiz rules. Do not change the launcher to the unmerged H07 flow. Stable content/questions, readable source labels and explicit practice/unverified-location labels must fit small screens and large fonts.
4. Show introductions, official references (safe ACTION_VIEW with missing-handler handling), then answerable three-question practice with correct/incorrect explanations and retry. Use approved copy from SOFTWARE_CONTENT_SOURCES.md. No new TTS/sensor feature is required. Preserve in-flight practice selection/result through normal recreation; no permanent personal/progress storage is needed.
5. Do not open or write any progress/profile/legacy DB or cloud document for this entry. No stamp, arrival, notification, 15-minute timing override or completion/promotion into H06 or a future account. H06 fixture assets, validators, hosts and legacy exploration continue unchanged. Reuse pure contracts only where semantically appropriate; do not fork or weaken accepted game rules to make practice pass.

## Ownership

Implementation lead owns `app/src/main/java/com/example/firststep/game/content/`, `game/ui/guide/`, new `app/src/main/assets/campus_content/`, scoped resource/layout additions, new tests and the minimal MainActivity/manifest/optional H05 host entry. Lead coordinates any shared file edits. Workers are not alone; preserve others' changes. No build dependency additions unless clearly necessary; Java/XML only. Management owns project-wide rules, tasks main, source packet, Notion, pushes and integration. Implementation may add the English H10 handoff and scoped verification evidence, not revise product decisions. Use bounded Sol PM -> Terra/Luna workers and independent review as required by ROUTING.

## Acceptance and evidence

- Trace every factual introduction and quiz answer/explanation to approved claims/sources; no raw copyrighted page copies. Source metadata and unknown arrival status are visible.
- Meaningful loader/domain tests cover malformed types, broken provenance, duplicate/answer IDs, unsupported version/status, safe URL/registered dataset handling and score/retry/recreation behavior where appropriate. Correct all three answers by stable IDs; wrong answers remain retryable without rewards.
- On an actual emulator APK, open guide from home, read all text and references, complete correct and incorrect practice, retry, rotate/recreate, use small portrait and large-font landscape. Verify scrolling/button accessibility and graceful offline/missing-browser behavior. Bundle works offline; opening a live source can require connectivity. Label simulated inputs separately; official content is sourced, not campus-tested.
- Demonstrate entry does not create/change existing progress/profile/legacy records and no fixture promotions; preserve existing data and compare DB evidence around the focused flow. Existing H06 technical path remains runnable and progress isolation unchanged.
- Run `scripts/verify.ps1` with Android Studio JDK (unit, assembleDebug, lintDebug), plus relevant focused device tests; report actual unique tests/executions, cached versus fresh work and existing warning count. No blanket claims about device or Google verification.
- Provide exact code/doc commits, source hash/base/worktree, file list, tests/screenshots/evidence paths, independent reviewer findings and remaining limits in `docs/harness/handoffs/H10.md`. Korean versioned commits `[campus-content vN] ...`; do not push, merge main or update Notion.

## Stop and next gate

Stop after the bounded candidate/handoff for management review. Authored mission/story/sensor rationale, surveyed arrival, authenticated progress integration, other departments and H07 live checks are not completed by this slice. Management re-evaluates the next eligible work after acceptance; no automatic optional backlog dispatch.
