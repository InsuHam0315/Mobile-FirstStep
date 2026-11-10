# Final V1 combined review — 2026-11-10

## Decision and exact scope

Accepted for the implemented, production-closed V1 scope at `7cd25b9cd9eafb904cda7ee5352d369bd9a65126`, fast-forward integrated into main after independent source review and the checks below. This is not approval of campus coordinates, production Firebase activation, physical sensors, tracked AR or live Google sign-in on the new APK.

The user's original defect was profile completion reaching an account Home without a usable exploration path. Identity COMPLETE now opens Campus Home, with department guide/practice, explicit local DEMO, facilities and map. Authored NORMAL has actual GPS/discovery/intro/quiz/storage/sync callers, but its production registry and surveyed target configuration intentionally remain empty. A closed NORMAL button must not be described as an enabled field tour.

Accepted owner tips:

| Branch | Exact owner tip | Scope |
| --- | --- | --- |
| reskin | 4303c1cc4de95516f07b779f717517959393fe33 | Replaceable Java/XML presentation; landscape map and DEMO action repairs |
| validation | e4b345316e690700a4272c8b8928ec4c5c602044 | Actual Activity/storage tests, local Android Firebase SDK harness, reminder receiver checks |
| home-progress | 6c1e05e0f3d5eb3c86df7fe54c5d78df8a2bb822 | Explicit countdown format resource fixes full-gate lint error |
| campus-content | 5b961def6c040cdba5bac57530fd9069ea2c0854 | Previously integrated functional authored flow; exercised by combined tests |
| progress-sync | eabed511d98274e6454e7dc7f00b34ddddfcb07c | Previously integrated candidate rules; unchanged production rules |

Root management independently reviewed exact changes and pixels. Sol implementation PM coordinated bounded presentation work with Terra specialists; Sol verification PM independently inspected source/screens and added the bounded reminder test. The authorized implementation chat owned Activity/storage and SDK test code. Management alone performed device runs, integration, publication and journals. No accepted H01–H06 historical findings were reopened.

## Final build and actual runtime evidence

Isolated worktree: `C:/Users/InsuHam/FirstStep-worktrees/management-final-v1`.

Command: `gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`, Android Studio JBR.

- Initial full gate executed all 81 tasks: 233 JVM tests passed; one lint error blocked acceptance.
- After the countdown repair: 81 tasks, 15 executed / 66 reused; lint passed.
- After final presentation changes: 81 tasks, 18 executed / 63 reused; 233 JVM tests in 44 suites, no failures/errors/skips.
- Final reminder-test-only integration: 81 tasks, 8 executed / 73 reused, PASS. No app source changed in this last merge.
- Final lint: **0 errors / 107 warnings**, not zero warnings. Landscape variants add 12 warnings over the preceding 95: duplicated hardcoded map text, overdraw and the existing API-level accessibility attribute. These are recorded maintenance items; no suppression was added to hide a failure.
- Final app APK SHA-256: `C975437546361C14BA7DF7D49576976DD29B8C05013BE697B8E607AE97805DB1`.
- Final app configuration: `H07_CONFIGURED=false`. Synthetic identity tests and named local Firebase clients do not make this a live Google-login APK.

### Separate configured user-preview build

After source acceptance, main reused its existing ignored `app/google-services.json` unchanged to build a separate user-preview APK. No client ID or credential was invented, copied into Git or changed. `H07_CONFIGURED=true`; `testDebugUnitTest assembleDebug lintDebug` passed (51 tasks, 29 executed / 22 reused). App SHA-256: `BFE6CD6500F20D85A998287C6DFA8EB5EB5CC8FFAB382D1FCBBC8F661890AC1B`. This confirms configuration and build, not successful new live OAuth/profile or production progress activation.

Both artifacts are retained separately in `C:/Users/InsuHam/FirstStep/.local/delivery/2026-11-10-final-v1/`: `FirstStep-qa-unconfigured.apk` is the tested isolated QA binary, `FirstStep-google-configured.apk` is for the user's real Google sign-in check. Do not describe the former as Google-configured or transfer its synthetic records to the latter. Protected user-held devices remain unchanged.

After all isolated tests, private AVD5592 was restarted with a visible window and the configured APK installed there. The real `IdentityActivity` opened successfully; the rendered Google button is enabled and the screen requests account selection. Management did not click Google or enter account information. Screenshot: delivery `screenshots/google-configured-login.png`. The user can now perform live sign-in themselves on this separate preview. No protected device was reinstalled.

