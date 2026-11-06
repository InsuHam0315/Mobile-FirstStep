package com.example.firststep.game.composition;

import android.os.Handler;
import android.os.Looper;
import com.example.firststep.game.presentation.GameCoordinator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Preview-only composition and executor ownership. */
public final class PreviewHost implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final GameCoordinator coordinator;
    public PreviewHost(GameCoordinator.Renderer renderer, String selectedId) {
        Handler main = new Handler(Looper.getMainLooper());
        coordinator = new GameCoordinator(new FakePreviewCatalog(worker, main::post), renderer, selectedId);
    }
    public GameCoordinator coordinator() { return coordinator; }
    @Override public void close() { coordinator.stop(); worker.shutdownNow(); }
}
