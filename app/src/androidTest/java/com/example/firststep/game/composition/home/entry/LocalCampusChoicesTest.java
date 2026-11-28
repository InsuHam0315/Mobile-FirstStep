package com.example.firststep.game.composition.home.entry;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.game.auth.Profile;
import com.example.firststep.game.presentation.home.entry.CampusChoice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalCampusChoicesTest {
  private static final class IsolatedContext extends ContextWrapper {
    final String filename = "campus_test_" + UUID.randomUUID();
    IsolatedContext(Context base) {
      super(base);
    }

    @Override
    public Context getApplicationContext() {
      return this;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
      return super.getSharedPreferences(filename, mode);
    }
  }

  @Test
  public void persistsAndIsolatesGuestAndAccounts() {
    IsolatedContext context = new IsolatedContext(ApplicationProvider.getApplicationContext());
    LocalCampusChoices store = new LocalCampusChoices(context);
    assertEquals(new CampusChoice("", ""), store.load(null));
    assertEquals(new CampusChoice("", ""), store.load("uid-1"));
    assertEquals(new Profile("", "", ""), store.loadGuestProfile());
    CampusChoice guest = new CampusChoice("Guest college", "Guest department");
    CampusChoice account = new CampusChoice("Account college", "Account department");
    assertTrue(store.save(null, guest));
    assertTrue(store.save("uid-1", account));
    assertTrue(store.saveGuestProfile(new Profile("", "00123", "")));
    LocalCampusChoices reopened = new LocalCampusChoices(context);
    assertEquals(guest, reopened.load(null));
    assertEquals(account, reopened.load("uid-1"));
    assertEquals(new CampusChoice("", ""), reopened.load("uid-2"));
    CampusChoice secondAccount = new CampusChoice("Second college", "Second department");
    assertTrue(reopened.save("uid-2", secondAccount));
    LocalCampusChoices again = new LocalCampusChoices(context);
    assertEquals(secondAccount, again.load("uid-2"));
    assertEquals(account, again.load("uid-1"));
    assertEquals(guest, again.load(null));
    assertEquals(new Profile("", "00123", ""), again.loadGuestProfile());
    assertThrows(IllegalArgumentException.class, () -> reopened.load(""));
    assertThrows(IllegalArgumentException.class, () -> reopened.save(" uid", guest));
  }

  @Test
  public void atomicGuestSavePersistsMatchingChoiceAndProfile() {
    IsolatedContext context = new IsolatedContext(ApplicationProvider.getApplicationContext());
    LocalCampusChoices store = new LocalCampusChoices(context);
    Profile profile = new Profile("", "00123", "Department");
    CampusChoice choice = new CampusChoice("College", "Department");
    assertTrue(store.saveGuest(profile, choice));

    LocalCampusChoices reopened = new LocalCampusChoices(context);
    assertEquals(profile, reopened.loadGuestProfile());
    assertEquals(choice, reopened.load(null));
  }

  @Test
  public void invalidAtomicGuestSaveLeavesEntirePreferencesUnchanged() {
    IsolatedContext context = new IsolatedContext(ApplicationProvider.getApplicationContext());
    LocalCampusChoices store = new LocalCampusChoices(context);
    Profile original = new Profile("Original", "00123", "Department");
    CampusChoice choice = new CampusChoice("College", "Department");
    assertTrue(store.saveGuest(original, choice));
    assertTrue(store.save("uid-1", new CampusChoice("Other college", "Other department")));
    SharedPreferences preferences = context.getSharedPreferences("ignored", Context.MODE_PRIVATE);
    Map<String, ?> before = new HashMap<>(preferences.getAll());

    String tooLong = new String(new char[81]).replace('\0', 'a');
    assertThrows(
        IllegalArgumentException.class,
        () -> store.saveGuest(new Profile(tooLong, "00123", "Department"), choice));
    assertEquals(before, preferences.getAll());
    assertThrows(
        IllegalArgumentException.class,
        () -> store.saveGuest(original, new CampusChoice("College", "Different department")));
    assertEquals(before, preferences.getAll());
    assertThrows(IllegalArgumentException.class, () -> store.saveGuest(null, choice));
    assertThrows(IllegalArgumentException.class, () -> store.saveGuest(original, null));
    assertEquals(before, preferences.getAll());
  }

  @Test
  public void rejectsCorruptionWithoutOverwritingIt() {
    IsolatedContext context = new IsolatedContext(ApplicationProvider.getApplicationContext());
    SharedPreferences prefs = context.getSharedPreferences("ignored", Context.MODE_PRIVATE);
    assertTrue(prefs.edit().putInt("guest.choice.college", 4).commit());
    LocalCampusChoices store = new LocalCampusChoices(context);
    assertThrows(IllegalStateException.class, () -> store.load(null));
    assertEquals(4, prefs.getInt("guest.choice.college", 0));
    String tooLong = new String(new char[81]).replace('\0', 'a');
    assertTrue(prefs.edit().putString("guest.profile.name", tooLong).commit());
    assertThrows(IllegalStateException.class, store::loadGuestProfile);
    assertThrows(
        IllegalArgumentException.class, () -> store.saveGuestProfile(new Profile(tooLong, "", "")));
  }
}
