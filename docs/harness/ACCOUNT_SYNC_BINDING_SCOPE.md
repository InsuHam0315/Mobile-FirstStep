# Account ownership and durable gameplay delivery

2026-11-10. Continuation of approved required Firebase progress implementation under IMPLEMENTATION_FIRST.md. Baseline934d9b0 plus management checkpoint docs. This is actual missing wiring implementation, not a live service deployment or a claim of whole-app acceptance.

## Parallel ownership

1. Original authorized identity chat, existing app-entry worktree/branch: additive backward-compatible confirmed-owner observer in SessionController and its tests, followed by named Firebase graph binding once sync owner publishes the exact contract. Own identity/auth and identity composition only plus own module doc/handoff. Preserve UI listener, sign-in/profile behavior and configured device sessions. No edits to sync or authored repository files.
2. Management implementation PM and Terra specialist, existing progress-sync worktree/branch: ConfirmedSessionOwner, CommittedMilestoneSource, MilestoneReconciler and an isolated authored NORMAL gameplay repository under game/composition/authored. Own corresponding focused tests and own module contract/handoff. No edits to identity classes/shared resources/manifest/Gradle/production rules.

Owners are not alone. Do not revert others' work. Management integrates checkpoints and coordinates interface changes. Use current bare module branches and increasing Korean commits. No push/merge/Notion by owners. No devices, cloud/account actions, credentials copying, rule deployment, billing or optional services.

## Account confirmation contract

Identity adds a ConfirmedOwnerObserver port with onConfirmedOwner(String uid), where null means revoked, and a backward-compatible SessionController constructor overload. Publish a non-null UID only after server-confirmed complete profile. Publish revocation before logout/reselection, mismatching auth, failed or lost confirmation. Ordinary Activity pause/child navigation must not itself revoke an otherwise confirmed owner. Existing stale SDK-response guards remain authoritative. Observer callbacks may occur repeatedly; the sync owner must be idempotent for unchanged confirmation. Same-UID reauthentication starts a fresh generation after revocation.

Sync implements a reentrant synchronized ConfirmedSessionOwner with explicit project identity; onAuthChanged(uid), confirm(uid), revoke(), capture(dataset,mode,building) and whileCurrent(scope,action). Authentication identity and confirmed profile identity are distinct. Capture rejects incomplete/unconfirmed/mismatching owners. Actual named Firebase graph observes auth independently of Activity pause and invalidates old sessions before admitting a new one. This graph binding is the identity owner's second step after consuming the implemented sync contract; do not declare the end-to-end binding complete before it exists.

## Durable source and reconciliation

Create a separate account/project/dataset/mode/building-owned NORMAL repository compatible with the existing ProgressRepository interface. In one local transaction commit the gameplay aggregate and actual first-pass commit timestamp. Retain first accepted completion and existing domain invariants. Never read/migrate H06, DEMO, practice, legacy or anonymous files. Require explicit eligible registration before opening a namespace; production registry remains empty. Synthetic registrations are test-only.

Expose CommittedMilestoneSource.latest(SyncScope) from the committed row and a MilestoneReconciler.reconcile(scope) future: validate registry/current owner before source access, recover committed milestone into the sync outbox after save and on reopen, idempotently. Flush is a separate explicit operation. Do not rely only on a post-save callback, because a crash can occur before enqueue. This source is the durable recovery mechanism across the two databases.

Record actual timestamps. If wall-clock rollback makes a legitimate local pass incompatible with sync validation, preserve local progress and expose a recoverable/non-success synchronization state; never forge the timestamp or silently drop completion. No remote pass is synthesized as a local quiz attempt.

## Checkpoint checks and pending limits

Short focused compilation/unit checks for confirmation/revocation, same-UID reauthentication, A-to-B switching, navigation pause, stale callbacks; atomic store rollback; replay after the gameplay-commit/outbox gap; duplicate recovery; original-account ownership; clock rollback. Real Android SQLite restart and service integration may remain explicitly pending under the combined gate. Do not substitute a fake-store test claim for actual SQLite execution.

Document exact public APIs/callers and what is actually wired. Real authored NORMAL dataset activation/field arrival remains gated on verified coordinates and registration. No promotion of software_episode_demo_v1. The existing sourced narration/questions may inform future NORMAL content; their presence is not surveyed arrival evidence. No live sync/rules deployment or cross-device success claimed. Latest teammate design remains last.
