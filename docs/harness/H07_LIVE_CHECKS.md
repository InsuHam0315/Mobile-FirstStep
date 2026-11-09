# H07 live evidence reconciliation — 2026-11-09

Management and independent verification PM reconciled H07_SCOPE/H07_RESUME against user reports and the actual v6/v8 evidence. This matrix prevents unnecessary repeated human login while retaining required live checks. Exact installed app code ab117d3; installed existing user session on emulator-5564 must be preserved.

| Requirement | Evidence and remaining action |
| --- | --- |
| Real project/provider/signing/configured and clean unconfigured builds | Confirmed by recorded console/config/build evidence. No repeat project creation/config request. |
| Human Google sign-in and persistent profile | User reported login/profile entry; configured v6/v8 server-confirmed reads and returning Home confirm a persisted live profile. Initial first-save event chronology was not directly observed. That reporting limit does not require retyping personal data to recreate it. |
| Returning profile and automatic Home | Confirmed after data-preserving update/relaunch on v8 with exact installed APK hash. |
| Incomplete/missing onboarding and cached/failed-save gating | Local controller/SDK/APK coverage exists; the scope's live incomplete-onboarding recovery remains open. Use a separate human-selected test account with no FirstStep profile, not deletion/corruption of the existing profile. |
| Repaired authenticated logout | Source/JVM/local fake coverage exists. v5 failed on Android off-main UI delivery; v8 has not exercised authenticated real logout. Confirm in separate QA session. Starting already logged out or timeout is not successful provider clear. |
| Cross-account protection | Local A/B SDK/rules and stale-callback coverage confirmed. A second real Google-account comparison is additional evidence, not a new mandatory full-scope gate. Explicitly retain this live limitation. |
| Zero-account entry | Conditional on a suitable isolated device. Do not remove a human's existing account to manufacture this state. |

## Current independent verification work

Original implementation chat is assigned a focused Android instrumentation regression using production GoogleCredentialSource.clear on a newly owned no-human-account Google-Play API37 QA emulator. This executes actual Android Handler/provider delivery, unlike JVM tests. Record actual success versus provider error/deadline distinctly; it cannot alone certify authenticated Google logout. Prepare that owned QA device's configured login entry. No actions on emulator-5564 and no handling credentials.

Management has asked whether the user can select a separate Google test account through official UI, with explicitly proposed test profile values. Do not create/select an account or submit those values before the answer. If available, one QA session can check missing-profile re-entry, authorized test profile save and normal logout, leaving the original signed-in device untouched. No production rule weakening, data reset or personal re-entry is required.

H07 remains unaccepted until required live checks pass. H07B cannot start yet. H10B narration implementation is independent under the user's parallel-work authorization; its completion does not prove authentication or cloud progress.
