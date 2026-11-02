package com.example.firststep.data;

/** A single completion record, unique per mode and mission. */
public final class MissionCompletion {
    public final AppMode mode;
    public final String missionId;
    public final long savedAt;
    public final String photoUri;
    public final String discoveryMethod;
    public final String completionMethod;

    public MissionCompletion(AppMode mode, String missionId, long savedAt,
                             String photoUri, String discoveryMethod, String completionMethod) {
        this.mode = mode;
        this.missionId = missionId;
        this.savedAt = savedAt;
        this.photoUri = photoUri;
        this.discoveryMethod = discoveryMethod;
        this.completionMethod = completionMethod;
    }

    /** Compatibility constructor for callers that have no action audit metadata. */
    public MissionCompletion(AppMode mode, String missionId, long savedAt,
                             String photoUri, String discoveryMethod) {
        this(mode, missionId, savedAt, photoUri, discoveryMethod, "LEGACY");
    }
}
