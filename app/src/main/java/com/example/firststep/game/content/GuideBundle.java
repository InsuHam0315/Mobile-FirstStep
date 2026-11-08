package com.example.firststep.game.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, validated built-in learning content. */
public final class GuideBundle {
  private final String datasetId;
  private final String targetId;
  private final int version;
  private final String contentVerification;
  private final String arrivalVerification;
  private final List<Source> sources;
  private final List<Claim> claims;
  private final List<Question> questions;

  GuideBundle(
      String datasetId,
      String targetId,
      int version,
      String contentVerification,
      String arrivalVerification,
      List<Source> sources,
      List<Claim> claims,
      List<Question> questions) {
    this.datasetId = datasetId;
    this.targetId = targetId;
    this.version = version;
    this.contentVerification = contentVerification;
    this.arrivalVerification = arrivalVerification;
    this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    this.claims = Collections.unmodifiableList(new ArrayList<>(claims));
    this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
  }

  public String getDatasetId() {
    return datasetId;
  }

  public String getTargetId() {
    return targetId;
  }

  public int getVersion() {
    return version;
  }

  public String getContentVerification() {
    return contentVerification;
  }

  public String getArrivalVerification() {
    return arrivalVerification;
  }

  public List<Source> getSources() {
    return sources;
  }

  public List<Claim> getClaims() {
    return claims;
  }

  public List<Question> getQuestions() {
    return questions;
  }

  public static final class Source {
    private final String id;
    private final String label;
    private final String url;
    private final String checkedDate;

    Source(String id, String label, String url, String checkedDate) {
      this.id = id;
      this.label = label;
      this.url = url;
      this.checkedDate = checkedDate;
    }

    public String getId() {
      return id;
    }

    public String getLabel() {
      return label;
    }

    public String getUrl() {
      return url;
    }

    public String getCheckedDate() {
      return checkedDate;
    }
  }

  public static final class Claim {
    private final String id;
    private final String sourceId;
    private final String text;

    Claim(String id, String sourceId, String text) {
      this.id = id;
      this.sourceId = sourceId;
      this.text = text;
    }

    public String getId() {
      return id;
    }

    public String getSourceId() {
      return sourceId;
    }

    public String getText() {
      return text;
    }
  }

  public static final class Option {
    private final String id;
    private final String text;

    Option(String id, String text) {
      this.id = id;
      this.text = text;
    }

    public String getId() {
      return id;
    }

    public String getText() {
      return text;
    }
  }

  public static final class Question {
    private final String id;
    private final String claimId;
    private final String text;
    private final List<Option> options;
    private final String answerId;
    private final String explanation;

    Question(
        String id,
        String claimId,
        String text,
        List<Option> options,
        String answerId,
        String explanation) {
      this.id = id;
      this.claimId = claimId;
      this.text = text;
      this.options = Collections.unmodifiableList(new ArrayList<>(options));
      this.answerId = answerId;
      this.explanation = explanation;
    }

    public String getId() {
      return id;
    }

    public String getClaimId() {
      return claimId;
    }

    public String getText() {
      return text;
    }

    public List<Option> getOptions() {
      return options;
    }

    public String getAnswerId() {
      return answerId;
    }

    public String getExplanation() {
      return explanation;
    }
  }
}
