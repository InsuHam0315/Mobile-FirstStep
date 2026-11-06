package com.example.firststep.game.composition.integration;

import com.example.firststep.game.domain.discovery.DiscoveryResult;

public final class IntegrationResult {
  public final IntegrationContext context;
  public final String nonce;
  public final DiscoveryResult discovery;

  public IntegrationResult(IntegrationContext context, String nonce, DiscoveryResult discovery) {
    this.context = context;
    this.nonce = nonce;
    this.discovery = discovery;
  }
}
