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
- The app manifest registers `CampusHomeActivity` in the same package for the explicit intent. The existing identity and legacy launchers remain entry points. See [IdentityActivity](../../../app/src/main/java/com/example/firststep/game/ui/identity/IdentityActivity.java), [CampusHomeRoute](../../../app/src/main/java/com/example/firststep/game/composition/home/CampusHomeRoute.java), and the [manifest](../../../app/src/main/AndroidManifest.xml).

Before changing the binding to another destination, check its module contract or current handoff for the Activity component, export status, intent extras, result semantics, and storage owner.

## Confirmed owner event

`public SessionController(AuthPort auth, ProfilePort profiles, ConfirmedOwnerObserver ownerObserver)` accepts a callback alongside the existing ports. `public SessionController(AuthPort auth, ProfilePort profiles)` remains available. The observer is a separate channel from `setListener(Listener)` and receives `void onConfirmedOwner(String uid)` on the controller's serialized calling thread. A non-null value is the UID of a profile that reached `COMPLETE` after a server read. A null value revokes confirmation. Treat repeated values as idempotent, and treat a later confirmation following revocation as a new session even when the UID matches.

The controller emits revocation before sign-in, reselection and logout, when it observes a mismatching or lost session, or when a profile check produces a failed or incomplete result. An ordinary pause and the loading or cached part of a same-UID refresh preserve the last confirmed decision. The existing generation, request and UID checks reject stale profile callbacks.

The new observer is available to a caller that supplies it at construction. The current identity view model still uses the two-argument constructor. The next composition step needs the named Firebase graph to observe auth changes independently of Activity lifecycle and supply the sync owner. Until that wiring exists, paused controllers only detect an auth change when they resume. See the [account binding scope](../../harness/ACCOUNT_SYNC_BINDING_SCOPE.md), [Firebase module](../firebase/README.md), and [progress sync module](../progress-sync/README.md).

## Implementation and verification

The v3 binding uses the campus route from the prepared `8158893` candidate. Its build and source checks are recorded in the [app-entry verification handoff](../../harness/handoffs/APP_ENTRY.md). The unified route still needs runtime validation.

The earlier direct-guide binding at `6fbb872883b6623bb6e6adb6d53d3c3f2f4ff7e0` was integrated into `main` at `982a17c`. Its recorded QA check reached the guide and **DEMO · 이 기기에만 저장**, then returned through Back to confirmed account Home. The earlier APK evidence covers that route. The v3 route adds Campus Home between those screens.
