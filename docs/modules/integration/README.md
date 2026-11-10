# Registered episode integration

H06 and H10C connect debug entry, simulated arrival/discovery, explicit introduction continuation, quiz and return to authoritative Home. This layer chooses content and storage and owns the durable navigation transitions.

## Registered contexts

| Dataset | Entry and mode | Progress database |
| --- | --- | --- |
| `h04_fixture_v1` | Debug IntegrationEntryActivity. NORMAL or DEMO and a real or explicit debug clock. | `h04-debug-<UUID>.db` |
| `software_episode_demo_v1` | Debug button in the department guide. DEMO only. The normal entry uses a real clock with instant DEMO eligibility. | `software-episode-demo-<UUID>.db` |

Both target `digital_information_software`. [IntegrationContext.create](../../../app/src/main/java/com/example/firststep/game/composition/integration/IntegrationContext.java) validates dataset, canonical UUID, target, mode and clock payload. The codec, route access and storage open reject the software DEMO when debug mode is disabled. [StableRunStore](../../../app/src/main/java/com/example/firststep/game/composition/integration/StableRunStore.java) resolves a persistent run per dataset. Explicit test runs leave the entry run untouched.

## Callers and transitions

[GuideActivity](../../../app/src/main/java/com/example/firststep/game/ui/guide/GuideActivity.java) resolves the software run on a worker, builds the typed context, calls `IntegrationRouteAccess.bound(context, requested)`, writes the resulting context with `IntegrationIntentCodec.writeContext(intent, bound)`, then opens Home when the Activity is still current. [IntegrationComposition.open](../../../app/src/main/java/com/example/firststep/game/composition/integration/IntegrationComposition.java) resolves validated content and the matching Room repository off the UI thread. The caller owns its close.

[NavigationStore](../../../app/src/main/java/com/example/firststep/game/composition/integration/NavigationStore.java) persists `EMPTY → PENDING → ACCEPTED → CONSUMED`. A pending request binds the canonical nonce and context to the typed discovery result. Cancellation and stale or mismatched results cannot advance it. [IntegrationRouteAccess.requireIntroduction](../../../app/src/main/java/com/example/firststep/game/composition/integration/IntegrationRouteAccess.java) requires consumed discovery for the software DEMO. The existing fixture route can also reopen a recorded introduction. Home can resume a consumed route explicitly after a launch interruption.

## Isolation and evidence

The same UUID in both datasets resolves different databases, navigation keys and entry keys. The registry accepts packaged content only. It does not route into the default, legacy or account store. H10 guidance/practice and H10B narration preview remain separate transient flows.

H06 passed its isolated fixture loop and restart checks. H10C retained Android contract checks and the sourced DEMO loop through retry, one stamp and restart readback. Its manual discovery used labeled fallbacks. Combined V1 validation, field arrival and authored NORMAL progress remain open. [H06 review](../../harness/reviews/H06-management.md) · [H10C handoff](../../harness/handoffs/H10C.md)
