package com.example.firststep.game.presentation.authored;

import static org.junit.Assert.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class AuthoredSyncPublicationTest {
  @Test public void gameplayCannotDisplaceExplicitAcknowledgement() {
    AuthoredSyncPublication publication = new AuthoredSyncPublication();
    CompletableFuture<String> reconcile = CompletableFuture.completedFuture("pending");
    AuthoredSyncPublication.Ticket automatic = publication.observeAutomatic(reconcile);
    assertTrue(publication.complete(automatic));
    CompletableFuture<String> flush = new CompletableFuture<>();
    AtomicReference<String> displayed = new AtomicReference<>("pending");
    AuthoredSyncPublication.Ticket send = publication.beginExplicit();
    flush.thenAccept(value -> {
      if (publication.complete(send)) displayed.set(value);
    });
    assertNull(publication.observeAutomatic(reconcile)); // Failed submit: same reconciliation.
    assertNull(publication.observeAutomatic(CompletableFuture.completedFuture("new pending")));
    assertTrue(publication.explicitRunning());
    assertEquals("pending", displayed.get());
    flush.complete("server acknowledgement");
    assertFalse(publication.running());
    assertEquals("server acknowledgement", displayed.get());
    assertNull(publication.observeAutomatic(reconcile));
  }

  @Test public void pauseInvalidatesLateExplicitReadback() {
    AuthoredSyncPublication publication = new AuthoredSyncPublication();
    AuthoredSyncPublication.Ticket read = publication.beginExplicit();
    publication.reset();
    assertFalse(publication.complete(read));
    assertFalse(publication.running());
    assertNotNull(publication.beginExplicit());
  }
}
