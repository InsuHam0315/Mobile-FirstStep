# Authored host exact candidate review

2026-11-10. Candidate b2795f1d47ae769dabe1e947e30481ad95cac339, production d777ac51094796aa288ef984f6e2ca3b597e07a7, base dd4b45894bc4bc6a4f3c06bf9fd0e821fb2e544d. Management verification PM independently inspected exact source and retained check-04 evidence: nine focused JVM tests, APK and Android test compilation pass. No device/cloud actions.

Verdict: CHANGES_REQUIRED. Not integrated or accepted.

- P2: AuthoredEpisodeHost opening checks current arrival before reading committed introduction; startQuiz/submit also use withArrival. This contradicts CORE_GAME_SCOPE's quiz-away-after-introduction rule. Fix open/resume and post-intro actions, and test departure/restart/retry.
- P2: AuthoredEpisodeActivity gameplay publication can replace an in-flight explicit flush observer with old reconciliation, advance syncGeneration, suppress the actual acknowledgement and re-enable Send prematurely. Preserve explicit-send ownership to completion and test controlled overlaps, including failed quiz submit with no new reconcile.

The production gate remains closed before store/network, but that does not complete required V1 gameplay. Actual GPS policy/adapters/config injection and NORMAL direction-to-SHAKE discovery are missing implementation, not simply pending field validation. AUTHORED_FLOW_CONTINUATION.md assigns these within the current user-approved scope. Real surveyed configuration, production activation, actual SQLite/Firestore/device/field evidence remain separately unverified. Latest design stays deferred.
