# H07B preparation — not a dispatch

2026-11-09. Prepared by management with a read-only implementation PM audit while H07 account-home validation runs. User requested useful parallel V1 work and continued authorized engineering. Existing native workers suffice for this bounded independent planning/review; no additional user chat, branch, app edits or parallel dependent implementation was created. H07 live acceptance and integration remain prerequisites. Reconcile this proposal with actual main before issuing H07B_SCOPE.md.

## Proposed technical boundary

Use the existing firebase branch/journal after accepted H07. Isolate every operation, local cache and pending write by Firebase project, UID, authored dataset, mode and building. A possible Firestore path is users/{uid}/progressDatasets/{datasetId}/modes/{mode}/buildings/{buildingId}. Capture UID plus session generation for callbacks; detach old listeners and clear the visible projection on account changes. Pending operations stay owned by their original identity.

Explicitly register sync-eligible authored NORMAL gameplay datasets. H06 fixture data remains excluded even when labeled NORMAL. Exclude H10 practice, DEMO, legacy and anonymous databases. Never fabricate a quiz result to hydrate a cloud completion: existing Progress invariants require a passing result behind a stamp, so define a distinct validated remote projection or complete compatible contract.

Proposed conflict policy: atomic local milestone plus outbox, idempotent per-building transaction, earliest valid introduction timestamp and first accepted completion retained. Delayed incomplete writes must not erase a completion. Show pending, server-confirmed and retryable failure separately. Keep unfinished answers/attempt history local. Do not add cloud reset/delete in this slice; its offline resurrection semantics would need a separate decision.

## Proposed implementation and evidence

Bound ownership to account progress adapters, authored composition, authenticated Home sync presentation, Firestore rules and focused tests. Preserve current identity/profile, H10 and legacy routes. No billing, Functions, photos or RAG.

Test unauthenticated/cross-UID denial, late account callbacks, rejected fixtures/modes, duplicate/competing writes, offline restart/reconnect and cross-instance readback. Synthetic test datasets use an isolated Firebase emulator project. Live acceptance requires an approved authored episode producing an acknowledged cloud record and restoring on another instance. An emulator rules pass alone is not live Google/Firestore evidence.

## Genuine remaining dependency

There is currently no sync-eligible authored gameplay episode. H10 provides four sourced claims and three practice questions; it explicitly writes no game progress. H10B now independently authors a narration preview from those facts. Re-reading PDF pages 1-4 confirms the fairy framework and digital-information SHAKE action were already specified; do not re-ask these choices. Final episode text/art and surveyed safe arrival point/radius remain unfinished. Do not silently convert practice to rewarded gameplay. Later college/department destinations are separate choices.

Firebase selection, UID partitioning, retry implementation and routine tests do not need another user approval. Cross-device drafts or cloud reset need a decision only if added. Preparation does not authorize deploying rules, importing data, implementing H07B before H07 acceptance, or claiming a complete V1.
