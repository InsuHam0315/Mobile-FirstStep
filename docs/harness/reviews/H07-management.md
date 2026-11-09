# H07 management review

Current addendum (2026-11-09): candidate 91a62b796ab70f4d47d06195553d86ebcb16ec50 is CHANGES_REQUIRED for one P2 lifecycle finding below. Real configuration is supplied, but live OAuth/profile and repaired APK execution remain unverified. No H07 main integration or H07B dispatch. Earlier reviews below are historical.

## v4 button repair review, 2026-11-09

- Management inspected exact five-file app/test delta against d5fccf9 plus owner handoff. Sol management review independently matched patch SHA256 8C66A0C87201CF169B80D74A1AD992CE938AAA2A7A983FEBCCDBB0ACAF662CAE and inspected logs: 131 units/28 suites, zero failures/errors/skips; configured verify 24 executed/27 reused; lint 0 errors/93 warnings. No new management runtime execution.
- GetSignInWithGoogleOption and existing CustomCredential/GoogleIdTokenCredential type handling match the official button flow. NoCredentialException has a distinct outcome/message. Original observed failure only establishes validated default network, zero device accounts and provider request activity; terminal exception was not captured, so no confirmed OAuth/server-failure diagnosis is claimed.
- **P2: terminal sign-in failure is discarded across external picker pause.** IdentityActivity.onPause calls SessionController.pause (around line 87), advancing generation/nonce. The pending signIn callback (around line 122) rejects its failure even after resume. FirebaseAuthPort.publish (around line 60) emits nothing for unchanged null UID. The user can therefore return without the new missing-credential/cancel/error guidance. This is a deterministic conditional source finding, not an observed runtime cause.
- Required repair: preserve only the current auth attempt's terminal non-sensitive outcome across pause/resume; invalidate on newer attempt/logout/session change. Keep account/profile isolation protections. Add deferred-completion regressions for delivery while paused/after resume and superseded-attempt/logout rejection. Root independently confirmed the code path and sent one bounded repair to the existing owner, with no device interference.
- v4 was normally published to origin/firebase and remote SHA verified. Publication is a tested checkpoint, not acceptance. The fixed APK remains uninstalled while the human's existing Google interaction is preserved. Do not replace it during active sign-in.

Reference: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation#button-flow

2026-11-08. Local source/evidence review **PASS**; overall acceptance **BLOCKED_CONFIG**. Keep H07 in progress. No main app integration or H07B advancement.

## Exact candidate

- Branch: firebase; candidate `d05dd6b02664918584eeb60e3b11ddab968252ba`.
- Implementation: `b92b9f7595bf0ed146baa88759f18ecdf9914666`, base `b7fffd048d026065c66b8ed03796d40f0b9628b6`.
- Management independently recomputed the source `git diff --binary` SHA256: `2D60ECB4F713F18EC8AD12C61035CCC55ADA720EAB0AA67A37C95219E105BB7E`, matching the reviewed owner patch. Candidate adds only handoff/status metadata after that source.
- Both v1 and then v2 were normally published; remote firebase was verified at exact candidate d05dd6b. Publication is not acceptance or main integration.

## Independent review

Management root inspected session/controller lifecycle, Firebase authentication serialization, Credential Manager cancellation, server-confirmed profile transactions, UID isolation, configuration/runtime wiring, rules and release factory. Management verification PM (Sol) plus Terra performed a separate bounded read-only source/evidence review and found no actionable findings on this exact candidate. No new builds or device runs were performed by these reviewers.

Retained XML was independently summed: 27 suites, 130 unit tests, zero failures/errors/skips. Rules log reports seven passes with no skips. Exact final APK logs each report eleven unique named cases in Portrait and font-1.6 LargeLandscape across two invocations (one outage + ten general), not one uninterrupted eleven-case process. Final verify reused unit outputs: 51 tasks, three executed and 48 up-to-date. Lint reports zero errors/93 warnings. Existing 235 database hashes are recorded unchanged; full legacy coverage is not implied. Root sampled the actual landscape incomplete-form capture; it is scrolled, with readable fields and preserved leading zeros, not a complete accessibility assessment.

The handoff accurately records failed attempts and confines recovery evidence to two explicit UI retries after transport restoration plus an SDK network reset. It does not claim automatic/one-click recovery. The configuration-removal fixture deliberately omits OAuth metadata; it does not certify a genuine Google-configured build transition. Local SDK identities are synthetic email accounts, not Google OAuth.

## Remaining required acceptance

Main's app/google-services.json is absent. Actual configured build and configured-to-unconfigured isolation check, signing/provider setup, reviewed deployed rules, human interactive Google login, live profile write/restart/read/incomplete recovery/logout remain unverified. Complete these against the identified user project without exposing credentials or accepting fake success. Do not deploy to an unidentified project, enable billing, promote legacy/fixture progress, or start sync. Physical-device/campus checks are separate, not inferred from local APK execution.

No bounded source repair is currently required. Resume the same firebase slice when configuration is available, recheck current main and candidate/evidence, and review any resulting code changes before acceptance. Preserve all worktrees and failed/successful evidence.
