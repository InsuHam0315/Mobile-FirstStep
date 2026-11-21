package com.example.firststep.game.guide;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.game.content.NarrationAsset;
import com.example.firststep.game.content.NarrationContent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NarrationParserTest {
  private String asset() throws Exception { return asset("software_narration_v1"); }
  private String asset(String name) throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    try (InputStream in = context.getAssets().open("campus_content/" + name + ".json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[4096]; int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }
  private void reject(String id, String json) {
    try { NarrationAsset.decodeJson(id, json); fail("invalid narration accepted"); }
    catch (IOException expected) { }
  }
  @Test public void bundledExactApprovedCopyAndStrictParser() throws Exception {
    String json = asset();
    NarrationContent c = NarrationAsset.decodeJson(NarrationContent.DATASET_ID, json);
    String[] expected = {
      "안녕하세요. 디지털정보관을 소개할 안내 요정이에요.",
      "먼저 소프트웨어학과를 함께 알아볼까요?",
      "소프트웨어학과는 컴퓨터소프트웨어특성화대학의 학과 목록에서 확인할 수 있어요.",
      "학과 공식 위치 안내에는 디지털정보관 1층이 적혀 있어요.",
      "학과는 소통·융합·공유를 바탕으로 실무 소프트웨어 역량을 키우는 교육을 소개해요.",
      "공지사항, 교과과정, 학사안내는 학과 홈페이지에서 확인해 보세요.",
      "방문하기 전에는 최신 안내를 다시 확인해 주세요."
    };
    assertEquals(expected.length, c.sentences().size());
    for (int i = 0; i < expected.length; i++) assertEquals(expected[i], c.sentences().get(i).text);
    reject("h04_fixture_v1", json);
    reject("../databases/firststep.db", json);
    reject(NarrationContent.DATASET_ID, json + " garbage");
    reject(NarrationContent.DATASET_ID, "[" + json + "]");
    String duplicate = json.replaceFirst("\"version\"\\s*:\\s*1", "\"version\":1,\"version\":1");
    assertNotEquals(json, duplicate);
    reject(NarrationContent.DATASET_ID, duplicate);
    reject(NarrationContent.DATASET_ID, json.replaceFirst("\"version\"\\s*:\\s*1", "\"version\":1.0"));
    reject(NarrationContent.DATASET_ID, "{'version':1}");
    char[] big = new char[65537]; Arrays.fill(big, ' ');
    reject(NarrationContent.DATASET_ID, new String(big));
  }
  @Test public void koreanAssetLoadsAndRejectsMismatchedIdentityAndClaims() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String json = asset("korean_narration_v1");
    NarrationContent content = NarrationAsset.load(context, NarrationContent.KOREAN_DATASET_ID);
    String[] expected = {
      "안녕하세요. 국어국문학과를 함께 둘러볼 안내 요정 강린이에요.",
      "국어국문학과를 함께 알아볼까요?",
      "국어국문학과는 인문콘텐츠융합대학의 학과 목록에서 확인할 수 있어요.",
      "학과 사무실 위치 안내에는 인문관 3층 1326호가 나와 있어요.",
      "학과 소개에서는 국어와 국문학을 비롯해 영상문학과 웹라이팅을 소개해요.",
      "학과 홈페이지에서 학사정보와 공지사항을 확인할 수 있어요.",
      "방문하기 전에는 최신 안내를 다시 확인해 주세요."
    };
    assertEquals(expected.length, content.sentences().size());
    for (int i = 0; i < expected.length; i++) assertEquals(expected[i], content.sentences().get(i).text);
    assertEquals("ko_office_claim", content.sentences().get(3).claimId);
    reject(NarrationContent.DATASET_ID, json);
    reject(NarrationContent.KOREAN_DATASET_ID, asset());
    reject(NarrationContent.KOREAN_DATASET_ID, json.replace("ko_office_claim", "office_guidance"));
    reject(NarrationContent.KOREAN_DATASET_ID, json.replace("ko_office_claim", "ko_info_claim"));
    try { NarrationAsset.load(context, "../software_narration_v1"); fail(); }
    catch (IOException expectedFailure) { }
  }

}
