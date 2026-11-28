# Home redesign implementation and verification

Worktree: `C:/Users/InsuHam/FirstStep-worktrees/home-redesign`. Branch: `home-redesign`. Base and current HEAD: `8a77d02cc6df42da12441dc41a04d05cd2eff065`. This is implementation/verification only: **no staging, implementation commit, push, merge or main integration**. The exact reviewed working patch is identified below. Other worktrees, including `department-content`, were outside this assignment.

## Goal and resulting behavior

The launcher directly displays the existing university campus artwork and FirstStep title, Start, and optional Google sign-in. The same artwork backs the Activity window and paused/loading surface. Guests enter Home without identity. Successful authentication enters Home at UID recognition, before the profile read completes and without mandatory onboarding. Cancellation keeps browsing available.

Home, Map and My Page are the three bottom tabs in that order, as relayed by management from design PDF pages 1 and 5. Exploration is a Home action. The main map entry reuses the actual approved browse-only map with its attribution, catalog, explicit location request and existing permission handling. Returning from Map targets the existing shell. Current tab, entered state and drafts survive configuration changes. The native OS splash and rendered layout have not been demonstrated in this run.

My Page supports optional guest-local name/student/college/department and explicit account profile editing. The guest's profile and matching choice are validated and saved together. Account name/student/department use the existing Firestore schema only on explicit Save; college and routing choice stay device-local in the selected account namespace. Signing in does not copy guest fields or local/demo progress into an account. Reads do not overwrite saved profiles, and merely bypassing profile entry never changes `COMPLETE` or progress ownership.

Home routes only a saved exact college/department pair. `컴퓨터소프트웨어특성화대학` → `소프트웨어학과` reaches the real existing software guide; its existing demo remains separate. The known Korean pair retains its existing guide action. Empty/unsupported/mismatched choices are visibly unavailable and cannot silently open software. Campus Home's existing browse/guide paths remain reachable. No other department, destination, pin, field registration, reward, storage migration or account operation was introduced.

Java/XML Views, existing artwork/icons/resources, retained view models, pure route descriptors, an explicit local preferences adapter, existing identity ports and the account progress controller supply this behavior. This keeps visual binding separate from identity, persistence, map/device handling, and gameplay rules.

## Exact changed paths

