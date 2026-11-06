package com.example.firststep.game.data.local;

import com.example.firststep.game.domain.*;
import java.util.*;
import org.json.*;

/** Versioned JSON contract for bundled synthetic content and durable snapshots. */
public final class GameJson {
  private GameJson() {}

  private static long integer(JSONObject object, String key) throws JSONException {
    Object value = object.get(key);
    if (!(value instanceof Integer) && !(value instanceof Long)) {
      throw new IllegalArgumentException("expected integer: " + key);
    }
    return ((Number) value).longValue();
  }

  public static GameContent catalog(String json) {
    try {
      JSONObject root = new JSONObject(json);
      if (integer(root, "schema") != 1) throw new IllegalArgumentException("catalog schema");
      List<GameContent.Building> bs = new ArrayList<>();
      JSONArray a = root.getJSONArray("buildings");
      for (int i = 0; i < a.length(); i++) {
        JSONObject b = a.getJSONObject(i);
        bs.add(
            new GameContent.Building(
                b.getString("id"), b.getString("label"), questions(b.getJSONArray("questions"))));
      }
      return new GameContent(root.getString("version"), bs);
    } catch (JSONException e) {
      throw new IllegalArgumentException("invalid catalog", e);
    }
  }

  private static List<GameContent.Question> questions(JSONArray a) throws JSONException {
    List<GameContent.Question> qs = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) {
      JSONObject q = a.getJSONObject(i);
      JSONArray os = q.getJSONArray("options");
      List<GameContent.Option> options = new ArrayList<>();
      for (int j = 0; j < os.length(); j++) {
        JSONObject o = os.getJSONObject(j);
        options.add(new GameContent.Option(o.getString("id"), o.getString("text")));
      }
      qs.add(
          new GameContent.Question(
              q.getString("id"), q.getString("text"), q.getString("correctOptionId"), options));
    }
    return qs;
  }

  private static JSONArray questions(List<GameContent.Question> qs) throws JSONException {
    JSONArray a = new JSONArray();
    for (GameContent.Question q : qs) {
      JSONArray os = new JSONArray();
      for (GameContent.Option o : q.options)
        os.put(new JSONObject().put("id", o.id).put("text", o.text));
      a.put(
          new JSONObject()
              .put("id", q.id)
              .put("text", q.text)
              .put("correctOptionId", q.correctOptionId)
              .put("options", os));
    }
    return a;
  }

  public static String encode(Progress p) {
    try {
      JSONObject o =
          new JSONObject()
              .put("schema", 1)
              .put("mode", p.mode.name())
              .put("buildingId", p.buildingId)
              .put("introDoneAt", p.introDoneAt == null ? JSONObject.NULL : p.introDoneAt)
              .put("instantDemo", p.instantDemo)
              .put("stamp", p.stamp);
      if (p.active != null)
        o.put(
            "active",
            new JSONObject()
                .put("id", p.active.id)
                .put("version", p.active.contentVersion)
                .put("questions", questions(p.active.questions)));
      else o.put("active", JSONObject.NULL);
      JSONArray history = new JSONArray();
      for (Progress.Result r : p.history.values()) {
        JSONObject answers = new JSONObject();
        for (Map.Entry<String, String> e : r.answers.entrySet())
          answers.put(e.getKey(), e.getValue());
        history.put(
            new JSONObject()
                .put("id", r.attemptId)
                .put("version", r.contentVersion)
                .put("answers", answers)
                .put("correct", r.correct));
      }
      return o.put("history", history).toString();
    } catch (JSONException e) {
      throw new IllegalArgumentException("cannot encode", e);
    }
  }

  public static Progress decode(String json) {
    try {
      JSONObject o = new JSONObject(json);
      if (integer(o, "schema") != 1) throw new IllegalArgumentException("progress schema");
      Progress.Mode mode = Progress.Mode.valueOf(o.getString("mode"));
      String id = o.getString("buildingId");
      if (!o.has("introDoneAt") || !o.has("active"))
        throw new IllegalArgumentException("missing required progress field");
      Long intro = o.isNull("introDoneAt") ? null : integer(o, "introDoneAt");
      Progress.Attempt active = null;
      if (!o.isNull("active")) {
        JSONObject a = o.getJSONObject("active");
        active =
            new Progress.Attempt(
                a.getString("id"), a.getString("version"), questions(a.getJSONArray("questions")));
      }
      Map<String, Progress.Result> history = new LinkedHashMap<>();
      JSONArray h = o.getJSONArray("history");
      for (int i = 0; i < h.length(); i++) {
        JSONObject r = h.getJSONObject(i), a = r.getJSONObject("answers");
        Map<String, String> answers = new LinkedHashMap<>();
        Iterator<String> keys = a.keys();
        while (keys.hasNext()) {
          String k = keys.next();
          answers.put(k, a.getString(k));
        }
        Progress.Result result =
            new Progress.Result(
                r.getString("id"),
                r.getString("version"),
                answers,
                Math.toIntExact(integer(r, "correct")));
        if (history.put(result.attemptId, result) != null)
          throw new IllegalArgumentException("duplicate result");
      }
      return new Progress(
          mode, id, intro, o.getBoolean("instantDemo"), o.getBoolean("stamp"), active, history);
    } catch (JSONException e) {
      throw new IllegalArgumentException("invalid progress", e);
    }
  }
}
