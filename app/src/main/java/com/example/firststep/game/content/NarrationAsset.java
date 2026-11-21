package com.example.firststep.game.content;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Opens only the registered preview asset; callers cannot supply paths. */
public final class NarrationAsset {
  private NarrationAsset() {}
  public static NarrationContent load(Context context) throws IOException {
    return load(context, NarrationContent.DATASET_ID);
  }
  public static NarrationContent load(Context context, String narrationId) throws IOException {
    if (!NarrationContent.isRegistered(narrationId)) throw new IOException("Unregistered narration");
    String path = NarrationContent.KOREAN_DATASET_ID.equals(narrationId)
        ? "campus_content/korean_narration_v1.json" : "campus_content/software_narration_v1.json";
    try (InputStream in = context.getAssets().open(path);
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[4096]; int n;
      while ((n = in.read(b)) != -1) { if (out.size() + n > 65536) throw new IOException("Narration too large"); out.write(b, 0, n); }
      String json = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();
      return decodeJson(narrationId, json);
    }
  }
  public static NarrationContent decodeJson(String id, String json) throws IOException {
    if (!NarrationContent.isRegistered(id) || json == null || json.length() > 65536) throw new IOException("Unregistered narration");
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      Object root = AssetGuideRepository.value(reader, 0);
      if (!(root instanceof Map) || reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Invalid narration root");
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) root;
      return NarrationContent.decode(id, map);
    } catch (IllegalArgumentException | IllegalStateException invalid) { throw new IOException("Invalid narration", invalid); }
  }
}
