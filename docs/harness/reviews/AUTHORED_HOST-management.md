# Authored host exact candidate review

## Continuation review: 262af20 / 7cdd170

2026-11-10. Exact independent management review closes both historical P2s below: post-introduction quiz no longer depends on proximity, and explicit send publication is independently owned. Required GPS policy/adapters, NORMAL direction/SHAKE, Skip, readback and reminder callers now exist. Retained42 focused JVM tests passed;52 tasks9 executed43 reused. Three authored Activity tests compile only.

CHANGES_REQUIRED for one new P2: a registered rotation sensor can remain silent, leaving heading WAITING while the actual view hides its fallback. No first-valid-heading timeout transitions it to unavailable, so both SHAKE/hold remain gated. Bounded repair dispatched once to the same owner, v16 onward; source regression and short check required. No device/field claim.

Management prepared isolated combined source e19c10e (main596f7f9 + campus262af20 + homefab2016); this is provisional, not main integration. Equivalent reminder dependency commits reconcile; the only test conflict preserves Home's new cold-process ticket rejection test. Home's missing facility search/map/detail bridge is separate approved code work assigned to its owner. Final design and whole-app runtime follow those required callers.

## Historical initial host review

2026-11-10. Candidate b2795f1d47ae769dabe1e947e30481ad95cac339, production d777ac51094796aa288ef984f6e2ca3b597e07a7, base dd4b45894bc4bc6a4f3c06bf9fd0e821fb2e544d. Management verification PM independently inspected exact source and retained check-04 evidence: nine focused JVM tests, APK and Android test compilation pass. No device/cloud actions.

Verdict: CHANGES_REQUIRED. Not integrated or accepted.

- P2: AuthoredEpisodeHost opening checks current arrival before reading committed introduction; startQuiz/submit also use withArrival. This contradicts CORE_GAME_SCOPE's quiz-away-after-introduction rule. Fix open/resume and post-intro actions, and test departure/restart/retry.
- P2: AuthoredEpisodeActivity gameplay publication can replace an in-flight explicit flush observer with old reconciliation, advance syncGeneration, suppress the actual acknowledgement and re-enable Send prematurely. Preserve explicit-send ownership to completion and test controlled overlaps, including failed quiz submit with no new reconcile.

The production gate remains closed before store/network, but that does not complete required V1 gameplay. Actual GPS policy/adapters/config injection and NORMAL direction-to-SHAKE discovery are missing implementation, not simply pending field validation. AUTHORED_FLOW_CONTINUATION.md assigns these within the current user-approved scope. Real surveyed configuration, production activation, actual SQLite/Firestore/device/field evidence remain separately unverified. Latest design stays deferred.
