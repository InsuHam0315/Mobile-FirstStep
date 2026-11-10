# App entry module contract

App entry connects the signed-in FirstStep Home to campus browsing. It owns the Home button, the session check at the point of navigation, and the return to account Home. H07 owns authentication and profile verification. Campus content owns the map and facility entry points, the department guide, and its debug-only exploration demo. Account sync, transfer of local or demo records, and field exploration completion are outside this contract.

## Caller and destination

| Boundary | Implemented contract |
| --- | --- |
| Home caller | `IdentityActivity` binds **캠퍼스 둘러보기** (`R.id.game_identity_open_campus`) in `game_identity_home.xml`. The action is available on account Home after `COMPLETE`, outside the profile detail panel. |
| Session input | `public SessionState SessionController.state()` takes no arguments and returns the current state. The button reads it on each tap and uses `public SessionState.Stage SessionState.getStage()`. If an active session's UID no longer matches the controller's profile, `state()` masks the stale profile state. |
| Campus launch | `public static Intent CampusHomeRoute.intent(Context)` takes the caller's Activity context and returns an explicit intent targeting the non-exported `CampusHomeActivity`. It supplies no extras. `startActivity(Intent)` launches it without requesting a result. |
| Back and resume | Campus Home's Back button finishes it. Android Back also returns through the existing stack. `IdentityActivity` resets the launch guard on resume, resumes profile verification and renders the current state. A still-valid session returns to account Home after server confirmation. A new campus launch requires another tap. |

`openCampusHome()` is private. The button is the UI entry for this binding. The production caller is equivalent to the following Java inside `IdentityActivity`, with `controller` referring to its existing `SessionController`.

```java
if (openingCampusHome || controller.state().getStage() != SessionState.Stage.COMPLETE) return;
openingCampusHome = true;
startActivity(CampusHomeRoute.intent(this));
```

For a UI or integration check, first reach account Home with a server-confirmed profile. Tap **캠퍼스 둘러보기** and verify that Campus Home offers the map, facility directory and department guide. On a debug build, open the guide and tap **소프트웨어학과 탐방 시연** to reach the local demo. Back through those screens should return to account Home. Logged-out, missing, saving, failed, and unconfirmed profile states leave the account Home action hidden. The launch guard rejects a second tap before resume.

## Dependencies and data ownership

- The identity controller supplies the session gate and owns UID/profile handling. App entry reads the stage without passing UID, profile values, progress, or storage identifiers. See the [H07 identity handoff](../../harness/handoffs/H07.md).
- Campus content supplies `CampusHomeRoute` and owns the map, directory, and guide destinations. The guide resolves its own packaged content and constructs the registered software DEMO context only through its debug entry. Its progress stays in the local demo store. See the [campus content contract](../campus-content/README.md) and the [H10C demo handoff](../../harness/handoffs/H10C.md).
- The app manifest registers `CampusHomeActivity` in the same package for the explicit intent. Android's launcher opens `IdentityActivity` through the single **첫발자국** icon in debug and release. The legacy `MainActivity` and the debug preview activities remain registered for explicit callers. See [IdentityActivity](../../../app/src/main/java/com/example/firststep/game/ui/identity/IdentityActivity.java), [CampusHomeRoute](../../../app/src/main/java/com/example/firststep/game/composition/home/CampusHomeRoute.java), and the [manifest](../../../app/src/main/AndroidManifest.xml).

Before changing the binding to another destination, check its module contract or current handoff for the Activity component, export status, intent extras, result semantics, and storage owner.

## Confirmed owner event

`public SessionController(AuthPort auth, ProfilePort profiles, ConfirmedOwnerObserver ownerObserver)` accepts a callback alongside the existing ports. `public SessionController(AuthPort auth, ProfilePort profiles)` remains available. The observer is a separate channel from `setListener(Listener)` and receives `void onConfirmedOwner(String uid)` on the controller's serialized calling thread. A non-null value is the UID of a profile that reached `COMPLETE` after a server read. A null value revokes confirmation. Treat repeated values as idempotent, and treat a later confirmation following revocation as a new session even when the UID matches.

The controller emits revocation before sign-in, reselection and logout, when it observes a mismatching or lost session, or when a profile check produces a failed or incomplete result. An ordinary pause and the loading or cached part of a same-UID refresh preserve the last confirmed decision. The existing generation, request and UID checks reject stale profile callbacks.

`IdentityViewModel` supplies `runtime.confirmedOwnerObserver()` to the three-argument constructor. The configured process graph subscribes to auth independently of the controller. Non-null confirmation requires the current port and the same named Firebase app's non-anonymous user to match. Worker operations recheck that SDK user under the owner guard, including before a queued auth callback arrives. Pausing the Activity or closing its view model leaves the graph subscription intact.

### Account progress access

`IdentityRuntime.accountProgress()` returns an optional `AccountProgressAccess`. The configured runtime supplies it. Unconfigured, fake and local runtimes default to an empty value. A future authored composition should capture its dataset and building through `capture(datasetId, buildingId)`, which requires a current confirmed owner and an eligible NORMAL registration. Use `registry()` and `owner()` to construct the bound authored repository. Pass that repository as the committed source to `reconciler(source)` for local recovery on save or reopen. `flush(scope)` is a separate explicit network step. The same registry and guard protect the coordinator, backend and reconciler.

The production registry is empty, and no Activity or authored composition calls this access yet. The graph reuses the named Firestore instance from profile verification and creates no progress reads or writes during setup. See the [account binding scope](../../harness/ACCOUNT_SYNC_BINDING_SCOPE.md), [Firebase module](../firebase/README.md), and [progress sync module](../progress-sync/README.md).

For an explicit fetch action, pass the scope from `capture(datasetId, buildingId)` to `public CompletableFuture<OutboxState> refresh(SyncScope scope)`. The coordinator reads the server and updates the local sync projection without uploading or changing authored gameplay. The caller should recheck the captured scope through `owner().whileCurrent(...)` before applying the result to the current account. The registry rejects ineligible scopes before scheduling work. A stale owner fails the future and cannot apply a delayed result. The campus authored composition owns the future UI action and must keep remote summaries separate from gameplay attempts and rewards. The [readback checkpoint](../../harness/handoffs/APP_ENTRY.md#explicit-account-progress-readback) records the fake backend and retained coordinator checks.

## Implementation and verification

The v7 launcher correction passed merged manifest checks for debug and release, plus a binary manifest and label check on the debug APK. The [launcher verification handoff](../../harness/handoffs/APP_ENTRY.md#single-launcher-entry) records the build and check limits.

The v3 binding uses the campus route from the prepared `8158893` candidate. Its build and source checks are recorded in the [app-entry verification handoff](../../harness/handoffs/APP_ENTRY.md). The unified route still needs runtime validation.

The earlier direct-guide binding at `6fbb872883b6623bb6e6adb6d53d3c3f2f4ff7e0` was integrated into `main` at `982a17c`. Its recorded QA check reached the guide and **DEMO · 이 기기에만 저장**, then returned through Back to confirmed account Home. The earlier APK evidence covers that route. The v3 route adds Campus Home between those screens.
