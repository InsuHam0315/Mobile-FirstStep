# Confirmed core scope and H02 dispatch basis

Source: user-supplied C:/Users/InsuHam/Desktop/2026-2_모바일/Project/mid/첫발자국 게임 설계서.pdf, dated 2026-11-06. Management re-read all eight pages and rendered page 5 on 2026-11-06. The user authorized sequential implementation of the supplied design. The earlier coreGame=pending_confirmation entry was a stale management tracking entry, not an unresolved team decision. It is now reconciled to approved for the documented core only; do not ask the user to reapprove these specified rules.

## Source-backed rules

- Pages 1-2: five destination episodes, free visit order, department building recommended rather than a sequential unlock chain.
- Pages 1, 5-6: introduction completion records introDoneAt; normal quiz eligibility begins after 15 minutes. A notification is an aid, not the authority. The quiz can be taken away from the building afterward.
- Pages 3-4: three questions per building; at least two correct passes; immediate retry on failure with question/choice order shuffled.
- Pages 2, 5: passing grants one building stamp; five completed buildings enable the ending. Prevent duplicate grants as an implementation invariant.
- Pages 5-6: static content in assets JSON, per-building progress in Room, profile/settings in SharedPreferences. Preserve presentation/domain separation established in H01.
- Page 1: normal 15-minute delay remains; instant eligibility is an explicit demonstration/debug setting, never represented as a real 15-minute wait.
- Page 5 diagram: LOCKED means arrival eligibility is unmet (outside GPS range), not that another building must be completed. ARRIVED -> discovery/summon -> introduction -> waiting -> quiz -> completed is per-building; waiting elsewhere must not block another building.
- Pages 3, 7-8: coordinates, building facts, scripts and assets still need field/content confirmation. Use clearly labeled fixtures for H02 tests; never invent verified campus facts or mark 0,0 as a real location.

The PDF excludes server integration for this final-presentation core. Firebase/auth/RAG and legacy-retention discussions remain separate optional decisions; approving coreGame does not approve those modules. H02 must stay local/offline and must not delete working legacy features.

## H02 bounded implementation

Branch: progress. Build domain rules, injectable clock, local progress repository/Room adapter and JSON contracts. No new map, discovery, TTS or quiz UI beyond test harness needs; those belong to later modules.

Keep each building independent. Persist eligibility across restart; replaying introductions must not postpone or duplicate an existing unlock. Preserve stable answer identity after shuffling. Make attempt submission/reward updates atomic and idempotent; define and test attempt restoration/retry behavior. Preserve legacy DB/data via explicit isolation or a non-destructive migration with tests. Keep demonstration progress distinguishable from normal progress under existing repository rules.

Verify multiple waiting buildings, eligibility boundary with a fake clock, restart, retry/scoring, duplicate completion, shuffled answer identity and persistence behavior. Domain checks should not require a 15-minute real wait. Add meaningful Room instrumentation where required, disclose environment limits, and repair failures. Use the existing harness hierarchy and handoff protocol; management performs acceptance and integration.

Commit format: [progress v1] 기능: ... then v2, v3 for subsequent coherent commits, Korean subjects/bodies. Start only after H01's verified integration is on main. Do not write Notion, change product gates, push/merge or advance to H03 independently.
