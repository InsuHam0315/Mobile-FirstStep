# H05 management review

Date: 2026-11-06 (Asia/Seoul). Verdict: CHANGES_REQUIRED. Do not integrate or dispatch H06 yet.

## Latest: v3/v4 repair review

Reviewed code 145d8b858f43211405a6223665368e21e7f7c66d and documentation candidate 831c81939d9c80305bedaf5f4b54ed2ed99d266d. Published candidate 831c819 to origin/map-home and verified remote SHA. Exact six-file repair patch SHA256: BAC709F5D6154AF9CF49CA8C109604A4A61C68F49B872BD4FCB323B7E048229D. Evidence: feature worktree .local/harness/H05/repair-v3/.

Both original P2 findings below are closed: repository-wide same-process locking protects all favorite read/modify/write callers, and clipped-map attribution is visibly retained in the actual large-font landscape screenshots. The retained evidence includes pre-fix failures, 111 passing units, and six existing plus two focused APK checks in both conditions. This is not cross-process durability or campus validation.

P2 remains: CampusMapView.java:85-100 paints an opaque credit band over verified legacy mission markers but still hit-tests those hidden markers. Tapping the credit can select an invisible mission. Tiny neutral-only viewports also retain marker selection. Management Sol/Terra independent review and root code tracing confirm the path; management has not yet performed a runtime reproduction. Bounded repair dispatched to the same implementation chat: exclude credit/neutral areas from marker interaction, preserve visible marker selection/gestures, add focused pre-fix-failing touch regression and APK evidence using isolated synthetic markers. Continue v5 onward. Do not reopen closed repairs.

Evidence correction: repair-v3/lint.xml contains zero errors and 95 warnings, including a new DrawAllocation warning at CampusMapView.java:53, not the handoff's stated 94 existing warnings. Correct historical counts; a narrow allocation repair may be verified separately.

Management prepared an uncommitted isolated merge of d5e7484 and 831c819 at C:/Users/InsuHam/FirstStep-worktrees/management-h05-integration. Fresh scripts/verify.ps1 passed with all 51 Gradle tasks executed and 111 units, zero failures/errors/skips. Preserve this worktree and its .local/harness/H05-integration/verify.log. This test merge is NOT acceptance, a main integration commit, or main publication. H06 remains undispatched. The sections below retain the original v1/v2 review history.

## Exact checkpoint and evidence

Code v1 cea99d7f72852e7723d1f0d4bbe481e30de68563 is based on a296956735261550f765e12135f7891801bb30fe. Documentation/status-only v2 d493cfe26c9cb014e5c7ba189f4b09e383a1d9c7 changes handoffs/H05.md and H05's task entry. Both published to origin/map-home, remote v2 SHA verified. Publication is not acceptance.

Committed/reviewed patch SHA256: 6F47244A9C6BCCE35706ACD693604688825EC8DDFB75759F86242868FD9D2C71. Feature evidence is preserved in C:/Users/InsuHam/FirstStep-worktrees/h05-map-home/.local/harness/H05. Initial evidence: 107 units, no failures/errors/skips; APK six scenarios repeated in denied portrait, approximate portrait and fine-permission large-font landscape; 39 final screenshots. Lint zero errors and 94 existing warnings. Management inspected actual APK home and OSM viewport screenshots. The map has no facility destination pins because coordinates are unavailable. Simulated input, prototype routes and unknown profile/content remain labeled; no campus/physical-GPS acceptance is inferred.

## P2: shared favorite-set writes can lose unrelated IDs

Management's read-only independent review identified a cross-caller race. HomeHost.java (approximately 24-40) serializes H05 favorite operations, but retained FacilityDirectoryActivity.java (approximately 238) still invokes FacilityRepository.setFavorite on the main thread. FacilityRepository.java (approximately 65-73) performs an unsynchronized read-modify-apply of the entire preference set. A legacy writer and H05 worker can read the same old set and overwrite one another's unrelated changes. H05's local coordinator does not serialize the legacy writer.

This finding is based on code tracing; no management runtime reproduction is claimed. Existing H05-only favorite regressions do not close this separate shared-repository issue. Implementation's PASS does not override management's finding.

Bounded repair sent to the authorized implementation chat: add compatible repository-level atomicity across all instances/callers, encompassing the complete read-modify-write; preserve preference file and ids key, legacy API/catalog, and H05 failure recovery. Narrow FacilityRepository.java ownership is explicitly authorized for this repair. Add a deterministic interleaving/two-repository-caller regression with pre-fix failure, required checks, appropriate APK regression and independent exact-patch review. Continue at module v3, update the same handoff, preserve data/evidence. No H06 or unrelated code changes.

## P2: attribution scrolls out while the map remains visible

game_home_activity.xml (approximately line 36) places the OSM attribution below the fixed 360dp map as a sibling in the outer ScrollView. CampusMapView does not draw attribution itself. The retained screenshots-final/final-fine-large-landscape-selected-facility-map-without-destination-coordinate.png visibly shows the map occupying the viewport with attribution entirely offscreen. H05_SCOPE explicitly requires retaining attribution. Keep attribution visible with the displayed map across scrolling/large-font/landscape, using appropriate viewport sizing/overlay or persistent placement; a clipped overlay at an offscreen map bottom is insufficient. Preserve map gestures and readable text. Add actual APK visual/scroll evidence. This second bounded repair was sent to the implementation owner.

## Status and limits

H05 remains in repair and no management integration has been created. H01-H04 remain accepted. Final management Sol/Terra review: CHANGES_REQUIRED for the two P2 findings above; no additional actionable location, catalog/mode/storage identity, profile or ending issue found. Final handoff exists, but its implementation-side PASS does not override this verdict. Reviewers made no edits/builds/device changes. New user choices are not required for these fixes. H06 planning must distinguish a single simulated technical loop from authored/verified campus content and must not silently expand to five real destinations.
