# Accepted design review repairs

Branch `design`, worktree `C:/Users/InsuHam/FirstStep-worktrees/design-fixes`, base `b16877a718a02ad70f735ea58bedf2b29623f52a`. The local commit is recorded in `.local/harness/DESIGN/v4/verified-commit.log`.

`IntroQuizActivity.renderBooklet` now keeps Previous visible on the last page and preserves its existing index guard and controller action. `SoftwareBookletNavigationTest` creates a new local DEMO run with a synthetic discovery receipt, returns from page seven to page six, recreates the Activity on page seven and returns again. It asserts that browsing does not finish the introduction or write its timestamp.

`NarrationActivity` and `game_narration_activity.xml` add a visible preview disclosure and sentence provenance alongside the existing artwork, speech bubble, source button and fixed navigation. Claim sentences show the source label, checked date and full URL. Fictional and editorial sentences carry their respective labels. The preview disclosure explains the fictional guide, lack of rewards and unverified arrival location. The existing info dialog and source-opening adapter remain available. `game_guide_strings.xml` supplies the source details format.

`ReskinVisualSmokeTest.isolatedLocalDemoReachesDisclosureAndDiscoveryWithoutCompleting` now checks the camera fallback after simulated arrival, injects camera unavailability, clicks the fallback through Espresso, checks its displayed label, aligns synthetic heading and checks that manual discovery remains reachable without completing it. The test retains orientation and font-scale checks and runs with `reskinOrientation=1` or `2`. It captures the post-arrival fallback and ready states. `NarrationLifecycleTest` and `GuideSmokeTest` reveal scrolling content while retaining visibility and real Espresso clicks for fixed controls. The result-to-Home test returns through the visible quiz Back control, then exits the Guide. Existing read-only and end-state assertions remain.

The version catalog restores AGP 9.4.1 as assigned. No other dependency versions changed.

## Actual verification

With Android Studio JBR and the local Android SDK, `scripts/verify.ps1` ran `testDebugUnitTest assembleDebug lintDebug`. All 51 tasks executed and the build passed. JVM XML reports 233 tests, zero failures, errors or skips. Lint reported zero errors and 160 warnings. Eight warnings point into touched resource paths, covering existing unused resources, API-level attributes and RTL padding. The separate `gradlew.bat :app:assembleDebugAndroidTest --console=plain` compiled and packaged the instrumentation APK with 30 executed and 21 up-to-date tasks. Harness and whitespace checks passed.

The build used the unconfigured identity variant because this worktree has no local Firebase configuration. App APK SHA256 `D38A0E4EABC5B18906827511962FEBF9D76E7D0354F5F0E46052378B73153E3E`. Android test APK SHA256 `DF9854728F723A7B5B44EB7F306E9F90AFF7A5694998D3F3BB613F89557C1670`. Logs, counts, source diff and hashes are under `.local/harness/DESIGN/v4/`.

Management supplied pre-fix runtime evidence of hidden result Close and fixed-footer helper failures. The source repairs above address those paths. This owner performed source/build checks only. Portrait and landscape execution, screenshots and the new booklet instrumentation await management's isolated device run.

The Terra specialist `/root/narration_design_fix` (`gpt-6.1-terra`) implemented the scoped Narration repair. The root implemented the booklet, fallback and guide regression changes and ran builds. Independent reviewer `/root/h07_exact_review_pm` (`gpt-6.1-sol`, high) reported PASS on the nine-file source/test/catalog patch with no actionable findings. The full-index binary patch SHA256 is `47302E98FAAF2BB320DAA46B9F4B21E9DBE55F1470E70135E92DDCBE23FAD776`. The reviewer performed source review only.

Journal update: pending management review.
