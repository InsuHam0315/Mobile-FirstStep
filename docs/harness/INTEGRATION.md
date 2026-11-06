# Sequential module integration and advancement

User authorization, 2026-11-06: finish one module, resolve issues and verify it, then merge into main and proceed to the next module. Routine integration, main push and next eligible module dispatch no longer need repeated approval. This supersedes older requirements for a separate integration approval. It does not authorize bypassing unresolved feature choices, spending, credentials, deployment or rewriting published history.

## Ownership and gate

Implementation delivers one bounded module and its evidence. Management reviews the result, owns integration and Notion, and selects the next eligible module. The implementation chat's per-slice stop remains a handoff boundary, not a demand for renewed user approval. Do not have both chats independently merge or start competing modules.

Before integration, all acceptance criteria and required checks must pass on the reviewed patch. Independent review must have no unresolved actionable findings. A failing test, missing required evidence or CHANGES_REQUIRED verdict blocks integration and advancement. Repair and retest first. Record pre-existing warnings and explicitly excluded real-device/field checks; passing scoped checks never proves the entire app has no bugs.

## Sequence

1. Verify the candidate SHA, clean feature worktree, change scope and evidence. Preserve the actual review result; do not overwrite a management rejection with an implementer's PASS.
2. Inspect current main, origin/main and worktree ownership. Preserve unrelated edits; do not force push/reset/stash. Prepare integration against current main in isolation when necessary. Resolve overlapping documents semantically: retain the current module-name/Korean-commit policy and feature evidence. Escalate substantive product conflicts rather than guessing.
3. Run relevant integration checks, including scripts/verify.ps1 for app changes, and review any conflict-resolution changes. Publish only the verified integration result. If main advances during validation, refresh the candidate and validate affected changes before updating main. Never push a known failing integration.
4. Record the real integrated SHA and update task status/baseline consistently; push main normally and confirm the remote SHA. Update the same branch journal with checks, revision and integration status. Preserve verification evidence before any future worktree retirement; retirement is not implied by this policy.
5. Select exactly one next module whose dependencies are integrated and whose required product decisions are resolved. Create its plain module-name branch from the updated reviewed main and dispatch implementation under current rules. No routine user confirmation is needed. Do not silently approve decisions in tasks.json, skip a blocked module, or implement optional Firebase/login/RAG features merely because they appear in the backlog.

Historical adoption gate: H01 had a P2 test-isolation finding. It was repaired, independently accepted and integrated at 0c5f633. H02's coreGame gate was reconciled to the already-confirmed design in CORE_GAME_SCOPE.md; it is no longer awaiting confirmation. Use current tasks.json and PRODUCT_DECISIONS.md for subsequent scope, rather than replaying historical gates.

## Coordination limits

This file defines permission and sequencing; it does not start a background monitor or connect chats automatically. Use only available coordination tools and user-authorized destinations. If no authorized destination or active run exists, preserve the next dispatch/handoff and accurately report that implementation has not started. Do not claim an autonomous next module is running merely because its plan is ready.
