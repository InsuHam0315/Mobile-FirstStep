package com.example.firststep.game.composition;

import com.example.firststep.game.domain.PreviewCatalogPort;
import com.example.firststep.game.domain.PreviewItem;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Simulated data only. Work and callback executors are supplied by the host. */
public final class FakePreviewCatalog implements PreviewCatalogPort {
    private final Executor worker;
    private final Executor ui;
    public FakePreviewCatalog(Executor worker, Executor ui) { this.worker = worker; this.ui = ui; }
    @Override public Cancelable load(long id, Callback callback) {
        AtomicBoolean canceled = new AtomicBoolean();
        worker.execute(() -> {
            PreviewItem item = new PreviewItem("sample_place", PreviewItem.Title.SAMPLE_PLACE);
            ui.execute(() -> {
                if (!canceled.get()) callback.onResult(id, Collections.singletonList(item));
            });
        });
        return () -> canceled.set(true);
    }
}
