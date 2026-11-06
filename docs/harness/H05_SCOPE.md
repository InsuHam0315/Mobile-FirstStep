# H05 home, map and local profile slice

Read AGENTS.md, tasks.json, PRODUCT_DECISIONS.md, CORE_GAME_SCOPE.md and UI_BOUNDARIES.md. Start only after H04 is accepted/integrated and management dispatches H05. Branch: map-home; Korean versioned commits starting [map-home v1]. Management owns acceptance, publication, integration, Notion and subsequent dispatch.

## Approved experience and boundary

The user selected option 1: connect existing facility search, favorites and the real map into the new game home/map. Build a coherent debug-accessible Java/XML home -> search/detail/map/favorite flow alongside progress, waiting quizzes, local profile and ending surfaces. Preserve legacy launchers and data. This is one presentation/composition slice, not a full production-routing migration or a new university-content rollout.

The first actual content target remains digital-information/software department. Reuse H04's explicit digital_information_software identity and current synthetic-content disclosure; do not map it to H02 fixture IDs or H03 synthetic_cover_demo. Sharing the H04 catalog/repository composition through a bounded adapter is allowed to make home progress and supported quiz navigation refer to the same records. Navigation into existing H03/H04 debug screens must be labeled as prototype/test entry, not verified GPS arrival or completed H06 end-to-end integration. Facilities without an authored mapping have no exploration action. Do not fabricate relationships by fuzzy matching names.

H05 does not implement Google sign-in/onboarding, Firebase/server/RAG, legacy photo/ARCore migration, surveyed arrival gates, all-college expansion, five actual departments, or a final visual redesign. Local profile may display existing safe local values or explicit unknown/not-configured state; it is not a verified university account or first-login authentication flow.

## Reuse and data honesty

- Reuse FacilityRepository's existing catalog and facility_favorites/ids preferences, FacilitySearch matching/filter semantics and safe official-link behavior. Do not erase favorites, rename facility IDs or silently migrate device-local favorites to an account. Surface unavailable/corrupt data safely.
- The existing eight facility records have no coordinates. Source-link checks are not entrance surveys. Search results can show details and campus-map context without a fabricated pin or arrival claim. Campus center is a viewport default, never a destination. If no explicit valid coordinate mapping exists, state location is unavailable; do not turn 0,0 or screen pixels into latitude/longitude.
- Reuse CampusMapView's real-map rendering, pan/zoom, current-location and cache behavior with attribution retained. A bounded generic marker API/adapter may be added while preserving its legacy Mission-based API and behavior; never create fake legacy missions just to draw a marker. No tile-provider rewrite/new paid API.
- Keep illustrative map placement distinct from geographic coordinates. Until a reference asset/mapping is provided, use the approved real-map surface and truthful missing-placement states rather than inventing an illustrated map layout.
- Use H02 ProgressService.read, eligible and endingAvailable, plus ProgressRepository.readAll, for the selected catalog/mode. Repository work stays off the UI thread; do not duplicate scoring/timing/reward logic. Never reinterpret legacy completions as new stamps. Unknown departments do not default to software.

## Behavior and acceptance

Home shows selected NORMAL/DEMO context, saved completion state, independent waiting/eligible quizzes and favorites. Refresh from authoritative progress on resume/return and recompute remaining time from the stored timestamp. A waiting building must not block browsing another facility. Controls with unavailable routes explain the missing prerequisite instead of silently navigating to another ID. Error/retry/loading/empty states stay usable.

Search supports query/category/favorites filters, readable result rows, detail and explicit favorite toggles. Favorites survive restart and update home/search consistently. Returning from details/map preserves a reasonable selection/query; repeated rendering must not toggle favorites or issue navigation twice. Existing directory behavior remains available.

Map starts at the existing campus viewport, can pan/zoom, and offers an explicit current-location action. Request permissions in context, handle denial/approximate-only/no fix/stale fix/provider or network failure without trapping navigation. Distinguish received location from emulator-injected test coordinates; never report campus accuracy from injection. Remove stale location indicators according to the reused adapter's documented validity policy. Preserve OSM attribution/cache/network bounds and safely cancel obsolete callbacks across pause/recreation.

Local profile/record surface honestly identifies device-local progress and unknown profile fields. It must not collect or claim a Google-authenticated identity in H05. Ending derives from the selected content set's accepted rule; one completed target does not mean five buildings completed. Use isolated synthetic five-destination tests to demonstrate locked/unlocked ending behavior if needed, clearly labeled and kept separate from the first real content target. Do not reset shared databases to manufacture an ending screenshot.

Presentation uses replaceable state/actions, resource tokens, XML and semantic IDs. Keep storage/device/network in adapters, screen logic in presentation/composition. Provide scrolling/large-font/landscape support, useful content descriptions and back navigation. This slice does not require a new visual design reference.

## Ownership

In addition to tasks.json UI/layout paths, authorized supporting scope is H05-specific game.presentation/composition/contracts and facility/map adapters, bounded content metadata/adapters, game_home/map/profile/ending strings and additive resource tokens, focused unit/instrumentation tests and verification scripts. The implementation lead owns narrowly additive debug manifest/navigation wiring and any necessary backwards-compatible generic-marker support in map/CampusMapView.java. Preserve existing APIs, facility data/preferences, tile policy, H02 schemas and H01-H04 behavior. If a substantive cross-module rule change is required, report it rather than silently expanding scope.

## Evidence and completion

Test progress/waiting/eligibility/mode isolation; unknown department/facility/missing coordinates; search filters and favorites persistence; map/selected-target identity and stale callbacks; location denial/no-fix; ending lock/unlock with clearly synthetic fixtures; recreation and return refresh. Use real API35 APK screenshots for home, search/detail/favorite, real campus map tiles when network permits, location unavailable/denied and simulated-location modes, profile/ending states and large text/landscape. Record exact input provenance. A screenshot of a real map tile is not evidence of surveyed destination coordinates.

Run scripts/verify.ps1 with Studio JDK and explicit SDK, meaningful focused instrumentation, and independent exact-patch review. Preserve existing app data and unrelated devices/worktrees. Record failures and fixes as observed, not invented, and distinguish cached tasks from fresh tests. Evidence under .local/harness/H05; English handoffs/H05.md with exact SHA, actual agents, command/counts, screenshots and limits. Only update H05 factual state in tasks.json. Report coherent owner commits for management publication; no own push/main merge/Notion/H06 work. Stop at H05 handoff.
