package com.example.firststep.game.presentation;

import com.example.firststep.game.domain.PreviewCatalogPort;
import com.example.firststep.game.domain.PreviewItem;
import java.util.Collections;
import java.util.List;

/** All calls, including adapter callbacks, must arrive on the UI thread. */
public final class GameCoordinator {
    public interface Renderer { void render(GameScreenState state); }
    private final PreviewCatalogPort catalog;
    private final Renderer renderer;
    private long requestId;
    private boolean active;
    private boolean awaiting;
    private PreviewCatalogPort.Cancelable pending;
    private String restoredId;
    private GameScreenState state = new GameScreenState(GameScreenState.Status.LOADING, Collections.emptyList(), null);

    public GameCoordinator(PreviewCatalogPort catalog, Renderer renderer) {
        this(catalog, renderer, null);
    }
    public GameCoordinator(PreviewCatalogPort catalog, Renderer renderer, String restoredId) {
        this.restoredId = restoredId;
        this.catalog = catalog;
        this.renderer = renderer;
    }
    public void start() { if (!active) { active = true; refresh(); } }
    public void stop() {
        active = false;
        requestId++;
        awaiting = false;
        if (pending != null) { pending.cancel(); pending = null; }
    }
    public void dispatch(GameAction action) {
        if (!active) return;
        if (action.getType() == GameAction.Type.REFRESH) { if (state.canRefresh()) refresh(); return; }
        if (!state.canSelect()) return;
        for (PreviewItem item : state.getItems()) {
            if (item.getId().equals(action.getItemId())) {
                restoredId = item.getId();
                state = new GameScreenState(GameScreenState.Status.SELECTED, state.getItems(), item.getId());
                renderer.render(state);
                return;
            }
        }
    }
    public String getSelectedId() { return restoredId; }
    private void refresh() {
        long id = ++requestId;
        awaiting = false;
        if (pending != null) { pending.cancel(); pending = null; }
        awaiting = true;
        state = new GameScreenState(GameScreenState.Status.LOADING, Collections.emptyList(), null);
        renderer.render(state);
        pending = catalog.load(id, new PreviewCatalogPort.Callback() {
            @Override public void onResult(long resultId, List<PreviewItem> items) { GameCoordinator.this.onResult(resultId, items); }
            @Override public void onError(long resultId) { GameCoordinator.this.onError(resultId); }
        });
    }
    private void onResult(long id, List<PreviewItem> items) {
        if (!active || id != requestId || !awaiting) return;
        awaiting = false;
        pending = null;
        String selected = null;
        for (PreviewItem item : items) {
            if (item.getId().equals(restoredId)) { selected = restoredId; break; }
        }
        restoredId = selected;
        state = new GameScreenState(items.isEmpty() ? GameScreenState.Status.EMPTY : selected == null ? GameScreenState.Status.READY : GameScreenState.Status.SELECTED, items, selected);
        renderer.render(state);
    }
    private void onError(long id) {
        if (!active || id != requestId || !awaiting) return;
        awaiting = false;
        pending = null;
        state = new GameScreenState(GameScreenState.Status.ERROR, Collections.emptyList(), null);
        renderer.render(state);
    }
}
