# Notion task recording

## Destination and authority

- Parent: [AI기반모바일프로그래밍](https://www.notion.so/3cded980d97e808bb86dc8db842ef22c).
- Board: [첫발자국 · 개발 작업](https://www.notion.so/410ed980d97e801dbd3ce4f854c92a90), the user's existing inline database, formerly 새 데이터베이스.
- Stable IDs and task-page URLs: `notion.json`. Fetch the selected row and current schema before writes. Reuse a row by Task ID; do not create another board or duplicate task when resuming.
- `tasks.json` defines executable scope, dependencies and decision gates. Notion provides the shared progress/evidence record; a row or checkbox never authorizes implementation or deployment. Resolve discrepancies against current code, commits and user decisions rather than silently overwriting either source.
- Preserve the existing AI handoff and PPT summary child pages. They are historical/reference material, not automatic evidence that the new game is complete.

## Ownership, language and cadence

Only the coordinator writes task status and shared records. PMs/workers return concise evidence to it; never let multiple agents overwrite the same row. Technical task records remain English; retain existing Korean Notion property names, app copy and Korean final reports. Do not assign people, deadlines, send comments, or notify teammates without explicit instructions.

Read the selected task at session start. Write only when work starts, a meaningful decision/blocker changes, a review finishes, or the task completes. Do not log each tool call, poll unchanged rows, copy the whole chat, or reread all project pages.

## Status mapping

| Repository state | Notion 상태 | Meaning |
|---|---|---|
| queued / ready | 시작 전 | Planned or ready, implementation not started |
| in_progress | 진행 중 | Actual implementation or review is underway |
| verified | 완료 | Acceptance and required checks passed with independent review; integration may still be pending |
| deferred before start | 시작 전 | Gate states the unresolved decision or blocker |
| deferred after start | 진행 중 | Gate explicitly says blocked/paused; never imply active unattended execution |

Create a task worktree from a clean reviewed checkout **before** changing the local manifest to in_progress (the worktree command requires ready and a clean checkout). Update the manifest/handoff in that task worktree; do not dirty the original main checkout to track its activity. Resume an existing worktree by inspecting it, not by forcing new-worktree to overwrite it.

## Required evidence

Every task page has Objective, Scope and prerequisites, Acceptance, Evidence and current state, Sources, and Updates. Keep these concise and preserve user notes.

- At start: actual branch, absolute worktree, base SHA, bounded goal, owner roles and acceptance criteria. Set 상태 to 진행 중 only once implementation starts.
- At a blocker: what is blocked, what remains possible, decision needed, and evidence. Keep Gate distinct from status; do not mark unverified work complete.
- At completion: candidate commit, changed behavior, actual models/roles (or unavailable), exact check commands/results, reviewer verdict, retained evidence paths/links, and unresolved runtime/device limits. Update checkboxes only with evidence; set 상태 to 완료 only after required checks and review pass.
- Commit property records the verified candidate SHA. Verification explicitly states whether work is local-only, pushed, or integrated. Only record integratedSha in tasks.json after actual integration. Never fabricate remote links for unpushed commits.
- Keep screenshots of real APK UI distinct from simulated inputs; never equate camera overlays with tracked AR or emulator checks with campus measurements.

No credentials, API tokens, private configuration, or raw personal data belong in the board. Reference relevant repo paths/commit URLs; retain essential local evidence before retiring a worktree.

## Recovery

Read current tool responses and heed warnings. After a partial batch, retain successful IDs and retry only missing work. If an update is uncertain, fetch the affected page before retrying.

If Notion cannot be accessed, continue independent authorized local work and record the exact pending update in `docs/harness/handoffs/<TASK>.md` under `Pending Notion sync`. Retry when access returns; the Korean final report must state that sync is pending. Do not claim the board is updated or create a substitute database. Scope/decision blockers still block their dependent work.

## Initial state (2026-11-06)

H00 is verified against the existing baseline and handoff. H01 is ready but not implemented. H02-H09 are planned, not approved for automatic execution. Firebase/auth/Firestore/cloud photos/Gemini RAG and game/legacy selections retain their existing gates. Board setup itself does not change those decisions.
