package com.example.firststep.game;

import static org.junit.Assert.*;
import com.example.firststep.game.domain.PreviewCatalogPort;
import com.example.firststep.game.domain.PreviewItem;
import com.example.firststep.game.presentation.GameAction;
import com.example.firststep.game.presentation.GameCoordinator;
import com.example.firststep.game.presentation.GameScreenState;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameCoordinatorTest {
    private static final PreviewItem ITEM = new PreviewItem("sample", PreviewItem.Title.SAMPLE_PLACE);
    private static class Fake implements PreviewCatalogPort {
        final List<Callback> callbacks = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        int cancellations;
        @Override public Cancelable load(long id, Callback callback) {
            ids.add(id); callbacks.add(callback); return () -> cancellations++;
        }
        void success(int index) { callbacks.get(index).onResult(ids.get(index), Collections.singletonList(ITEM)); }
        void error(int index) { callbacks.get(index).onError(ids.get(index)); }
    }
    @Test public void stoppedSupersededAndDuplicateCallbacksCannotChangeState() {
        Fake fake = new Fake(); List<GameScreenState> rendered = new ArrayList<>();
        GameCoordinator coordinator = new GameCoordinator(fake, rendered::add);
        coordinator.start(); coordinator.stop(); fake.success(0);
        assertEquals(1, rendered.size()); assertEquals(1, fake.cancellations);
        coordinator.start(); fake.success(0); assertEquals(2, rendered.size());
        fake.success(1); coordinator.dispatch(GameAction.selectItem("sample"));
        int count = rendered.size(); fake.success(1); fake.error(1);
        assertEquals(count, rendered.size());
        assertEquals(GameScreenState.Status.SELECTED, rendered.get(count - 1).getStatus());
    }
    @Test public void restoredSelectionIsReconciledAndUnknownIdDiscarded() {
        Fake fake = new Fake(); List<GameScreenState> states = new ArrayList<>();
        GameCoordinator coordinator = new GameCoordinator(fake, states::add, "sample");
        coordinator.start(); fake.success(0);
        assertEquals(GameScreenState.Status.SELECTED, states.get(states.size()-1).getStatus());
        Fake other = new Fake(); GameCoordinator unknown = new GameCoordinator(other, states::add, "missing");
        unknown.start(); other.success(0);
        assertNull(unknown.getSelectedId());
        assertEquals(GameScreenState.Status.READY, states.get(states.size()-1).getStatus());
    }
    @Test public void immutableSnapshotAndErrorRecovery() {
        List<PreviewItem> source = new ArrayList<>(); source.add(ITEM);
        GameScreenState snapshot = new GameScreenState(GameScreenState.Status.READY, source, null);
        source.clear(); assertEquals(1, snapshot.getItems().size());
        try { snapshot.getItems().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        Fake fake = new Fake(); List<GameScreenState> states = new ArrayList<>();
        GameCoordinator coordinator = new GameCoordinator(fake, states::add);
        coordinator.start(); fake.error(0); assertTrue(states.get(states.size()-1).canRefresh());
        coordinator.dispatch(GameAction.refresh()); fake.success(0);
        assertEquals(GameScreenState.Status.LOADING, states.get(states.size()-1).getStatus());
        fake.success(1); assertEquals(GameScreenState.Status.READY, states.get(states.size()-1).getStatus());
    }
    @Test public void cancellationCallbackAndEmptyResultRemainRecoverable() {
        List<GameScreenState> states = new ArrayList<>();
        List<PreviewCatalogPort.Callback> callbacks = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        PreviewCatalogPort port = (id, callback) -> {
            callbacks.add(callback); ids.add(id);
            return () -> callback.onResult(id, Collections.singletonList(ITEM));
        };
        GameCoordinator coordinator = new GameCoordinator(port, states::add);
        coordinator.start(); coordinator.stop();
        assertEquals(1, states.size());
        coordinator.start();
        callbacks.get(1).onResult(ids.get(1), Collections.emptyList());
        assertEquals(GameScreenState.Status.EMPTY, states.get(states.size()-1).getStatus());
        assertTrue(states.get(states.size()-1).canRefresh());
    }
}
