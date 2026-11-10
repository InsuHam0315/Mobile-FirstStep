package com.example.firststep.game.composition.home.account;

import static org.junit.Assert.*;

import com.example.firststep.facility.Facility;
import com.example.firststep.game.sync.DatasetRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public final class AccountHomeReaderTest {
  @Test public void productionRegistrationRemainsClosed() {
    assertFalse(AccountHomeReader.registered(DatasetRegistry.production()));
  }

  @Test public void favoritesOnlyIncludeSavedIdsPresentInOfflineCatalog() {
    assertEquals(Arrays.asList("Library", "Student center"),
        AccountHomeReader.favoriteNames(Arrays.asList(
            facility("library", "Library"), facility("food", "Cafeteria"),
            facility("center", "Student center")),
            new HashSet<>(Arrays.asList("center", "removed", "library"))));
  }

  private static Facility facility(String id, String name) {
    return new Facility(id, name, "", "", Collections.emptyList(), Collections.emptyList(),
        "", "", "", "", "");
  }
}
