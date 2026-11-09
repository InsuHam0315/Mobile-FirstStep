# H07 configured identity/profile resumption

Management dispatch basis, 2026-11-09 (Asia/Seoul). Scope remains H07_SCOPE.md, identity and first profile only. H07B sync is not dispatched. H10 is accepted on main and must be preserved.

## Current anchors

- Main a0782c32b4338b257b2c34cc33a0b0c6d531a197 contains accepted H10 cd0f44f plus management records.
- Resume existing branch firebase at d05dd6b02664918584eeb60e3b11ddab968252ba, original base b7fffd048d026065c66b8ed03796d40f0b9628b6, worktree C:/Users/InsuHam/FirstStep-worktrees/h07-firebase. No dirty tracked changes were present before resumption.
- Implementation chat: local 01a1b03e-8027-7ad3-b54e-53c538832975. Compact snapshot confirmed last H10 run complete/inactive, no concurrent slice. Do not reopen H10 or drift to H01 after compaction.
- This continuation file lives on current main. Read it by its absolute main path when resuming the older firebase worktree. Do not import/overwrite main planning from the older worktree. Management will integrate the final candidate against latest main and verify preservation of H10; implementation does not merge main or push.

## Configuration evidence

Authorized setup worker reported console completion on 2026-11-09: project firststep-a952f, Spark, Android com.example.firststep, Google provider enabled and debug signing fingerprints registered; Firestore Standard/default in asia-northeast3. Reviewed source rules were published and reloaded at 14:34 KST with no unpublished changes. No billing, Analytics, optional Gemini/Functions/backups or user profile documents were added by setup. This is attributed console evidence, not management APK/OAuth evidence.

Management independently inspected local client metadata (project/package/client types 1 and 3) and hashes without printing credential values. Client configuration SHA256: 311FBC32AC26E80A803A9397090F25F704F32226275BA1FA9ED030AFBECD2008. It remains ignored at main app/google-services.json. Copied only this intended file into the previously absent firebase-worktree app/google-services.json, refusing overwrite and verifying identical hash; both paths are Git-ignored. Do not commit/config-dump it or copy other credentials.

Rules source SHA256: E9F463F50221582A55867A5B474A771A465DA9DC4C6EFD1D187EEF198F889F4D, independently recomputed. Deployed behavior must still be checked; source hash alone does not prove remote enforcement. Do not redeploy or weaken rules autonomously.

- Console: https://console.firebase.google.com/u/0/project/firststep-a952f/overview
- Provider: https://console.firebase.google.com/u/0/project/firststep-a952f/authentication/providers
- Rules: https://console.firebase.google.com/u/0/project/firststep-a952f/firestore/databases/-default-/security/rules

## Bounded work now

1. Verify exact scope/worktree/candidate/config metadata and usable Play-services-enabled emulator before editing. Preserve prior local evidence. Implement only bounded fixes required by genuine configuration/live acceptance; continue Korean [firebase v3] and later commits.
2. Run actual configured build and required checks. Validate real configured -> clean unconfigured build isolation using a separate temporary verification checkout/build context; leave both user config copies untouched. No stale generated client resources or fake-success production route. Record exact source/config hashes privately as appropriate, no API keys/tokens/client secrets/profile values in shared output.
3. Prepare a visible APK login entry for the human. Human handles Google account selection, consent and any password/MFA. Do not invent name/student number/department or type into a personal account without user-provided values. Report exact emulator/window/button when intervention is necessary. Initial missing-profile login can establish incomplete onboarding before the human submits their own form.
4. Verify real Google identity -> server-confirmed private profile write -> app restart/read -> missing/incomplete onboarding recovery -> logout/account isolation. Preserve original records and all test/fixture databases. Do not delete or corrupt a real profile to manufacture an incomplete case; use the naturally incomplete pre-save flow or explicitly authorized test data. Distinguish real second-account evidence from local synthetic isolation; if a second human sign-in is required, state that gate rather than claiming mock coverage is live coverage.
5. Re-run affected local/unit/rules/APK checks as warranted by changes, independent exact-candidate review and fresh required checks. Record actual fresh/cached counts, real-versus-simulated evidence and remaining live gates. Do not reset devices/accounts or seize another running device session. Only restore/stop resources owned by this run.
6. Submit updated English H07 handoff to management. No push, main merge, Notion changes, H07B, optional services or billing. Configuration success does not close live acceptance. Management owns publication, acceptance, latest-main/H10 integration and the same firebase journal.

If an operation is denied, inspect uncertain outcomes before retrying. Retry only once for materially new relevant evidence when known not executed; if still blocked, stop that action and report target/reason/completed work/outcome. Preserve this constraint when delegating.
