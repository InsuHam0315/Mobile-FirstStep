# Product decisions after H02 dispatch

Sequencing clarification 2026-11-10: IMPLEMENTATION_FIRST.md supersedes older per-module live acceptance as a prerequisite for implementing independent approved code. The product choices below remain unchanged; live checks are deferred to combined validation, not claimed passed. User confirmed the immediate login complaint is a missing exploration entry after Home, not failed profile saving; APP_ENTRY_SCOPE.md addresses that gap.

Source: direct user messages in the management chat, 2026-11-06 (Asia/Seoul). These decisions supplement the game design PDF and CORE_GAME_SCOPE.md. Do not infer implementation or verification from a decision record.

## First department, then expansion

The first real content and end-to-end acceptance target is 디지털정보관 -> 컴퓨터소프트웨어특성화대학 -> 소프트웨어학과, using the user's naming. Complete and validate this department's building information, story, missions, quiz and saved progress before selecting one department from each other college for later implementation and execution checks. This is staged rollout, not permission to fabricate campus facts or simultaneously implement all departments.

Keep shared behavior reusable and college/department/building content configurable. H02 may retain synthetic multi-building tests to prove independent progress; synthetic fixtures are not verified campus content. The existing five-destination design is not silently replaced with five departments. Resolve the later destination/course composition with the user before implementing that expansion if still unspecified.

## Google sign-in and first-login profile

The user selected Google sign-in. After authentication, users without a complete profile must enter name, student number and department before entering the app. Complete returning profiles go to home; incomplete profiles return to onboarding. Google identity alone does not verify university enrollment or ownership of the entered student number.

This selects the sign-in method and onboarding requirement, not a configured Google/Firebase project, deployed backend, cloud progress sync or RAG. Firebase provider/storage selection and project setup must be reconciled before the authentication slice; do not label Google sign-in itself pending user choice. Do not expand active H02 into authentication/profile UI. Define account ownership, logout/switch-account behavior and local-to-account progress handling in that later slice before integration; never silently share one user's records with another.

## Existing facilities in the new home/map

Direct user selection on 2026-11-06: option 1, integrate existing facility search, favorites and the real map into the new game home/map. The intended experience is facility search -> map/detail -> favorite -> exploration where a supported mission exists. Home combines progress, waiting quizzes and favorite facilities; map combines exploration destinations and facility lookup/current location when available.

This approves H05's legacyFeatures gate for these three capabilities only. Reuse existing data/adapters and preserve records; do not delete legacy launchers during this slice. Unverified coordinates stay labeled unverified, and facilities without authored missions must not fabricate an exploration flow. Unknown departments must not silently map to software. Optional photo capture, legacy tracked ARCore, cloud/backend migration and authentication are not approved by this choice. The first complete content target and sequential module gates remain unchanged.

## Firebase identity, profile and later progress sync (2026-11-08)

Direct user selection: option 1, store both profile and exploration progress in Firebase. Implement and verify Google sign-in plus first-login profile first; only after acceptance/integration proceed to exploration-record synchronization. Use Firebase Authentication (Google provider) and Cloud Firestore. H07 is identity/profile; H07B is the subsequent sync slice on the same stable firebase branch/journal, with increasing Korean versioned commits. Selection does not mean the project exists or live OAuth works.

Keep the no-cost Spark plan, no billing upgrade or photo uploads/Functions/RAG in these slices. User configures the console project/Android registration/provider/Firestore. Management supplies verified package/fingerprints and setup instructions. Do not ask again whether Google or Firebase is selected.

Profiles are private by Firebase UID, not student number/email; require name, student number (string, preserving leading zeros) and department before authenticated entry. No assumed student-number length or university enrollment verification. Use truthful department entry without inventing a complete official department catalog. Profile completion requires valid persisted data, not an unchecked local boolean. Cancellation/failed saves/incomplete profiles stay recoverable. Logout/account switch must stop previous user listeners and invalidate delayed callbacks/cache before another profile is shown.

Never attach anonymous legacy data or H06 fixture progress to a signed-in account automatically. H07 writes no exploration records; H07B will define and test account/dataset/mode isolation and conflict handling before sync. Keep original databases and legacy prototypes intact.