Paths below are relative to the worktree:

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/firststep/RouteMapActivity.java
app/src/main/java/com/example/firststep/game/auth/SessionController.java
app/src/main/java/com/example/firststep/game/composition/identity/FirestoreProfilePort.java
app/src/main/java/com/example/firststep/game/composition/home/entry/LocalCampusChoices.java (new)
app/src/main/java/com/example/firststep/game/presentation/home/entry/CampusChoice.java (new)
app/src/main/java/com/example/firststep/game/ui/identity/IdentityActivity.java
app/src/main/java/com/example/firststep/game/ui/identity/IdentityViewModel.java
app/src/main/res/layout/activity_geographic_map.xml
app/src/main/res/layout/game_identity_activity.xml
app/src/main/res/layout/game_main_dashboard.xml (new)
app/src/main/res/layout/game_main_navigation.xml (new)
app/src/main/res/values/game_main_strings.xml (new)
app/src/main/res/values/game_main_theme.xml (new)
app/src/main/res/color/game_main_nav_background.xml (new)
app/src/main/res/color/game_main_nav_text.xml (new)
app/src/test/java/com/example/firststep/game/auth/SessionControllerTest.java
app/src/test/java/com/example/firststep/game/presentation/home/entry/CampusChoiceTest.java (new)
app/src/androidTest/java/com/example/firststep/game/composition/home/entry/LocalCampusChoicesTest.java (new)
app/src/androidTest/java/com/example/firststep/game/identity/IdentityFakeSmokeTest.java
app/src/androidTest/java/com/example/firststep/game/identity/IdentityConfigurationSmokeTest.java
app/src/androidTest/java/com/example/firststep/game/identity/IdentityEmulatorSmokeTest.java
app/src/androidTest/java/com/example/firststep/game/guide/KoreanGuideRouteTest.java
app/src/androidTest/java/com/example/firststep/game/reskin/ReskinVisualSmokeTest.java
scripts/verify-h07-apk.ps1
docs/modules/home-redesign/README.md (new)
docs/harness/handoffs/HomeRedesign.md (new)
```

New interfaces: `CampusChoice` and `Guide { NONE, SOFTWARE, KOREAN }`; `LocalCampusChoices.load/save`, `loadGuestProfile/saveGuestProfile` and atomic `saveGuest`; `SessionController.beginEdit()`; `IdentityActivity.tabIntent(Context, boolean)`; `RouteMapActivity.mainBrowseIntent(Context)`. Existing profile constructors/ports, dataset/action values and server schema remain compatible. The public `hasPendingLocalWritesForTest()` is a read-only fixture fence, not a storage operation. [Module contract and caller details](../../modules/home-redesign/README.md)

## Actual failures, review findings and repairs

The first `scripts/verify.ps1` run executed all 51 tasks. JVM tests and app assembly passed, but lint rejected two new issues: `windowLightNavigationBar` in base values required API 27 while minSdk is 24; and the waiting-time renderer passed formatting arguments through a resource variable lint also associated with a non-format loading string. Removed the version-specific XML item, used `WindowCompat` for bar appearance and referenced the waiting resource directly. The repaired gate passed (23 executed, 28 up-to-date). No baseline/suppression was added.

Independent source review then found four actionable issues, all repaired:

1. An old Map return intent overrode the current saved tab during recreation. Intent tab requests now apply only on fresh creation; saved state wins. The actual route test now covers Map→My→Home→recreate and Map→Home→My→recreate.
2. Fake test preference restoration could race a queued local save after Activity close. Added a thread-safe pending-write counter (increment before queueing, decrement in `finally` after the write), per-save completion assertions, and an `@After` zero-write fence before restoring the original preference snapshot. This was a source-identified race, not observed runtime corruption.
3. Reskin asserted the old unconfigured-state text. It now checks the new approved message while retaining disabled Google/reachability assertions.
4. The H07 runner still listed old fake/SDK method names. Its expected eight fake+configuration cases and renamed SDK case now match source; its strict no-skip result parser remains. Screenshot capture and orientation/font-scale assertions are retained. The script was parsed and name lists compared, not executed.

Management additionally identified two separate guest preference commits that could leave profile and route inconsistent on partial failure/process death. Added `saveGuest(Profile, CampusChoice)`: validate all fields and matching department before one editor/commit writes all five guest fields. The view model now uses it. New Android cases check reopening and complete preference-map preservation on invalid input. No process-kill experiment or disk-failure simulation was run.

After the review repairs and then the atomic-save repair, the full gate was rerun each time. Both final runs passed with 15 executed/36 up-to-date; the test APK runs passed with 4 executed/47 up-to-date. Final exact-patch review reported no remaining actionable findings. A local metadata extraction initially hit Windows default text decoding; rerunning it explicitly as UTF-8 produced the final evidence JSON. This did not affect app verification.

## Commands, environment and actual results

Windows PowerShell; Android Studio JBR `C:/Program Files/Android/Android Studio/jbr` (OpenJDK 25.0.3); SDK `%LOCALAPPDATA%/Android/Sdk`; Gradle 9.6.0; AGP 9.4.1. `app/google-services.json` is absent: final APKs are **unconfigured** builds.

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
./scripts/verify.ps1
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebugAndroidTest --console=plain
python scripts/harness.py check
git diff --check
```

Final `verify-atomic.log`: `testDebugUnitTest assembleDebug lintDebug` PASS, **15 executed, 36 up-to-date**. JUnit XML: **239 tests, 45 classes, zero failures/errors/skips**. Lint: **zero errors/fatal issues, 190 warnings**, which remain unresolved. `android-test-atomic.log`: instrumentation compilation/packaging PASS, **4 executed, 47 up-to-date**. Harness syntax/contracts: 11 tasks and 5 profiles PASS; this does not verify model access. Whitespace PASS. Merged debug manifest check found only `com.example.firststep.game.ui.identity.IdentityActivity` as launcher. PowerShell parser and static expected-method comparisons passed for the updated H07 runner. No device script was invoked.

