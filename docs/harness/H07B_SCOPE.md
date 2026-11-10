# Account progress sync — implementation-first local slice

2026-11-10. Authorized product: Firebase-backed exploration progress. Current IMPLEMENTATION_FIRST.md allows implementation with explicit contracts before human live acceptance. H07B_PREPARATION.md supplies the conflict/isolation rationale. This dispatch implements the independent synchronization core/adapters, not deployed live sync.

## Exclusive owner

Management's existing implementation PM coordinates one Terra implementer in C:/Users/InsuHam/FirstStep-worktrees/progress-sync, branch progress-sync, base228a088bdec3c8b1c34f8240c8b7f9a7c5868275. Actual branch journal is progress-sync. Original H07 chat exclusively owns app-entry identity binding; campus-content chat owns campus navigation. No concurrent edits in either worktree.

Write only additive game/sync packages, associated focused unit tests, optional isolated test-rules artifacts under firebase/progress-sync/, and handoffs/H07B.md. Do not edit identity/profile classes, navigation, shared manifest/resources/Gradle, existing domain classes, production rules or existing profile tests. Use existing Firebase dependencies. If a required interface cannot be additive, report its exact signature and owner to management first.

## Implemented contract

- Explicit immutable project/UID/session-generation/dataset/mode/building context. Session ownership comes from a port; no account selection, profile access or credentials. Reject invalid context before storage/backend calls.
- Dataset eligibility is explicit: authored NORMAL only, registered eligible targets; DEMO, H06 fixture, H10 practice, legacy and anonymous records always excluded. Production has no enabled eligible dataset until the authored composition is independently supplied. Tests use synthetic registrations. Never relabel current software_episode_demo_v1 as NORMAL.
- Durable local milestone/outbox write is atomic within this new namespace; reuse existing Android SQLite/Room facilities only without changing existing schemas or opening any legacy/account database. Separate local pending/confirmed/failure projection; remote completion does not fabricate a local passing quiz attempt.
- Idempotent per-building merge: earliest valid introduction time and first accepted completion survive duplicate/reordered/retried writes; incomplete cannot erase completion. No cloud reset/delete, draft answer upload, leaderboard, photos or extra services.
- Firestore adapter uses the current existing SDK, a supplied FirebaseFirestore instance and captured ownership context. Suggested path users/{uid}/progressDatasets/{datasetId}/modes/{mode}/buildings/{buildingId}. Do not initialize the user's configured project or deploy rules during this slice. Reads/writes/listeners must invalidate stale sessions and remain owned by original context.
- Backend interface supports fake/local implementation for deterministic tests. Pending work survives local restart and may resume only for its original account/session ownership policy, never reassigning to a new UID. Define retryable versus permanent failure and allow bounded explicit retry; no uncontrolled background loop.

## Short checkpoint evidence

Compile affected code and run focused contract tests covering UID/project/dataset isolation, forbidden datasets/modes, late callback/session switch, duplicate/reordered completion, offline/retry/outbox restart. Add targeted local storage instrumentation only if necessary; do not run long historical suites. Capture exact commands/counts/reused results honestly. Record adapter/local tests versus unrun live cross-device behavior separately.

No google-services copy, actual account read/write, Firebase console/rules deployment, billing, device actions, new service or real dataset import. Existing configured devices remain user/other-owner controlled. Integration binding to account Home and real authored gameplay is a later management-owned combined step, not a fake-success UI.

Korean [progress-sync vN] commits, English H07B handoff. No push/merge/Notion by implementation. Stop at a coherent implemented checkpoint pending combined validation, with exact wiring and external-validation gaps described.
