package com.example.firststep.game.presentation;

import com.example.firststep.game.domain.PreviewItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameScreenState {
    public enum Status { LOADING, READY, SELECTED, EMPTY, ERROR }
    private final Status status;
    private final List<PreviewItem> items;
    private final String selectedId;
    public GameScreenState(Status status, List<PreviewItem> items, String selectedId) {
        this.status = status;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.selectedId = selectedId;
    }
    public Status getStatus() { return status; }
    public List<PreviewItem> getItems() { return items; }
    public boolean canRefresh() { return status == Status.EMPTY || status == Status.ERROR; }
    public boolean canSelect() { return status == Status.READY || status == Status.SELECTED; }
    public String getSelectedId() { return selectedId; }
}
