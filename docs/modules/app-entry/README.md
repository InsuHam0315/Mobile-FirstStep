# App entry module contract

App entry connects the signed-in FirstStep Home to the Software Department guide. It owns the Home button, the session check at the point of navigation, and the return to account Home. H07 owns authentication and profile verification. The guide owns its content, practice, and debug-only exploration entry. Account sync, transfer of local or demo records, and field exploration completion are outside this contract.

## Caller and destination

| Boundary | Implemented contract |
| --- | --- |
| Home caller | `IdentityActivity` binds `R.id.game_identity_open_guide` in `game_identity_home.xml`. The action is available on account Home after `COMPLETE`, outside the profile detail panel. |
| Session input | `public SessionState SessionController.state()` takes no arguments and returns the current state. The button reads it on each tap and uses `public SessionState.Stage SessionState.getStage()`. If an active session's UID no longer matches the controller's profile, `state()` masks the stale profile state. |
| Guide launch | `android.app.Activity.startActivity(Intent)` takes an explicit `Intent` targeting the non-exported `com.example.firststep.game.ui.guide.GuideActivity`. It returns `void`; Home expects no result. The route has no required action, URI, extras, or result payload. |
| Back and resume | Guide's close button finishes that activity. Android Back also returns through the existing stack. When `IdentityActivity` resumes, it resets the launch guard, resumes profile verification and renders the current state. A still-valid session returns to account Home after server confirmation. |

`openGuide()` is private. The button remains the public UI entry for this binding. The production caller is equivalent to the following Java inside `IdentityActivity`, with `controller` referring to its existing `SessionController`.

```java
if (openingGuide || controller.state().getStage() != SessionState.Stage.COMPLETE) return;
openingGuide = true;
startActivity(new Intent(this, GuideActivity.class));
```

For a UI or integration check, first reach account Home with a server-confirmed profile. Tap **소프트웨어학과 안내 열기** and verify `GuideActivity` opens. On a debug build, **소프트웨어학과 탐방 시연** opens the local demonstration. Back from DEMO returns to the guide, and Back from the guide returns to the existing identity activity. Logged-out, missing, saving, failed, and unconfirmed profile states leave Home hidden. A tap during an in-flight launch does not open a second guide.

## Dependencies and data ownership

- The identity controller supplies the session gate and owns UID/profile handling. App entry reads the stage without passing UID, profile values, progress, or storage identifiers. See the [H07 identity handoff](../../harness/handoffs/H07.md).
- The guide resolves its own packaged content. Its debug button constructs the registered software DEMO context and opens the demo screen with local storage chosen by the guide. This is a second, separate intent. See the [H10C demo handoff](../../harness/handoffs/H10C.md).
- The app's manifest must register `GuideActivity` in the same package for the explicit intent. The existing identity and legacy launcher declarations were preserved. Source references are [IdentityActivity](../../../app/src/main/java/com/example/firststep/game/ui/identity/IdentityActivity.java), [GuideActivity](../../../app/src/main/java/com/example/firststep/game/ui/guide/GuideActivity.java), and the [manifest](../../../app/src/main/AndroidManifest.xml).

Before changing the binding to another destination, check its module contract or current handoff for the Activity component, export status, intent extras, result semantics, and storage owner. No separate public guide router or cloud progress API is part of this contract.

## Implementation and verification

The binding was implemented in `app-entry` commit `6fbb872883b6623bb6e6adb6d53d3c3f2f4ff7e0` and integrated into `main` at `982a17c`. The configured build passed 158 unit tests and lint with zero errors. On QA emulator `5572`, a data-preserving update returned the existing session to server-confirmed Home. The actual APK opened the guide, reached **DEMO · 이 기기에만 저장**, and returned to the guide and account Home through Back. See the [app-entry verification handoff](../../harness/handoffs/APP_ENTRY.md) for the APK hash, screenshots, and check results.

The QA run stopped before discovery or stamp award. The release build's entry UI, full gameplay, combined validation, and H07's remaining live account scenarios are outside this verification result.
