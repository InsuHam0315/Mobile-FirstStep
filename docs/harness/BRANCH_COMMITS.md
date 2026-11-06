# Module branches and versioned commits

Effective scope: new module work after the H01 run already in progress on 2026-11-06. H01, existing branches and historical commits are grandfathered. Do not interrupt the active implementation, rename its branch, amend its commits or rewrite published history for this rule.

## Branch names

Use one stable branch per module: `codex/game-<module>`, lowercase kebab-case. Versions belong in commit subjects, not branch names. Use the branch declared in tasks.json; do not independently invent a second spelling.

| Task | Module | Branch |
| --- | --- | --- |
| H01 (existing) | foundation | codex/game-foundation |
| H02 | progress | codex/game-progress |
| H03 | discovery | codex/game-discovery |
| H04 | intro-quiz | codex/game-intro-quiz |
| H05 | map-home | codex/game-map-home |
| H06 | integration | codex/game-integration |
| H07 (pending choice) | firebase | codex/game-firebase |
| H08 (pending choice) | chatbot | codex/game-chatbot |
| H09 | reskin | codex/game-reskin |

These are implementation responsibility boundaries, not a requirement to create a separate Gradle module for every branch. Existing decision gates and dependencies still apply. Keep main as the reviewed integration branch. Do not create future branches merely to reserve names. A branch can contain several bounded tasks; never create branches per agent or per commit version.

## Commit subjects

Format: `[<module> v<N>] <type>: <specific change>`

Examples:

```text
[progress v1] feat: persist per-building exploration progress
[progress v2] fix: prevent duplicate quiz rewards
[progress v3] test: verify progress restoration after restart
[discovery v1] feat: add camera discovery with fallback input
```

Allowed types: feat, fix, refactor, test, docs, chore. Keep subjects concise and English. Do not use bare `v1`, `v2`, or vague descriptions such as `update`.

- Start at v1 for each module and increase by one for each authored module commit, including fixes and documentation commits. These numbers identify development revisions, not completion percentages or app releases.
- Before committing, inspect existing local and fetched history for that module prefix, including main and previous integration history. Continue above the highest recorded number; do not restart at v1 if a branch is recreated.
- Only the implementation lead commits the module changes. Workers return edits and evidence to avoid competing version numbers. If concurrent work needs a different boundary, management decides it first.
- Each commit must be a coherent change with relevant checks. Do not combine unrelated modules merely to assign one version. Preserve the full commit SHA in handoffs; a display version is not a substitute for an immutable Git identifier.
- Do not create Git tags, change Android versionName/versionCode, publish releases, push or merge merely because a commit is named v1 or v2. Existing authorization rules still apply.
- Main-only management documents may retain descriptive conventional subjects such as `docs: define module branch and commit conventions`; they do not consume a module revision number. Merge commits retain their integration meaning and do not require a new module version.

## Handoff and journal

Record module, stable branch, latest revision label, full commit SHA, verification and integration status in the technical handoff. Management maintains one Korean Notion journal per actual branch and appends meaningful revision milestones to that same page. Do not create a journal page per version.

Before dispatching H02 or any later module, management ensures the implementation worktree includes this policy. The already-running H01 worktree does not need to fetch or merge it mid-run.
