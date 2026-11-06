package com.example.firststep.game.domain;

import java.util.List;

/** Implementations run work off the caller thread and deliver asynchronously on the UI executor.
 * At most one terminal callback is expected; consumers also reject duplicates.
 * Cancellation is best effort: consumers must also reject stale request IDs. */
public interface PreviewCatalogPort {
    interface Cancelable { void cancel(); }
    interface Callback {
        void onResult(long requestId, List<PreviewItem> items);
        void onError(long requestId);
    }
    Cancelable load(long requestId, Callback callback);
}