Ignored evidence: `.local/harness/HOME_REDESIGN/v1/`. Original failed `verify.log`, repaired `verify-repair.log`, pre-atomic `verify-final.log`, final `verify-atomic.log`, corresponding Android compile logs, `verification-summary.json`, independent review, manifests/hashes and `final.patch` preserve the run. JVM XML is under `app/build/test-results/testDebugUnitTest/`; lint XML/HTML is under `app/build/reports/`. `final-worktree.json` records the final diff hash and all changed file hashes without requiring a commit.

Final app APK `app/build/outputs/apk/debug/app-debug.apk` SHA256:
`e99da8061c95e147c8266ee3e8aa8c6f22f44651a3e572c6543dab106b5e541e`

Final test APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` SHA256:
`6b98786a118268c0fb6edb32361270944923a917b79d7f93ae75898d4b69aeaa`

## Test scope and runtime handoff

Passing JVM additions include `SessionControllerTest#explicitEditRetainsDraftAndRequiresExactServerConfirmation`, `#editCannotCrossAccountsOrAcceptLateConfirmation`, and `CampusChoiceTest`'s two exact-pair/validation cases. Existing identity/sync guard regressions also ran as part of the full suite.

Compiled but **not run** Android coverage:

- `IdentityFakeSmokeTest`: seven named cases in `verification-summary.json`, covering immediate Home/deferred profile, exact server save and editing, cache/read failure, cancel/retry, stale UID/logout, draft recreation and guest saved route/tab/configuration behavior. The guide case checks actual `software_guide_v1` and `digital_information_software`, not just an Activity class. Fake fixture data and provider callbacks are synthetic. Preference snapshot/restore is limited to the new selection file and requires an isolated test device.
- `LocalCampusChoicesTest`: `persistsAndIsolatesGuestAndAccounts`, `rejectsCorruptionWithoutOverwritingIt`, `atomicGuestSavePersistsMatchingChoiceAndProfile`, `invalidAtomicGuestSaveLeavesEntirePreferencesUnchanged`.
- `IdentityConfigurationSmokeTest`, affected `ReskinVisualSmokeTest` and the existing four `KoreanGuideRouteTest` cases: adapted to Start→Home→Campus Home→Guide and My Page while retaining guide, software-state, narration and source assertions.
- `IdentityEmulatorSmokeTest`: requires the deliberately prepared local Firebase SDK fixture. The updated case is `sdkConcurrentExistingProfileIsUpdatedAndPreservesCreationTime`; other server/cache/malformed-data/held-callback guards remain. No Firebase emulator, live Google or cloud operation was performed here.

The next bounded step is management's isolated portrait and enlarged-landscape run of fake/configuration, local store, guide and reskin routes using the exact APK hashes, then explicitly prepared SDK tests if authorized. Native startup/splash, keyboard/dropdown layout, optional Google cancel/success on a configured build, actual profile read/update and real-device behavior remain pending. The current build does not establish configured/live acceptance. No existing user emulator, phone, session, profile, local progress or account was operated by this run.

## Actual roles and exact review

Implementation lead owned shell/map navigation, view-model wiring, resources, test refinements, builds and this handoff. `/root/korean_content` actually ran as `gpt-6.1-terra` and implemented explicit profile editing plus its JVM tests, then adapted fake identity/route tests. `/root/implementation_pm` actually ran as `gpt-6.1-sol` and coordinated the reused `gpt-6.1-terra` specialist for route/local-store models, persistence/atomic tests and four legacy instrumentation adapters. No role profile is counted as execution.

Independent `/root/h07_exact_review_pm` actually ran as `gpt-6.1-sol`, high. It reviewed the initial patch, reported the four issues above, then matched all **25** final source/test/runner file hashes and reported PASS/no remaining actionable findings on `review-final.patch` SHA256 **`a8990f8280d5b6a0679232d304bbd7dd725efd47a936d5ece5c66d8cadbb6ce6`**. It also inspected the final build log. Review was read-only source/evidence review; it did not build, operate devices or claim runtime acceptance. Documentation is added after that reviewed app patch; final diff/hash evidence includes it.

Journal update: pending management review.
