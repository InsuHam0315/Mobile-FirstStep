# Game foundation

The H01 foundation provides a replaceable Java/XML presentation pattern and shared visual tokens. Its debug preview is a small example of the pattern. Production feature screens have their own controllers and hosts.

## Interfaces and callers

- [GameCoordinator](../../../app/src/main/java/com/example/firststep/game/presentation/GameCoordinator.java) accepts a `PreviewCatalogPort`, a renderer and an optional restored selection ID. `start()` loads the catalog, `dispatch(GameAction)` routes selection or refresh, and `stop()` invalidates pending work. The renderer receives [GameScreenState](../../../app/src/main/java/com/example/firststep/game/presentation/GameScreenState.java) with loading, empty, ready, error or selected state.
- [PreviewCatalogPort](../../../app/src/main/java/com/example/firststep/game/domain/PreviewCatalogPort.java) exposes `load(long requestId, Callback callback)`, returning a cancel handle. Run the load off the UI thread and deliver callbacks on the UI thread with the original request ID. The coordinator rejects obsolete or duplicate terminal callbacks.
- [GamePreviewActivity](../../../app/src/main/java/com/example/firststep/game/ui/preview/GamePreviewActivity.java) obtains the coordinator from [PreviewHost](../../../app/src/main/java/com/example/firststep/game/composition/PreviewHost.java), calls `start()`/`stop()` with the screen lifecycle, and saves `getSelectedId()` in its instance state. A row click dispatches `GameAction.selectItem(item.getId())`. The host closes its executor when destroyed.

## Integration and evidence

Use stable item IDs and render the state supplied by the controller. Use `Theme.FirstStep.GamePreview`, `GameActionButton`, and the shared color, spacing and touch dimensions in [game_tokens.xml](../../../app/src/main/res/values/game_tokens.xml). The preview host uses a fake catalog and keeps its selection transient. Feature data, device effects and progress storage belong to their respective hosts.

H01's isolated checks exercised the preview lifecycle, state and permission independence. Combined app validation remains scheduled after the shared V1 assembly. [H01 review](../../harness/reviews/H01-management.md) · [Current verification order](../../harness/IMPLEMENTATION_FIRST.md)
