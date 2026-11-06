# H06 management review

2026-11-07 Asia/Seoul. Verdict: CHANGES_REQUIRED for tested checkpoint 586855f60de93d2d90dcd6b2e0111921e3b63498. Final handoff/candidate not yet submitted. Publication is not acceptance.

## Candidate and evidence

Base 94e0714, branch integration, worktree C:/Users/InsuHam/FirstStep-worktrees/h06-integration. Management published exact v1 to origin/integration and verified remote SHA. Patch SHA256 A3905D1BFA8C6914058FCD1C5D291BCE9C404607D072A764CE4ED6A9EC555C34 matches retained committed.patch. Evidence in .local/harness/H06 supports 116 passing units (zero failures/errors/skips), lint zero errors/94 warnings, required verify 8 executed/43 cached tasks, 10 focused APK tests in each configuration, restart preparation/resume 1+1, nine focused legacy checks, default REAL entry sharing one UUID across three process launches and 189 original DB files byte-identical. These are API35/synthetic fixture checks, not physical/campus evidence. No management integration build has been performed for H06 yet.

## P2: paused mode callback splits navigation and home state

GameHomeActivity.java:452-463 switches integrationState/context and Intent in its asynchronous mode callback with only destroyed/finishing guards. If the activity pauses before the worker returns, controller.mode ignores the update because HomeController.mode explicitly returns when inactive. On resume, refreshIntegration at593-598 only resumes the prior controller and does not reconcile its mode. Displayed NORMAL progress can therefore disagree with DEMO context/next navigation (and vice versa).

Management Sol PM identified the path and root independently traced it; no management runtime reproduction is claimed. Terra independently reviewed context/storage/provenance and found no additional actionable issue. This is an introduced integration lifecycle issue, not a reopening of H05.

Bounded repair sent once to the authorized implementation chat at the next safe operation boundary: apply/defer mode changes atomically across context/Intent/controller/visible progress when resumed, guard stale callbacks, and add a controlled delayed-callback regression proving pre-fix failure with distinct mode records. Verify resume/recreation and required checks, preserve data/evidence, continue Korean versioned commits, update final handoff accurately. No interruption of active device/build operations, competing implementation, main merge or H07 dispatch. Current scope and product gates remain unchanged.
