package com.example.firststep.game.notifications.authored;

import com.example.firststep.game.domain.Progress;
import com.example.firststep.game.sync.SyncScope;

public interface AuthoredReminder {
  enum Result { SCHEDULED, ALREADY_SCHEDULED, PERMISSION_DENIED, UNAVAILABLE, INELIGIBLE }
  Result schedule(SyncScope scope, Progress committed);
}
