# H01 management acceptance review

Date: 2026-11-06. Verdict: CHANGES_REQUIRED (P2 test isolation).

Reviewed feature: codex/game-foundation, worktree C:/Users/InsuHam/FirstStep-worktrees/h01-game-foundation.
Base: 8f239756486075b37aeca343ac73f51e2b6d9452.
Implementation: 0a4328e89c206a856c7ce8d931b48341506c0d40.
Handoff tip: ce6857da8160a144d4cb7daefdfd23cd8a1e37f7.
Feature not pushed or integrated. Management made no app changes.

## Confirmed evidence

Read implementation handoff, source, retained logs and XML reports. Inspected actual APK normal-portrait and large-font landscape restored-state PNGs. Read-only verification PM reviewed the exact patch independently and reported no production-code blocker within H01.

- Retained unit XML: 44 tests, zero failures/errors/skips, including four H01 tests.
- Three final targeted instrumentation logs each report OK (2 tests): normal portrait, large-font landscape, small-font portrait. These are repeated conditions, not six distinct tests.
- Retained lint XML: zero errors, 94 warnings, none located in H01 files.
- Token-only experiment log reports two instrumentation tests passing; handoff records 44 passing unit tests and restored token bytes.
- Handoff evidence commit changes only its documentation and task status relative to the implementation commit.
- Management did not rerun Gradle, the emulator, or the whole legacy instrumentation suite. Review evidence does not establish physical-device or field success.

## P2: new preview test depends on previously denied permissions

File: app/src/androidTest/java/com/example/firststep/game/GamePreviewSmokeTest.java:43-45.
The general preview/recreation test unconditionally asserts CAMERA, FINE_LOCATION and COARSE_LOCATION are denied before launching the Activity.

Existing ThreeMissionFlowTest.java:100 and VisualToolsTest.java:50 grant CAMERA without restoring it. Running the new test after those tests, or on an already-authorized installation, can fail before testing the preview. This is a source-confirmed composability issue; management did not execute that ordering to reproduce it. Targeted denied-permission run results remain valid under their documented setup.

Required repair: separate general preview/recreation behavior from the denied-permission scenario. The general smoke test must work with permissions granted or denied. Give the denied-permission scenario an explicit isolated invocation/preflight that runs before instrumentation; do not revoke target-app permissions mid-instrumentation. Record reproducible commands and verify both initial permission states. Do not merely remove denied-permission evidence or silently count a skipped scenario as passed.

Repair only in the existing feature worktree. Preserve newer management policy on main. Do not merge/push or begin H02 as part of this repair. Commit the repair with a descriptive Korean message; the already-running H01 remains exempt from branch renaming and revision-label retrofit. Return exact commit, changed tests, commands/results and remaining limitations for acceptance re-review.

## Journal and next step

Notion branch journal: https://www.notion.so/410ed980d97e819db87ac527355f5786
Status: implementation reported complete, management review open pending test-isolation repair.
After repair acceptance, integration and core-game decision gates must be addressed before H02. New module branch will be progress under the current naming rule.