| Executed check | Result and boundary |
| --- | --- |
| Final combined Activity/storage suite | 31 tests PASS, 79.953 s; actual Activities with controlled synthetic account/location/sensor/time and isolated stores |
| Presentation / reachable controls | 4 tests portrait 100% + the same 4 landscape 130% PASS after repairs; screenshots inspected |
| Identity large-font recheck | 6 fake identity/profile/account-Home cases PASS before landscape-only map/discovery repair; not real OAuth |
| Android Firebase SDK | 1 selected opt-in test PASS on local Auth/Firestore; two independent named SDK clients, synthetic identities, no skip counted |
| Authored notification permission | 1 denied-permission test PASS through the real Android adapter |
| Authored receiver notification | 1 granted-permission test PASS; actual OS notification, immutable explicit entry and stale-owner rejection after directly invoking a due receiver |

Repeated executions are not additional unique test cases. The final combined 31 cover guide/narration, account Home, Campus Home, facilities/map attribution/touch regions, identity fake states, local DEMO storage, authored SQLite and actual authored journey. The journey checks blocked misaligned shake, aligned discovery, intro Skip, leaving/re-entering before and at the 15-minute boundary, failed quiz/retry/pass, explicit send/readback and late owner change. Time/location/sensor inputs are synthetic. SQLite tests exercise real commit/reopen/recovery and account isolation; they do not establish OS process-death durability.

The SDK run uses project `demo-firststep-progress-sdk`, Android host alias `10.0.2.2`, loopback Auth19383 / Firestore18383, and candidate rule SHA-256 `A5A98005ECCE528372FF658E24EA4ABDBC2AA5912B097344D45F2AC2F221E15E`. It verifies empty read, intro/pass merge, earliest intro / immutable pass, second-client readback, pass-removal denial, cross-owner denial and local revoked-owner guard. It does not test two physical devices or live Google OAuth. Local services were shut down after the successful run.

Reminder checks do **not** claim elapsed AlarmManager delivery, notification tapping, reboot survival or process-death durability. Process-local reminder tickets remain best effort; Home uses persisted introduction time independently.

## Observed failures and repairs

1. Full lint found `StringFormatInvalid` in the Home countdown. The owner replaced the dynamically selected format resource with the explicit waiting string. The full gate then passed without suppressing the rule.
2. The first private AVD rendered both the app and Android Settings black, and Espresso could not obtain focused root windows. Restarting only that private AVD with `-gpu swiftshader -feature -Vulkan` restored Settings rendering; after reinstalling the absent test app on that disposable instance, the same routes passed. This is documented host-renderer evidence, not a claim that all black screens have this cause.
3. The SDK launcher received two Node paths and concatenated them into an invalid executable path; its old caller could retain an unrelated zero exit code. Validation v4 selects one verified application path and fails explicitly. The repaired launcher and actual SDK test passed. The earlier launch failure is not counted as an SDK pass.
4. Automated visibility checks originally allowed a roughly 13-pixel map strip in landscape with 130% text. Screenshot review rejected this. Reskin v4 gives the map a dedicated left panel and scrolls controls on the right. The strengthened check requires at least 96 dp of drawable map above its internal attribution, plus reachable controls. Portrait and landscape reruns and pixels passed.
5. Camera-denied landscape DEMO initially put its arrival and fallback actions below a tall black preview. Reskin v5 moves both actions above a smaller landscape preview. Both are now enabled and at least 90% visible before scrolling in the tested configuration. Camera permissions and actual discovery logic are unchanged.

## Devices, retained evidence and limits

- Combined QA: `emulator-5592`, AVD `FirstStep_Combined_1110`, API35, 1080×1920 / density420. SDK QA: `emulator-5582`, AVD `FirstStep_ProgressSdk_1110`. Both are separate disposable instances with synthetic inputs.
- User-held 5564, 5572, 5580 and their account/data/installations were not touched. No new build was installed on the user's held login session.
- Raw retained evidence under the isolated worktree: `.local/harness/final/build-final.log`, `build-final-reskin.log`, `combined-final-runtime.txt`, `visual-final-portrait.txt`, `visual-final-landscape.txt`, `reminder-denied.txt`, `reminder-granted.txt`, `sdk-runtime-repaired.txt` and `visual-repaired/reskin-evidence/`.
- SDK raw run folder: `firebase/progress-sync/verification/.local/b3b38225db144d0693042a2c25626401/`. Keep emulator logs local; do not publish credentials/tokens or raw personal information.
- Root-host renderer logs: `C:/Users/InsuHam/FirstStep/.local/harness/final-runtime/`. Failed-run and prior evidence worktrees are preserved.
- Screenshots are real APK pixels. Identity/arrival/progress may use explicitly synthetic inputs; the illustration is artwork, and OSM base-map tiles are not verified mission pins.

Remaining external gates: surveyed and approved digital-information/software destination coordinates/radius; versioned trusted production registration; separate authorized production rules activation; real Google/profile and two-device live sync on a configured new build; campus GPS/camera/sensor measurements. No fabricated coordinates, DEMO promotion, cloud photos, optional RAG or new college rollout was added. Camera overlay is not tracked AR.

No next implementation slice is dispatched automatically beyond this authorized combined V1 closure. Operational activation decisions and physical evidence remain separate.
