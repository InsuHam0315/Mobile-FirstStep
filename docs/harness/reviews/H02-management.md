# H02 management acceptance review

Date: 2026-11-06 (Asia/Seoul). Verdict: **CHANGES_REQUIRED**. Integration and H03 advancement are blocked until the finding is repaired and independently re-reviewed.

## Candidate and evidence

- Base: 346f146b3c51ac86a9d6467ae59a925640d0e034.
- Implementation: 3916bbf77e03efb442d427207c45eab622e3498c; evidence tip: 8d7118f404641d12fae9486c838de5917de76e42, identical app tree.
- Retained app/test patch SHA256: 238F886E442A51D41D631602ACF22AEA5269FCAC5DB3E47CFDC607AFFB0CCC68.
- Worktree: C:/Users/InsuHam/FirstStep-worktrees/h02-progress.
- Retained final unit XML: 52 tests, zero failures/errors/skips. API35 instrumentation: five successes/no skips. Legacy DB and WAL before/after hashes match. These successful scoped checks do not cover the malformed-type issue below.
- Management prepared an uncommitted, no-conflict integration candidate from main690a51f and feature8d7118f in C:/Users/InsuHam/FirstStep-worktrees/management-h02-integration. App tree identical to feature. Fresh Studio-JDK scripts/verify.ps1 succeeded in13s with51/51 tasks executed; parsed XML52 tests0failures/errors/skips, lint0errors/fatal and94existingwarnings. Logs: .local/harness/H02-integration/{verify.log,totals.json}. No management instrumentation rerun claimed. Do not mistake this worktree for accepted/main integration.

## P2: malformed JSON values are coerced and overwritten

GameJson.java:142 uses JSONObject.getBoolean, accepting string booleans. getString calls at120/130/134-135 also coerce non-string IDs/answers into strings. Android SDK org/json/JSON.java confirms these coercions. RoomProgressRepository.java:59-63 re-encodes and saves a decoded aggregate during atomicUpdate, including identity updates, so malformed stored bytes can be silently normalized and overwritten instead of rejected and preserved.

Required repair: check actual Boolean/String runtime types on raw values throughout the catalog/progress decoder, including nested snapshots and answer values. Reject malformed types before a transaction writes. Add focused read/identity-update tests for string booleans and numeric/object/null string fields, verifying the original payload remains unchanged. Preserve existing successful snapshot/retry/idempotence/isolation behavior.

Management requested this bounded repair in the authorized implementation chat. Implementation must provide new revision(s), affected checks, required Gradle checks and independent final-patch review. Management will re-evaluate acceptance and integration afterward; no H03 dispatch yet.

## Review ownership and limits

Management reused verification_pm (Sol PM) for read-only acceptance review; its Terra storage specialist independently confirmed the finding. Neither edited the candidate or ran devices/builds. The reviewers found no additional actionable blocker in the scoped domain rules, snapshots, idempotence, catalog size or ending logic. Missing new UI, authentication, production composition and campus content remain stated out-of-scope work, not proof of a complete app.
