# Compact dispatch prompts

## Coordinator entry

```text
Use the FirstStep harness. Read AGENTS.md, docs/harness/README.md, and tasks.json.
Execute only task {ID} if authorized and ready. Preserve existing uncommitted work.
Follow ROUTING.md. Use English internal prompts/handoffs; Korean app copy, final report,
and substantive human-readable Notion branch journals per NOTION_WORKFLOW.md.
Create isolated task worktrees only after baseline/dependency checks. Keep UI replaceable.
Read NOTION_WORKFLOW.md and resolve the branch journal from notion.json. The coordinator
records goals, choices/rationale, observed results, real fixes and next steps in Korean.
Append milestones to the same branch page, never create per-task/session pages.
Create a missing branch journal only after the actual branch exists; specialists return evidence only.
Do not expand into pending Firebase/chatbot choices or later backlog tasks.
Verify the exact patch, obtain independent review, and report changed behavior, evidence,
remaining limitations, and the next bounded step. Do not claim configured roles have run.
```

## PM -> implementer

```text
Task: {ID / single outcome}
Worktree: {absolute path}; branch: {branch}; base: {SHA}
Read: AGENTS.md, docs/harness/UI_BOUNDARIES.md, {minimal relevant files}.
Write ownership: {explicit paths}. Shared files: escalate to PM; coordinator owns edits.
Acceptance: {observable behavior}; non-goals: {excluded features}.
You are not alone in the codebase. Preserve others' edits and adapt to their changes.
Do not change branches, global settings, credentials, or unrelated files.
Use English for technical work; preserve Korean product strings. Follow repository checks.
Return <=200 words: changed paths, behavior, checks+results, blockers, actual model used.
```

## PM -> independent verifier

```text
Review task {ID} in {absolute worktree}, base {SHA}, candidate {SHA or diff}.
Read its packet, acceptance criteria, and only relevant code/evidence.
Do not edit files or repeat completed checks without a failure/new risk.
Check behavior, state restoration, UI/domain separation, ownership, and false test claims.
Return PASS / CHANGES_REQUIRED / UNVERIFIED with concise file:line evidence and limits.
Use English. Missing runtime/hardware evidence is UNVERIFIED, not PASS.
```

## Handoff record

```text
Task / branch / worktree / base / candidate:
Actual agent roles/models used:
Changed paths and user-visible outcome:
Checks: command, environment, exit/result, evidence path:
Review verdict and unresolved limits:
Notion branch-journal URL / sync result / feature commit vs main integration:
Next owner and minimal required context:
```

Use one handoff per task under `docs/harness/handoffs/`. Keep verbose logs and screenshots under ignored `.local/harness/` until intentionally selected for a user-facing report. Evidence must remain accessible through integration; copy required logs before retiring a worktree.
