# Module branches and versioned commits

Effective scope: new module work after the H01 run already in progress on 2026-11-06. H01, existing branches and historical commits are grandfathered. Do not interrupt the active implementation, rename its branch, amend its commits or rewrite published history for this rule.

## Branch names

Use one stable branch per module: `<module>` (no prefix), lowercase kebab-case. Versions belong in commit subjects, not branch names. Use the branch declared in tasks.json; do not independently invent a second spelling.

| Task | Module | Branch |
| --- | --- | --- |
| H01 (existing) | foundation | codex/game-foundation |
| H02 | progress | progress |
| H03 | discovery | discovery |
| H04 | intro-quiz | intro-quiz |
| H05 | map-home | map-home |
| H06 | integration | integration |
| H07 (pending choice) | firebase | firebase |
| H08 (pending choice) | chatbot | chatbot |
| H09 | reskin | reskin |

These are implementation responsibility boundaries, not a requirement to create a separate Gradle module for every branch. Existing decision gates and dependencies still apply. Keep main as the reviewed integration branch. Do not create future branches merely to reserve names. A branch can contain several bounded tasks; never create branches per agent or per commit version.

## Commit subjects

Format: `[<module> v<N>] <type>: <specific change>`

Examples:

```text
[progress v1] 기능: 건물별 탐방 진행 상태 저장
[progress v2] 수정: 퀴즈 보상 중복 지급 방지
[progress v3] 검증: 재시작 후 진행 상태 복원 확인
[discovery v1] 기능: 카메라 발견과 대체 입력 추가
```

Use Korean commit subjects and bodies. Allowed types: 기능, 수정, 리팩터링, 검증, 문서, 설정. Keep the module slug and vN label as defined; technical identifiers may remain in English. Keep subjects concise and specific. This overrides the general English technical-record preference for Git commit messages. Do not use bare `v1`, `v2`, or vague descriptions such as `update`.

- Start at v1 for each module and increase by one for each authored module commit, including fixes and documentation commits. These numbers identify development revisions, not completion percentages or app releases.
- Before committing, inspect existing local and fetched history for that module prefix, including main and previous integration history. Continue above the highest recorded number; do not restart at v1 if a branch is recreated.
- Only the implementation lead commits the module changes. Workers return edits and evidence to avoid competing version numbers. If concurrent work needs a different boundary, management decides it first.
- Each commit must be a coherent change with relevant checks. Do not combine unrelated modules merely to assign one version. Preserve the full commit SHA in handoffs; a display version is not a substitute for an immutable Git identifier.
- Do not create Git tags, change Android versionName/versionCode, publish releases, push or merge merely because a commit is named v1 or v2. Existing authorization rules still apply.
- Main-only management documents may retain descriptive Korean subjects such as `문서: 모듈 브랜치와 커밋 작성 규칙 정리`; they do not consume a module revision number. Merge commits retain their integration meaning and do not require a new module version.

## Handoff and journal

Record module, stable branch, latest revision label, full commit SHA, verification and integration status in the technical handoff. Management maintains one Korean Notion journal per actual branch and appends meaningful revision milestones to that same page. Do not create a journal page per version.

Before dispatching H02 or any later module, management ensures the implementation worktree includes this policy. The already-running H01 worktree does not need to fetch or merge it mid-run.
