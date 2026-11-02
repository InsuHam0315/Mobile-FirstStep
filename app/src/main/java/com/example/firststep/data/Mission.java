package com.example.firststep.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable catalog entry. Coordinates remain absent until field-verified. */
public final class Mission {
    public final String id;
    public final String title;
    public final String place;
    public final String type;
    public final String story;
    public final String question;
    public final String answer;
    public final List<String> acceptedAnswers;
    public final List<String> options;
    public final String explanation;
    public final int order;
    public final boolean coordinateVerified;
    public final Double latitude;
    public final Double longitude;

    public Mission(String id, String title, String place, String type, String story,
                   String question, String answer, List<String> acceptedAnswers,
                   List<String> options, String explanation, int order,
                   boolean coordinateVerified, Double latitude, Double longitude) {
        this.id = id;
        this.title = title;
        this.place = place;
        this.type = type;
        this.story = story;
        this.question = question;
        this.answer = answer;
        this.acceptedAnswers = immutable(acceptedAnswers);
        this.options = immutable(options);
        this.explanation = explanation;
        this.order = order;
        // Reject accidental operational use of coordinates before verification.
        this.coordinateVerified = coordinateVerified && latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
        this.latitude = this.coordinateVerified ? latitude : null;
        this.longitude = this.coordinateVerified ? longitude : null;
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
