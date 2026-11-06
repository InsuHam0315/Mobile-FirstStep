package com.example.firststep.game.presentation;

/** Actions identify content by stable ID, never by its position or translated label. */
public final class GameAction {
    public enum Type { SELECT_ITEM, REFRESH }
    private final Type type;
    private final String itemId;
    private GameAction(Type type, String itemId) { this.type = type; this.itemId = itemId; }
    public static GameAction selectItem(String id) { return new GameAction(Type.SELECT_ITEM, id); }
    public static GameAction refresh() { return new GameAction(Type.REFRESH, null); }
    public Type getType() { return type; }
    public String getItemId() { return itemId; }
}
