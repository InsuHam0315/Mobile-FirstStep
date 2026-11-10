# Account progress sync

The H07B core, adapters, confirmed-owner guard and durable gameplay reconciliation are implemented. The identity graph connection, authored registration and runtime validation are pending.

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

## Confirmed owner and committed gameplay

- `ConfirmedSessionOwner(String projectId)` is the reentrant `SessionOwner` implementation. Feed `onAuthChanged(uid)` from the identity graph's auth observer, and call `confirm(uid)` only after the identity controller's stale-response checks accept a server-confirmed profile. Those checks must reject old confirmations even after the same UID signs in again. Map loss of confirmation to `revoke()`. An ordinary Activity pause has no owner event. Repeated unchanged events preserve the epoch. Revocation, a changed UID or a new confirmation invalidates older captures.
- `capture(datasetId, mode, buildingId)` returns a `SyncScope` for the current confirmed owner or throws `SessionOwner.StaleSession`. Capture does not register the dataset. `DatasetRegistry` checks eligibility before the repository or reconciler accesses storage.
- `SQLiteAuthoredProgressStore(context, registry)` opens `firststep-authored-gameplay.db` lazily. Its composite key is project, UID, dataset, mode and building. Each transaction saves the `Progress` aggregate and observed first-pass commit timestamp together. The gameplay host owns the store lifetime.
- `AuthoredProgressRepository(registry, owner, scope, authoredStore, clock)` implements `ProgressRepository` and `CommittedMilestoneSource`. The repository is bound to one building and captured account epoch. Supply it to `ProgressService`, and rebind after the owner epoch changes. Its `readAll` covers only the bound building.
- `MilestoneReconciler(registry, owner, source, sync, worker).reconcile(scope)` reads a committed milestone, then enqueues it through the coordinator. Use it after a successful gameplay save and when reopening gameplay. A crash between those actions leaves the source record recoverable. Replay after an enqueue is idempotent. Call `flush` separately when appropriate.
- The first-pass timestamp records the actual observed commit time. A clock rollback can make the milestone fail validation even though H02 already allowed the quiz attempt. The repository keeps the local pass and timestamp, and `latest` reports `UnusableMilestone` without replacing pending sync data. The caller can surface this failure separately from gameplay completion.

## Dependencies and integration gates

Uses Android SQLite, existing Firebase Firestore and the existing NORMAL timing constant. The identity composition must connect the existing named app's auth observer and confirmed-profile events to the guard, then supply its matching Firestore instance. The gameplay composition must open a separately registered authored NORMAL episode, use the authored repository and invoke reconciliation on save and reopen. Production registration remains empty until approved authored content and arrival data are supplied.

Android Java compilation and 22 focused JVM tests passed. These cover the original sync contracts plus confirmation and revocation, authored transaction rollback in a fake store, crash-gap replay, stale reconciliation and clock rollback. Actual Android SQLite restart behavior, Firestore SDK transactions against an emulator or live service, deployed rules, account Home and cross-device readback remain pending. See [H07B handoff](../../harness/handoffs/H07B.md).

SDK references: [FirebaseFirestore](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore), [Transaction](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Transaction), [Source](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Source).

## Local Firestore rules candidate

The isolated candidate retains the existing profile rules and adds progress document access on the backend's versioned path. Individual reads and writes require a matching UID, a Google sign-in provider claim, a complete stored profile, NORMAL mode and an enabled server registration for the dataset and building. Clients cannot read or write the registration collection. Missing, malformed or disabled registrations close access. The rule accepts duplicate milestones, an earlier introduction time and the first passing summary. Later updates must preserve the first pass. List and delete are denied.

Local tests use the separate demo project and synthetic accounts. A registered document and a valid client summary establish schema and access eligibility. GPS, sensor readings, elapsed real time and quiz correctness remain outside this rule's evidence. Deployment and production registration require separate approval. [Candidate rules](../../../firebase/progress-sync/firestore.rules) · [Verification handoff](../../harness/handoffs/PROGRESS_RULES.md)
