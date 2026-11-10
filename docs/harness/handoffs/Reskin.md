# Final reskin handoff

- Branch `reskin`, development base `13f485b` over functional integration `167923d`. Exact app/module checkpoint `e4a749ce59ce01da6f528bdc5e62fe32d579ad71`. Teammate visual reference `0c22f2e4548439919bca8a8ae2a4ac728f814db9`; the original artwork was visually inspected and copied as one byte-identical blob. The teammate functional history was not merged.
- The Sol implementation PM owned shared tokens, artwork, map and facility styling and integration. The Terra specialist implemented identity/profile and Campus Home layouts. The Luna specialist implemented guide, narration, DEMO and authored presentation styling. [Module visual contract](../../modules/reskin/README.md).
- The diff preserves the existing Google button, routes and all visible copy, IDs, inputs and initial visibility attributes in the 11 modified layouts. The artwork stays decorative. The camera preview, map canvas/touch policy, legacy launchers, device interfaces, navigation, domain, accounts, persistence, sync, field configuration and test sources are outside this change.
- Final short gate with Android Studio JBR and explicit SDK: `:app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --console=plain` passed, 48 actionable tasks with 14 executed and 34 up-to-date. The initial checkpoint build executed 48 tasks. APK SHA-256 `665E8283C6E374E1703A40A213A678647D509D0588B6F6BCC809FA69258F7F32`.
- No device checks were run. Combined validation still needs the exact merged APK interaction and screenshot checks, including large-font landscape, map touch/attribution and selection, Google identity/profile, both episode modes and continued access to source/disclosure text. Real account, survey and field activation gates remain separate.

## Visual smoke source

`com.example.firststep.game.reskin.ReskinVisualSmokeTest` captures full-display PNGs from actual Activities into the target app's external files `reskin-evidence` directory. Run on an unconfigured debug APK. Use `reskinOrientation=1 reskinFontScale=1.0 reskinEvidencePrefix=p100` for portrait and `reskinOrientation=2 reskinFontScale=1.3 reskinEvidencePrefix=l130` for enlarged landscape. Prepare the actual device font scale externally. The test checks the observed configuration and rejects unsafe filename prefixes.

The four tests cover the disabled branded login without authentication, Campus Home through the bundled software guide and narration into transient practice and back, a fresh isolated local DEMO route through discovery entry and back without completing it, and the selected-facility browse map. The map screenshot is captured before checking that both drawable map area and the in-canvas attribution band are visible. The test checks reachability and enabled states without granting permissions or modifying account progress or favorites. The isolated DEMO uses its own debug run identity and records only its own navigation. Account profile/Home screenshots are supplied by the existing identity fake suite.

`:app:compileDebugAndroidTestJavaWithJavac --console=plain` passed with 29 tasks, 1 executed and 28 up-to-date. All screenshot and interaction outcomes remain pending device execution.

## Landscape map repair

The combined visual run exposed a selected-facility map with only about 13 pixels of drawable content at 1920 × 1080, density 420 and font scale 1.3. The landscape resource now gives the canvas the full available height beside a separate scrolling panel. Facility context, the external attribution link and controls remain in the panel. Canvas drawing, its built-in credit strip, touch and pan logic, browse-only eligibility, and the portrait layout are unchanged.

The smoke test checks that the in-canvas attribution is inside the visible viewport and at least 96dp of drawable map is available above it. That lower bound gives the content two 48dp touch-target heights for panning rather than counting a sliver as usable. The test also reveals each browse control, captures a second image and checks the map area again after scrolling.

Debug APK and Android test compilation passed with 48 actionable tasks, 9 executed and 39 up-to-date. Static inspection confirmed that the two layout variants have identical IDs, visible copy and visibility/live-region attributes. The repaired source still needs the landscape device rerun.

## Landscape DEMO discovery repair

The enlarged landscape capture showed the 240dp camera-denied preview before both entry controls, leaving them below the first viewport. The landscape resource now places arrival simulation and the camera-free fallback immediately after the existing DEMO disclosure. A centered 280 × 160dp preview sits below the status and retains the same CameraX view and overlaid artwork. Portrait, authored preview and discovery state/callbacks are unchanged.

The smoke test now captures the initial viewport before scrolling and checks that both entry controls are enabled and at least 90% visible in landscape. It still reveals both controls in each orientation. APK assembly and Android test compilation passed with 9 tasks executed and 39 up-to-date. Device confirmation of the revised layout is pending.

## Full-window welcome follow-up

Reskin v6 starts from `f87f3cafc599d0e99d4c6590d632e7900edf2762`, the accepted single-launcher main. The Sol implementation agent reused the original teammate illustration and matched the supplied welcome reference with edge-to-edge artwork, a dark scrim and bottom-aligned title, pink account button and dark guest button. The scroll layer accounts for system bars and permits larger text. Signed-out entry presents welcome. The account action opens the existing branded Google panel. Confirmed sessions keep the existing Home/profile route.

Guest opens the standalone bundled guide and transient practice. The existing isolated local software DEMO remains available from the guide in debug builds. The welcome copy discloses this scope. The guest path has no account action or transfer into account records. Tests exercise guest to guide and back, account-panel opening, and the prior identity flow after the new entry step.

`testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest` with an absent H07 config passed 233 JVM tests and Android test APK compilation. Gradle ran 50 of 81 tasks, with 31 up-to-date. Lint reported 0 errors and 111 warnings. Configured `assembleDebug` passed with 5 of 37 tasks run and 32 up-to-date. The configured APK reports `H07_CONFIGURED=true` and SHA-256 `6D86C3746A14286A239FA99660A29841D930B4A60BE3E5E501437B695A1DBBD7`. APKs and logs are in `.local/harness/identity-welcome/`. Device execution, remote publication and main integration are management steps.
