# Main shell and deferred profile

`IdentityActivity` remains the single launcher. It now displays the existing `game_reskin_artwork` immediately with the university/FirstStep title, **탐방 시작하기**, and an optional Google button. Its window background uses the same artwork while the Activity surface is unavailable. A guest starts directly into Home. An authenticated UID opens Home before profile loading or completion; a canceled sign-in leaves browsing available. No profile save is triggered by sign-in or entering Home.

The three bottom tabs are **홈 · 지도 · 마이페이지**, in that order. Management supplied this menu evidence from the design PDF's pages 1 and 5; this implementation did not independently inspect that PDF. The UI reuses existing art, icons, cards, Google button styling, and visual tokens. Exploration is a Home action, not a fourth tab.

## Screen and route contracts

- `IdentityActivity.tabIntent(Context, boolean myPage)` returns to the existing shell from Map using `CLEAR_TOP | SINGLE_TOP`. It sends only a tab choice, never UID/profile/progress values. Saved tab state takes precedence over an old navigation intent on recreation.
- `RouteMapActivity.mainBrowseIntent(Context)` uses the approved browse-only map and exposes the same three tabs. Existing browse/facility/legacy callers retain their own behavior; the navigation is hidden for those callers. Location is still an explicit map action, with existing permission and freshness handling. No destination coordinate or legacy mission is fabricated.
- `CampusChoice` accepts bounded college/department strings and resolves only exact registered pairs: `컴퓨터소프트웨어특성화대학` + `소프트웨어학과` → software; `인문콘텐츠융합대학` + `국어국문학과` → Korean. Empty, unknown or mismatched pairs return `Guide.NONE`. They cannot open software through a fallback.
- `LocalCampusChoices(Context)` owns a new device-local preferences file. `load(uidOrNull)` and `save(uidOrNull, choice)` isolate guest and encoded account namespaces. No automatic mapping, migration or guest-to-account copying occurs. Its methods are synchronous and run on the view model's worker.
- `saveGuest(Profile, CampusChoice)` validates all bounded fields and matching department before writing the three guest profile fields and two choice fields in **one** preferences edit/commit. `loadGuestProfile()` reads the optional local guest profile. Older helper APIs remain compatible; the shell uses the atomic method.

Home opens the actual existing `GuideActivity` only after an explicit saved matching choice. Software uses its original default dataset; Korean uses its existing fixed action. The separate Campus Home browsing route and its existing guide/demo controls remain accessible. Their source assets and gameplay implementations are unchanged. Main Home reuses the existing account-progress reader/controller and its independent owner guard; field registration is still closed in this base.

## My Page and identity boundary

My Page offers name, student number, college and department. Leading-zero student numbers remain strings. Guests may save incomplete optional profiles locally. For an account, name/student number/department use the existing profile port and server schema; college and the explicit routing choice remain device-local and account-scoped. The screen states those different storage scopes. A local choice can be saved even if the separately requested server profile update fails; this never confirms an account or promotes a record.

`SessionController.beginEdit()` explicitly opens a previously server-confirmed profile for editing. `edit(...)`, `save()` and retry retain current UID/generation guards. Editing revokes confirmed progress ownership. `FirestoreProfilePort.save()` now supports the explicitly requested update of an existing valid profile, preserves `createdAt`, and writes only the existing allowed fields. `COMPLETE` and confirmed-owner access still require the exact submitted profile from a current **SERVER** result; a cache, incomplete profile, old server value, failure, or local choice cannot grant that access. No Firebase rules/schema deployment is part of this module.

`IdentityViewModel` retains unsaved guest/college drafts across Activity recreation, fences asynchronous local callbacks by owner generation and keeps account and guest state separate. Its pending-write counter is exposed through a read-only Activity test seam so fixture restoration can wait for actual preference commits after Activity close.

## Validation and current limits

JVM coverage includes exact routing/bounds (`CampusChoiceTest`) and explicit editing/server/UID protections (`SessionControllerTest`). Android cases cover local persistence/atomic rejection, optional sign-in and cancellation, deferred profile, save/retry/cache handling, recreation, map/tab return, actual software dataset/target, unsupported choice and existing Korean paths. Tests use fake identity or explicitly prepared local SDK fixtures. They are compiled, **not executed** by this implementation run.

The final Studio-JBR gate passed 239 JVM tests (zero failures/errors/skips), `assembleDebug`, and `lintDebug` (zero errors, 190 warnings). `assembleDebugAndroidTest` passed. The merged debug manifest retains one launcher. The H07 runner's renamed tests/counts and screenshot capture were aligned and statically checked; it was not invoked. Runtime/UI, configured Google sign-in, live profile updates, native OS splash appearance, enlarged layout and real-device behavior remain unverified. Management owns isolated runtime acceptance. See the [English handoff](../../harness/handoffs/HomeRedesign.md) for exact hashes, logs, changes and review repairs.

The branch is `home-redesign`, base `8a77d02`. Per latest direction, source remains unstaged and uncommitted; no publication or integration was performed.
