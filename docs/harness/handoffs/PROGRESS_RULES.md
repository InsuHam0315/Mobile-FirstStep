# Firestore progress rule candidate

`progress-sync` v5, worktree `C:/Users/InsuHam/FirstStep-worktrees/progress-sync`, base `c4c0eba615ee07b141730fccd16c850cf56ddc71`. The verified candidate commit is recorded in `.local/harness/PROGRESS_RULES/v5/verified-commit.log`. The candidate files are under `firebase/progress-sync/`. Existing deployment files `firebase/firestore.rules` and `firebase/firebase.json` remain as in the base.

The candidate matches `FirestoreSyncBackend` and `MilestoneDocument` at `users/{uid}/progressDatasets/{datasetId}/modes/{mode}/buildings/{buildingId}`. A matching UID with a Google sign-in provider claim and a complete stored profile may access an eligible NORMAL document. Eligibility requires the exact trusted registration `{"enabled": true}` at `_firststepProgressAllowlist/{datasetId}/buildings/{buildingId}`. An administrator or trusted backend with out-of-band write access would own that collection. All client access to it is denied. Missing, disabled or malformed registrations close access, including reads of existing progress. Reserved dataset names remain excluded even if a registration is seeded. Local tests seed only synthetic registrations.

The progress schema requires version 1, integer milliseconds in the client range and a required nullable completion field. A passing completion has bounded IDs, integer score 2–3 and an integer completion time at least 900,000 ms after the stored intro. A valid owner can read a missing document before a transaction creates it. A first write can carry an intro or a merged pass. Updates can move the intro earlier and set the first pass. Once stored, all four pass fields must remain the same. Listing, deletion and access to malformed stored progress are denied. The profile path still follows the existing owner and metadata rules.

## Local verification

From the feature worktree, install the existing locked test tools and run the separate demo project.

```powershell
npm.cmd ci --offline --ignore-scripts --prefix firebase --no-audit --no-fund
.\firebase\node_modules\.bin\firebase.cmd emulators:exec --only firestore --project demo-firststep-progress-sync --config firebase/progress-sync/firebase.local.json 'node --test --test-concurrency=1 firebase/progress-sync/progress.rules.test.mjs'
```

Verification used Windows, Node 24.18.0, Android Studio JBR 25.0.3, Firebase CLI 15.35.0, Firestore Emulator 1.22.0, Firebase SDK 13.2.0 and Rules Unit Testing 6.0.0. The final run used loopback ports 18282 (Firestore), 19182 (websocket), 14682 (hub) and 14782 (logging), after confirming they were free. The command ran from the ignored evidence directory with absolute config and test paths so emulator logs stayed there.

The first emulator run passed 12 cases. Its expected denials for absent provider claims included rule evaluation diagnostics, and the CLI selected the default websocket port 9150. The final candidate explicitly checks claim and document presence, pins the websocket port and adds tests for registration revocation and partial-profile recovery. The final run passed 14 cases with zero failures, skips or cancellations. It covered transaction create/pass/read, merged writes, invalid schema, owner/provider/profile constraints, denied listing and deletion, default-denied targets and the retained profile path. Google claims were synthetic. The emulator shut down after the test command. The dependency log and first-run logs are in `.local/harness/PROGRESS_RULES/v5/`. The final run logs are `final/emulator.log` and `final/firestore-debug.log` in the same directory.

The root authored the candidate and ran the emulator. The independent `/root/h07_exact_review_pm` (`gpt-6.1-sol`, high) reviewed the three candidate files against the client contract and reported PASS with no actionable findings. The reviewer performed source review only. The full-index binary candidate patch SHA256 was `EF784EB7B82825A6010EF4202F89DA16994B8C400833D34521D0C6C64929CD3D`, recorded with the patch and `results.log` in the evidence directory. The harness and staged whitespace checks passed.

Android build and device runs were outside this rules-only checkpoint. Live Firestore, actual Google sign-in, cross-device readback, deployed rules and production registration remain unverified. The schema and client-supplied timestamps cannot establish campus arrival, sensor truth, elapsed real time or quiz integrity.

Journal update: pending management review.
