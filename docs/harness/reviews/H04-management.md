# H04 management review

Date: 2026-11-06 (Asia/Seoul). Verdict: CHANGES_REQUIRED. Do not integrate or dispatch H05 yet.

## Candidate and evidence

- Implementation v1: 00f60116f36181b6e17efbb69dec3e8edebedc14, based on 86a4a132535e4c4bde77dfd9bd65dc993f15c4ee.
- Documentation-only v2: b89584a1f6ba71b5e9059844c08391bae2dd7a54. Changes only handoffs/H04.md and the H04 task record. Published to origin/intro-quiz; exact remote SHA verified. Publication is not acceptance.
- Reviewed patch SHA256: 3E1BECC322F1029B9082C77A57F0998E29689F783476DB81803D21F46F10FC8E.
- Retained feature evidence supports 82 unit tests and the same seven APK tests under three conditions, with zero skips. Lint: zero errors, 94 existing warnings. Management's independent Sol PM and Terra review was read-only; no additional management build/device execution is claimed.
- Reminder registration/injected receiver delivery does not prove actual elapsed 15-minute background delivery. Synthetic school content, emulator inputs, debug routing and process-death draft limits remain explicit in the handoff.

## P2: conflicting pending submission cannot recover

IntroQuizController.resume (approximately lines 298-304) submits pending answers before reading authoritative progress. Two controllers or notification entries can load the same attempt. If A commits answers X, B's different answers Y are correctly rejected by the accepted H02 service. The controller catch (247-254) retains B's pending payload; ERROR Retry (388-394) calls resume, repeating the conflict before a read can reconcile the committed outcome. This creates an indefinite error/retry loop.

Both management reviewers confirmed the code path. This is static evidence, not an already-observed APK reproduction. The implementation-side PASS does not close this management finding.

Bounded repair dispatched to the authorized implementation chat: reconcile authoritative same-mode/building history/current attempt after submission conflict, display the actual saved outcome, and preserve pending data for transient storage failures. Do not weaken H02 conflict rejection, scoring or idempotency. Add two-controller and restored-pending/recreation regressions, covering failed-result/new-retry and transient-failure preservation as applicable. Re-run required checks and relevant APK recovery evidence, independently review the revised patch, and update the existing H04 handoff. Continue module commits at v3. No H05 work or main integration until acceptance.

## Coordination and next state

H04 is in repair, not accepted. Its existing evidence/worktree are preserved. Main's user-approved H05 facility search/favorites/real-map choice remains recorded in PRODUCT_DECISIONS.md and must survive later task-record reconciliation. H01-H03 remain accepted and must not be reopened. The intro-quiz branch journal records this finding and will be updated in place when repairs are verified.
