# Product decisions after H02 dispatch

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

## Clarification practice

The user explicitly requested questions before implementation when product intent is ambiguous or a decision is genuinely required. Consult source documents and recorded decisions first; do not repeatedly ask about confirmed rules. Routine technical implementation, fixes and evidence-based verification remain autonomous. Keep future questions scoped to the next dependent module while progressing independent authorized work.
