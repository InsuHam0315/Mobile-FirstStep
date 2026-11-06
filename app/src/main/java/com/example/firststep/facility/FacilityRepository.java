package com.example.firststep.facility;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads bundled data and keeps favorites on this device by stable facility ID. */
public final class FacilityRepository {
  private static final Object FAVORITES_LOCK = new Object();
  private final Context context;
  private final SharedPreferences preferences;

  public FacilityRepository(Context context) {
    this.context = context.getApplicationContext();
    preferences = this.context.getSharedPreferences("facility_favorites", Context.MODE_PRIVATE);
  }

  // Test-only seam: injected preferences are used without accessing Android assets.
  FacilityRepository(SharedPreferences preferences) {
    this.context = null;
    this.preferences = java.util.Objects.requireNonNull(preferences);
  }

  public List<Facility> load() throws Exception {
    try (InputStream stream = context.getAssets().open("facilities.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
      JSONArray data = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
      List<Facility> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < data.length(); i++) {
        JSONObject j = data.getJSONObject(i);
        String id = j.getString("id");
        if (id.trim().isEmpty() || !seen.add(id))
          throw new IllegalStateException("Duplicate facility ID");
        result.add(
            new Facility(
                id,
                j.getString("name"),
                j.getString("category"),
                j.getString("summary"),
                strings(j.getJSONArray("useCases")),
                strings(j.getJSONArray("aliases")),
                j.getString("officialUrl"),
                j.getString("sourceLabel"),
                j.getString("sourceCheckedOn"),
                j.getString("locationStatus"),
                j.getString("hoursStatus")));
      }
      FacilityCatalogValidator.validate(result);
      return Collections.unmodifiableList(result);
    }
  }

  private static List<String> strings(JSONArray a) throws Exception {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
    return out;
  }

  public Set<String> favorites() {
    synchronized (FAVORITES_LOCK) {
      return new HashSet<>(preferences.getStringSet("ids", Collections.emptySet()));
    }
  }

  public void setFavorite(String id, boolean selected) {
    synchronized (FAVORITES_LOCK) {
      Set<String> ids = favorites();
      if (selected) ids.add(id);
      else ids.remove(id);
      preferences.edit().putStringSet("ids", ids).apply();
    }
  }
}
