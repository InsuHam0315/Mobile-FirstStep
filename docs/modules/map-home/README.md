# Game home and map

H05 presents local progress, facility search, detail, favorites, map, profile placeholder and ending status. H10C uses the same host/controller seam with a smaller DEMO Home and its own progress namespace.

## Interfaces and callers

- [GameHomeActivity](../../../app/src/main/java/com/example/firststep/game/ui/home/GameHomeActivity.java) creates `HomeHost(context, run, fake, now, integrationContext)`, then `HomeController(host, renderer)` and calls `host.attach(controller)`. The controller exposes `snapshot()`, `resume()`, `pause()`, `filters(query, category, favoritesOnly)`, `select(id)` and `favorite(id, desired)`. The renderer reads [HomeState](../../../app/src/main/java/com/example/firststep/game/presentation/home/HomeState.java).
- [HomeHost](../../../app/src/main/java/com/example/firststep/game/composition/home/HomeHost.java) loads facilities, favorite IDs, authoritative progress, eligibility and ending state on workers. `load(generation, mode, query, category, only)` returns data through `controller.loaded(generation, data)` or `failed(generation)`. The generation protects mode/filter changes and recreation. The foreground tick re-evaluates cached progress against the selected clock.
- [FacilityRepository](../../../app/src/main/java/com/example/firststep/facility/FacilityRepository.java) loads the bundled catalog. `setFavorite(id, selected)` serializes the read-modify-write of the device-local `facility_favorites` ID set across both directory and Home. The software DEMO Home skips facility/favorite storage and uses only its selected progress composition.

## Map and evidence

[CampusMapView](../../../app/src/main/java/com/example/firststep/map/CampusMapView.java) renders the existing OSM tile viewport with attribution and guards marker selection against clipping. H05 supplies an empty destination marker list because the facility coordinates are unverified. [H05LocationAdapter](../../../app/src/main/java/com/example/firststep/game/device/homemap/H05LocationAdapter.java) uses `start()`/`stop()` for explicit foreground location requests. It rejects stale or invalid samples and clears the indicator on expiry or pause. A received or simulated fix is a current-location indicator.

H05 isolated APK checks covered favorites, clipping, permission denial, simulated location and local progress. The H10C demo has a separate completion/resume panel with no five-destination ending. Physical location accuracy, surveyed facility pins and combined validation remain pending. [H05 review](../../harness/reviews/H05-management.md) · [H10C handoff](../../harness/handoffs/H10C.md)
