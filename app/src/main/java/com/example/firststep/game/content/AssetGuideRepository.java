package com.example.firststep.game.content;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Reads exactly one registered, bounded, local asset. Never reads an intent path or database. */
public final class AssetGuideRepository implements GuidePorts.BundleLoader {
  private final Context context;

  public AssetGuideRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public GuideBundle load() throws IOException {
    try (InputStream in = context.getAssets().open("campus_content/software_guide_v1.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int size;
      while ((size = in.read(buffer)) != -1) {
        if (out.size() + size > 65536) throw new IOException("Guide too large");
        out.write(buffer, 0, size);
      }
      String json =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(out.toByteArray()))
              .toString();
      return decodeJson(GuideDecoder.DATASET_ID, json);
    }
  }

  /**
   * Shared parser entry for actual Android parser regression tests; registration is still required.
   */
  public static GuideBundle decodeJson(String registeredId, String json) throws IOException {
    if (!GuideDecoder.DATASET_ID.equals(registeredId) || json == null || json.length() > 65536)
      throw new IOException("Unregistered/oversized guide");
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      Object root = value(reader, 0);
      if (!(root instanceof Map) || reader.peek() != JsonToken.END_DOCUMENT)
        throw new IOException("Invalid root/trailing content");
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) root;
      return GuideDecoder.decode(registeredId, map);
    } catch (IllegalArgumentException | IllegalStateException invalid) {
      throw new IOException("Invalid guide", invalid);
    }
  }

  static Object value(JsonReader reader, int depth) throws IOException {
    if (depth > 16) throw new IOException("Guide nesting limit");
    switch (reader.peek()) {
      case BEGIN_OBJECT:
        Map<String, Object> object = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (object.containsKey(key)) throw new IOException("Duplicate object key");
          object.put(key, value(reader, depth + 1));
        }
        reader.endObject();
        return object;
      case BEGIN_ARRAY:
        List<Object> list = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) list.add(value(reader, depth + 1));
        reader.endArray();
        return list;
      case STRING:
        return reader.nextString();
      case BOOLEAN:
        return reader.nextBoolean();
      case NULL:
        reader.nextNull();
        return null;
      case NUMBER:
        String number = reader.nextString();
        try {
          if (number.matches("-?(0|[1-9][0-9]*)")) return Integer.valueOf(number);
          return Double.valueOf(number); // Kept non-integer; the typed decoder rejects it.
        } catch (NumberFormatException invalid) {
          throw new IOException("Invalid numeric field", invalid);
        }
      default:
        throw new IOException("Invalid JSON value");
    }
  }
}
