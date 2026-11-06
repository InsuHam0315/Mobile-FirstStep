package com.example.firststep.facility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline, curated facility information. Coordinates and hours are intentionally absent. */
public final class Facility {
  public final String id,
      name,
      category,
      summary,
      officialUrl,
      sourceLabel,
      sourceCheckedOn,
      locationStatus,
      hoursStatus;
  public final List<String> useCases, aliases;

  public Facility(
      String id,
      String name,
      String category,
      String summary,
      List<String> useCases,
      List<String> aliases,
      String officialUrl,
      String sourceLabel,
      String sourceCheckedOn,
      String locationStatus,
      String hoursStatus) {
    this.id = id;
    this.name = name;
    this.category = category;
    this.summary = summary;
    this.useCases = Collections.unmodifiableList(new ArrayList<>(useCases));
    this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
    this.officialUrl = officialUrl;
    this.sourceLabel = sourceLabel;
    this.sourceCheckedOn = sourceCheckedOn;
    this.locationStatus = locationStatus;
    this.hoursStatus = hoursStatus;
  }
}
