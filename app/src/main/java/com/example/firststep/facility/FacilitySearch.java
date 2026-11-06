package com.example.firststep.facility;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All query terms must match somewhere across the facility's searchable fields. */
public final class FacilitySearch {
  private FacilitySearch() {}

  private static String normalize(String s) {
    return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  public static boolean matches(Facility f, String query) {
    String q = normalize(query);
    if (q.isEmpty()) return true;
    StringBuilder text =
        new StringBuilder()
            .append(f.name)
            .append(' ')
            .append(f.category)
            .append(' ')
            .append(f.summary);
    for (String alias : f.aliases) text.append(' ').append(alias);
    for (String use : f.useCases) text.append(' ').append(use);
    String haystack = normalize(text.toString());
    for (String term : q.split("\\s+")) if (!haystack.contains(term)) return false;
    return true;
  }

  public static List<Facility> filter(
      List<Facility> facilities,
      String query,
      String category,
      boolean favoritesOnly,
      java.util.Set<String> favoriteIds) {
    List<Facility> result = new ArrayList<>();
    for (Facility f : facilities)
      if ((category == null || category.isEmpty() || category.equals(f.category))
          && (!favoritesOnly || favoriteIds.contains(f.id))
          && matches(f, query)) result.add(f);
    return result;
  }
}
