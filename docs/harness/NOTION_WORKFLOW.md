# Branch development journals in Notion

## Destination and record unit

- Parent: [AI기반모바일프로그래밍](https://www.notion.so/3cded980d97e808bb86dc8db842ef22c).
- Database: [첫발자국 · 브랜치별 개발일지](https://www.notion.so/410ed980d97e801dbd3ce4f854c92a90).
- One human-readable page per **actual development branch**, not per task, subtask, test, agent or session. Append meaningful dated entries to the existing branch journal. Keep an updated current-status summary at its top.
- Fetch the schema and branch page before writing. Use the exact branch name and stable IDs in `notion.json`. Before creating a missing journal, search/query this database by 브랜치 to avoid duplicates.
- Create a feature journal only after the branch/worktree actually exists and its development starts. Put future module plans in the main journal and `tasks.json`; do not pre-create pages for the entire backlog.
- A branch may cover several task IDs; record them in 관련 작업 and journal entries. Internal H00-H09 packets, dependencies and decision gates remain in `tasks.json`; Notion page count is not task count.
- Preserve existing AI/PPT reference pages. The former H01-H09 planning cards are kept in the filtered 이전 작업 계획 view, not the active board. Do not resume writing progress into them or recreate their per-task workflow.
- No row, status or prose grants implementation/deployment authority. Resolve discrepancies against current code, user decisions and commit evidence.

## Ownership, language and cadence

The management chat alone updates journals and their mapping in notion.json. The separate implementation chat, its lead, PMs and workers must not write Notion; they provide concise English evidence in the task handoff. Within this document, journal coordinator means the management chat, not an implementation lead. Internal prompts, repository technical records and handoffs stay English; **Notion titles, status summaries, rationale, development narratives, verification and next steps are Korean for the user/team**. Keep exact technical names, commands and paths when useful; explain unfamiliar terms on first use.

Write at meaningful implementation milestones, material blockers, completed reviews, and integration. Do not log each tool call or create daily pages. Never assign human owners, deadlines or notify teammates without instructions. Human-facing richness is not constrained by the specialist handoff word limit.

## Developer-journal style (explicit user requirement)

Write like a useful engineering journal: explain the chain of reasoning and observed work in connected paragraphs, rather than filling an agent task template or copying checklists. The reader should understand the development story without opening code or this chat.

Keep this narrative, adapting it to actual evidence:

1. **현재 상황**: a short visible summary of what works, what remains open, branch/integration state, and the next action.
2. **구현하려던 것**: the concrete user problem or module goal and scope.
3. **사용한 기술과 선택 이유**: what was actually used, its role, constraints and documented rationale. If the original rationale is unknown, explain the observed architectural role or label a present recommendation; do not invent a historical comparison or decision.
4. **구현 과정**: meaningful behavior/data-flow changes and how they fit the existing app, with a concrete example where useful.
5. **검증·시연 결과**: what was run, environment/input, observed result, date/commit and evidence link. Distinguish fresh checks from historical records or cached build output.
6. **문제와 수정 과정**: symptom -> observed evidence -> confirmed cause or explicitly labeled hypothesis -> attempted change -> retest result. Include failed alternatives only if actually attempted. If no failure is documented, omit that narrative rather than inventing one.
7. **남은 문제와 다음 행동**: unresolved issues, decisions, device limits, and the next bounded change; proposed work is labeled as planned.
8. **날짜별 개발 기록 및 근거**: accumulate concise meaningful entries in the same page. Keep detailed file paths, commands, model metadata and branch/worktree/commit references in a developer-evidence toggle.

Use descriptive Korean headings, short paragraphs, whitespace, limited bold and small tables (normally 2-3 columns) for comparable results. Avoid excessive headings, nested checklists, empty fields, unexplained acronyms, repetitive boilerplate and decorative clutter. Keep key status, results and limitations visible outside toggles.

Rich content means specific rationale, behavior, evidence, failures and remaining work, not length for its own sake. Do not imply a human performed AI work; do not fabricate first-person experiments, quotations, screenshots, success, root causes or alternative-library trials to make the narrative convincing. No canned "all tests passed" without scope and evidence.

## Status and Git evidence

Properties: 이름, 상태, 담당자, 관련 작업, 브랜치, 현재 상황, 검증 현황, 커밋, 문서 구분. Active journals use 문서 구분=브랜치 개발일지; historical planning cards use 이전 작업 계획.

- 시작 전: a real branch is prepared but implementation has not started.
- 진행 중: implementation/review remains open. For long-lived main, explicitly label ongoing integration/maintenance; this is not a claim that an unattended process is running.
- 완료: the recorded branch scope has been reviewed/closed or integrated, with remaining out-of-scope limitations stated. It never means the whole app or all hardware checks passed.
- Blocked/paused work stays accurately described in 현재 상황, with a cause and next action; do not hide blockers behind a green status.

Create the feature worktree from a clean reviewed checkout **before** changing its local manifest to in_progress; the harness requires ready and clean. Maintain task changes in that worktree and preserve main's independent changes. Branch creation alone does not complete a task. Keep `tasks.json` and handoffs accurate even though Notion aggregates by branch.

Record actual branch/worktree/base and candidate commits, checks/review and local/pushed/integrated state. 커밋 identifies the stated journal snapshot; use prose for candidate versus integrated SHAs. Update integratedSha in tasks.json only after actual integration. Never link an unpushed commit as if available remotely. A local branch can be included in main without an origin branch of the same name.

Screenshots of actual APK UI may use simulated inputs; label both. Camera overlays are not tracked AR. Emulator checks are not campus measurements. Do not claim deployment, Google/Firebase/Gemini access or free operation without evidence. Keep credentials and raw personal data out of Notion.

## Review and recovery

Before saving, check that the Korean narrative is readable, current status agrees with evidence, historical tests are dated, links work, and no planning text appears as shipped functionality. Preserve user notes and previous evidence. After partial writes, retain successful IDs and retry only missing changes; fetch uncertain outcomes before retrying.

Implementation chats mark their handoff `Journal update: pending management review` without attempting Notion writes. If Notion is inaccessible to the management chat, it records the pending update and discloses it in the final Korean report. Independent authorized implementation may continue. Do not create a substitute board or claim a successful sync. Scope/decision blockers still block dependent work.

## Verified inventory at transition (2026-11-06)

- main: integration baseline and next-step journal.
- codex/midpoint: previous app implementation journal; branch tip af0d3a2 is on origin and included in main.
- codex/game-harness: preparation/verification journal; local branch tip 6dc8af1 is included in main, with no separate remote branch observed.
- codex/game-foundation does not yet exist. H01 remains ready/unimplemented; create its journal when actual feature work starts.
- Firebase/auth/Firestore/cloud photos/Gemini RAG and game/legacy choices retain their existing gates.
