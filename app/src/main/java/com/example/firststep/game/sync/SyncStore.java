package com.example.firststep.game.sync;

import java.util.function.UnaryOperator;

/** Blocking store. Each update applies the transition exactly once in one transaction. */
public interface SyncStore {
  OutboxState read(SyncScope scope);
  OutboxState update(SyncScope scope, UnaryOperator<OutboxState> change);
}