## Clarification practice

### Independent sourced demo episode continuation (2026-11-10)

The user's standing continuous V1 implementation authorization permits H10C on the existing campus-content branch while live H07 QA remains human-controlled. Source and architecture reconciliation found one eligible bounded slice: explicit simulated arrival, already-approved direction/SHAKE discovery, approved seven-sentence software narration and three sourced questions, followed by isolated local DEMO progress. This does not select a new game policy: reuse existing DEMO eligibility, 2/3 pass, retry and idempotent reward. The distinct registered software_episode_demo_v1 dataset must not import or promote fixture/legacy/account records. H10 practice and H10B preview retain their no-reward behavior. H10C_SCOPE.md defines the additive seams and evidence gate. No H07 acceptance or H07B cloud synchronization is implied, and real arrival coordinates/field verification, later departments and optional services remain gated.

### Source reconciliation and independent narration continuation (2026-11-09)

Management re-read the original eight-page game PDF through a bounded read-only source audit. Pages 1-2 already approve shy building fairies, a guide provisionally called 첫발이, free-order episodes and short warm polite narration. Pages 3-4 specify direction alignment followed by SHAKE for digital-information; COVER applies to the libraries. The overall fairy framework and digital-information summon action are not missing product choices. Earlier blanket statements that the story/character/sensor treatment was entirely unresolved were too broad and must not trigger repeated approval questions.

Final names/art, exact authored building scripts and unverified facts/arrival coordinates/radii remain distinct unfinished content/field work. The sample 0,0 coordinates and example floor claims are not facts. Later rollout choices do not automatically replace the original five-destination lineup with five departments.

The user's continuation requests authorize useful independent required V1 implementation and parallel chats where useful. H10B continues the actual campus-content branch with a sourced seven-sentence fairy introduction preview, TTS/captions and return to existing practice, under H10B_SCOPE.md. This uses the already-approved fictional framework and existing verified facts; no rewarded gameplay, coordinate guesses, auth dependency or automatic fixture promotion. H07 continues isolated verification while the human's current session is preserved. No extra optional provider/service/design scope is inferred.

### Social sign-in entry clarification (2026-11-09)

The user clarified that anyone opening the app should see `Google로 계속하기` and continue into Google's official account-selection/add-account flow, including devices with no previously registered Google account. A separate Play Store login is a temporary workaround for the old installed candidate, not the selected product UX. Use the Google-branded button and clean hierarchy of the provided reference within FirstStep's visual system; the reference's other provider and legal text are not requirements. Google passwords remain outside FirstStep-owned UI. This is bounded H07 identity UX, not approval of Kakao/GitHub login, broad redesign or remote permission changes. Live provider behavior and profile persistence still require evidence.

### Authorized independent work during configuration wait (2026-11-08)

The user asked whether Firebase setup blocks all development and authorized proceeding with other implementable modules. H07 identity/profile candidate d05dd6b remains preserved on firebase, locally reviewed but blocked on real project/live acceptance. This is a sequencing exception, not acceptance or permission to skip cloud verification. H07B still waits for accepted/integrated H07.

Select H10/campus-content as the one active implementation slice: official-source-backed software-department guidance and three-question local practice in the actual APK. It depends on accepted H06 only. Management supplies the bounded fact packet in SOFTWARE_CONTENT_SOURCES.md; implementation must not invent campus facts or survey coordinates. The entry is available without Firebase and explicitly does not award stamps or write exploration/account progress. H06 fixtures remain unchanged. This provides required first-department content before later authenticated/field integration, not a complete authored game episode.

The story/character treatment, building-specific sensor mission rationale, surveyed arrival point/radius and later destinations are still unresolved for real gameplay. H10 does not choose those, expand to other departments, start RAG/photos/ARCore/reskin or imply enrollment verification. When H10 closes, reassess H07 configuration and remaining genuinely dependent choices rather than automatically opening the optional backlog.

The user explicitly requested questions before implementation when product intent is ambiguous or a decision is genuinely required. Consult source documents and recorded decisions first; do not repeatedly ask about confirmed rules. Routine technical implementation, fixes and evidence-based verification remain autonomous. Keep future questions scoped to the next dependent module while progressing independent authorized work.
