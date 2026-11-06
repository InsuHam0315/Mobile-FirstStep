# H04 management review

Date: 2026-11-06 (Asia/Seoul). Current verdict: PASS on repaired candidate 567ed5779f396a346b34ae1b670a70bcb730394d; original CHANGES_REQUIRED finding below is retained as history.

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

At the initial review H04 was in repair, not accepted. Its existing evidence/worktree are preserved. Main's user-approved H05 facility search/favorites/real-map choice remains recorded in PRODUCT_DECISIONS.md and survives task-record reconciliation. H01-H03 remain accepted and must not be reopened.

## Repair acceptance and integration verification

Code v3 329992a90a9ab698687d849d817fac27cd2ebd02 and documentation-only v4 567ed5779f396a346b34ae1b670a70bcb730394d were independently re-reviewed by management's Sol PM and reused Terra specialist. Verdict PASS; P2 closed with no remaining actionable findings. Exact three-file repair patch SHA256: 13FFED7A7C8986F3AC7C64EBE3E7A2E8CB64120AC6DEFAE53BB17D2D9AE1A41E. The same-key original-attempt history reconciles a conflicting pending submission; transient failures retain pending data. Failed-result retry reads the current retry or advanced pass. H02 code/rules remain unchanged.

Repair evidence in the preserved feature worktree .local/harness/H04/repair-v3 confirms four failing unit regressions before the repair and all three actual APK conflict cases failing ERROR/STORAGE. After repair: 90 units pass; focused three APK cases pass in portrait and denied large-font landscape; existing seven smoke cases pass in denied and granted portrait conditions, no skips. Final handoff accurately distinguishes simulated inputs and hardware/delivery limits. Management inspected the saved-pass large-landscape APK screenshot; scrolling is required at that font size. Reviewers made no edits/builds/device changes.

Fresh integration prepared from current main 47ecf21433fe5d932e8ed528b313a420e76fa43e in C:/Users/InsuHam/FirstStep-worktrees/management-h04-integration. The only merge conflict was the H04 task record; resolution preserves main's H05 approval, Notion mapping and management review while adding repaired candidate/evidence. App/scripts exactly match accepted v4. scripts/verify.ps1 with Studio JDK and explicit SDK passed with all 51 Gradle tasks executed, 90 units, zero failures/errors/skips, APK success, Lint zero errors and 94 existing warnings. Evidence: .local/harness/H04-integration/verify.log and totals.json. No additional integration instrumentation run is claimed.

Feature branch published through v4 and remote SHA verified separately from acceptance. Integration commit SHA is recorded by the following main task/journal update. Next eligible scope is H05 map-home after main publication and journal closure. Synthetic content/debug routing, untested physical-device/campus/audible-TTS/elapsed-alarm/process-death-draft limits remain unchanged.
