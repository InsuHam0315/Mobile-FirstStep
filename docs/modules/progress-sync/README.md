# Account progress sync

Status: implemented H07B core and adapters. Combined account/gameplay binding and live validation are pending.

## Entry points

All classes are in `com.example.firststep.game.sync`.

| API | Responsibility |
|---|---|
| `SyncScope(projectId, uid, sessionGeneration, datasetId, mode, buildingId)` | Captures one operation's owner and progress identity. Use the current Firebase project's UID and session generation from the account host. |
| `DatasetRegistry.production()` | Closed authored allowlist. It is empty until an approved authored NORMAL episode is registered. |
| `SessionOwner.whileCurrent(scope, action)` | Host-supplied guard. Admit only the current signed-in, complete-profile owner. Check project, UID and generation together, serialize with session changes, and throw `StaleSession` on mismatch. The guard must be reentrant because the coordinator and adapter each enforce it. |
| `Milestone(introDoneAtMillis, completion)` | Introduction plus an optional passing summary. `Completion(attemptId, contentVersion, correct, completedAtMillis)` carries no quiz answers. |
| `SyncStore` / `SQLiteSyncStore(context, registry)` | Blocking atomic transitions. One dedicated SQLite database stores the visible projection and pending milestone in one row. Close the store after its work drains. |
| `SyncBackend` / `FirestoreSyncBackend(firestore, registry, owner)` | Async read and transactional merge with the supplied SDK instance. Unit tests can provide a fake backend. |
| `SyncCoordinator(registry, owner, store, backend, worker)` | `readLocal`, `record`, `refresh` and `flush` return futures. Use a worker executor for the local store. |

### Future host binding

Use the same registry and owner guard for all three components. The following shows the API shape once an authored dataset is authorized and registered. Today `production()` rejects every scope.

```java
DatasetRegistry registry = DatasetRegistry.production();
SQLiteSyncStore store = new SQLiteSyncStore(appContext, registry);
SyncBackend backend = new FirestoreSyncBackend(accountFirestore, registry, sessionOwner);
SyncCoordinator sync = new SyncCoordinator(registry, sessionOwner, store, backend, worker);

SyncScope scope = new SyncScope(projectId, uid, generation, datasetId,
    Progress.Mode.NORMAL, buildingId);
// Use a milestone derived from committed authored gameplay.
sync.record(scope, milestone).thenCompose(local -> sync.flush(scope));
```

Callers can display the locally committed result before requesting a flush. Recheck the captured scope when rendering future results. Clear the displayed account projection on logout or account switch. In-flight work retains its original document path. A stale result cannot acknowledge local progress under another session.

## Local and remote contract

- Local key: project + UID + dataset + mode + building in `firststep-account-progress-sync.db`. Session generation gates operations but does not split durable storage. A fresh session for the original UID can retry its pending row.
- Remote path: `users/{uid}/progressDatasets/{datasetId}/modes/{mode}/buildings/{buildingId}`. The supplied Firestore instance must match the project's ID. Schema v1 contains `schemaVersion`, `introDoneAtMillis` and nullable `completion` with the four summary fields above.
- `record` commits the pending revision and effective milestone atomically in this store. `flush` attempts one pending revision. Acknowledgement clears it only if the server state covers the submitted milestone and the revision still matches.
- Merge keeps the earliest introduction and the first accepted passing summary. Duplicate or reordered records cannot erase a pass. `refresh` reads server data and preserves local pending work. Cache and SDK-pending snapshots cannot confirm server state.
- `OutboxState` exposes `confirmed`, `pending`, `effective()` and a status. Retryable errors preserve pending data for an explicit flush. Permanent failures stop flushing that revision until a meaningful new milestone is queued. There is no background retry scheduler or cloud delete API.
- Progress summaries do not become local quiz attempts. Current H06 fixtures, software DEMO, guide/practice and legacy data are ineligible, including fixture records that happen to be labeled NORMAL.

## Dependencies and integration gates

Uses Android SQLite, existing Firebase Firestore and the existing NORMAL timing constant. The adapter has no Firebase initialization, account selection or route registration. Account composition still needs to supply a reentrant session guard, the matching Firestore instance and an authorized authored registry. Gameplay composition must emit committed milestones reliably. Combined validation must exercise the delivery boundary between the gameplay database and this separate outbox.

The Android Java compilation and 12 focused JVM contract tests passed. Tests cover scope admission, merge ordering, stale callbacks, explicit retry, the versioned payload codec and reopening pending bytes through a fake store. Actual Android SQLite restart behavior, Firestore SDK transactions against an emulator or live service, deployed rules, account Home and cross-device readback remain pending. See [H07B handoff](../../harness/handoffs/H07B.md).

SDK references: [FirebaseFirestore](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore), [Transaction](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Transaction), [Source](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Source).
