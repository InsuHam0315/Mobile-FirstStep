package com.example.firststep.game.composition.introquiz;

import com.example.firststep.game.domain.discovery.DiscoveryResult;

/** H06 may use this bounded adapter; synthetic cover is never promoted into a real target. */
public final class DiscoveryIntroTarget {
  private DiscoveryIntroTarget() {}

  public static final class Handoff {
    public final String buildingId;
    public final DiscoveryResult provenance;

    Handoff(String id, DiscoveryResult provenance) {
      this.buildingId = id;
      this.provenance = provenance;
    }
  }

  public static Handoff from(DiscoveryResult result) {
    if (result == null || !"digital_information_software".equals(result.targetId))
      throw new IllegalArgumentException("unsupported discovery target");
    return new Handoff(result.targetId, result);
  }
}
