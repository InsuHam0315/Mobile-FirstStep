package com.example.firststep.facility;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Prevents a bundled typo from presenting an untrusted link as a university source. */
public final class FacilityCatalogValidator {
  private FacilityCatalogValidator() {}

  public static boolean isOfficialUrl(String value) {
    if (value == null) return false;
    try {
      URI uri = new URI(value);
      String host = uri.getHost();
      if (host == null
          || !"https".equalsIgnoreCase(uri.getScheme())
          || uri.getRawUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
      host = host.toLowerCase(Locale.ROOT);
      return host.equals("kunsan.ac.kr") || host.endsWith(".kunsan.ac.kr");
    } catch (Exception invalid) {
      return false;
    }
  }

  public static void validate(List<Facility> facilities) {
    if (facilities == null || facilities.isEmpty())
      throw new IllegalArgumentException("Empty facility catalog");
    Set<String> ids = new HashSet<>();
    Set<String> categories = new HashSet<>(Arrays.asList("학습", "학생지원", "진로·취업", "생활"));
    for (Facility facility : facilities) {
      if (facility == null
          || !present(facility.id)
          || !facility.id.matches("[a-z0-9][a-z0-9-]{1,63}")
          || !ids.add(facility.id)) throw new IllegalArgumentException("Invalid facility ID");
      if (!present(facility.name)
          || !present(facility.summary)
          || !categories.contains(facility.category)
          || !present(facility.sourceLabel)
          || !present(facility.locationStatus)
          || !present(facility.hoursStatus)
          || !isOfficialUrl(facility.officialUrl)
          || !isDate(facility.sourceCheckedOn)
          || facility.useCases.isEmpty()
          || facility.aliases.isEmpty())
        throw new IllegalArgumentException("Invalid facility information");
      for (String useCase : facility.useCases)
        if (!present(useCase)) throw new IllegalArgumentException("Empty use case");
      for (String alias : facility.aliases)
        if (!present(alias)) throw new IllegalArgumentException("Empty alias");
    }
  }

  private static boolean present(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static boolean isDate(String value) {
    if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    format.setLenient(false);
    ParsePosition position = new ParsePosition(0);
    return format.parse(value, position) != null && position.getIndex() == value.length();
  }
}
