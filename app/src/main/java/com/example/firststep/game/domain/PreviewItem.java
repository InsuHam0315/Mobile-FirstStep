package com.example.firststep.game.domain;

/** Identifies sample content without embedding display copy in the domain. */
public final class PreviewItem {
    public enum Title { SAMPLE_PLACE }
    private final String id;
    private final Title title;

    public PreviewItem(String id, Title title) {
        if (id == null || id.isEmpty() || title == null) throw new IllegalArgumentException();
        this.id = id;
        this.title = title;
    }
    public String getId() { return id; }
    public Title getTitle() { return title; }
}
