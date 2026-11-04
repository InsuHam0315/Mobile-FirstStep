package com.example.firststep.auth;

import android.content.Context;
import com.example.firststep.BuildConfig;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Synchronous API operations; callers use a worker and capture the account token at construction. */
public final class CloudApi {
  private final String token;
  private final String baseUrl;
  public CloudApi(Context context, String capturedToken) { this(BuildConfig.API_BASE_URL, capturedToken); }
  public CloudApi(String baseUrl, String capturedToken) {
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    token = capturedToken == null ? "" : capturedToken;
  }

  public JSONObject request(String method, String path, JSONObject body) throws ApiException {
    HttpURLConnection connection = null;
    try {
      if (!path.startsWith("/v1/") || path.contains("..")) throw new ApiException(0, "configuration", "서버 요청 설정을 확인해 주세요.");
      URL url = new URL(baseUrl + path);
      boolean local = "10.0.2.2".equals(url.getHost()) || "10.0.3.2".equals(url.getHost())
          || "127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost());
      if (!"https".equals(url.getProtocol()) && !(BuildConfig.DEBUG && local && "http".equals(url.getProtocol())))
        throw new ApiException(0, "configuration", "서버 주소 설정이 필요합니다. 배포 서버는 HTTPS를 사용해 주세요.");
      connection = (HttpURLConnection) url.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(6000); connection.setReadTimeout(10000);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "FirstStep/1.0 Android");
      if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
      if (body != null) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>12*1024*1024)throw new ApiException(413,"request_too_large","동기화할 데이터가 너무 커요. 사진 크기를 확인해 주세요.");
        connection.setDoOutput(true);connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
      }
      int status = connection.getResponseCode();
      InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
      String text = "";
      if (stream != null) try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[4096]; int size, total = 0;
        while ((size = input.read(buffer)) != -1) { total += size; if (total > 12 * 1024 * 1024) throw new IOException("Response too large"); output.write(buffer, 0, size); }
        text = output.toString(StandardCharsets.UTF_8.name());
      }
      JSONObject result;
      try { result = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text); }
      catch (Exception invalid) { throw new ApiException(status, "invalid_response", "서버 응답을 확인할 수 없어요. 잠시 후 다시 시도해 주세요."); }
      if (status < 200 || status >= 300) {
        JSONObject detail = result.optJSONObject("detail");
        String code = result.optString("error_code", result.optString("code", "http_" + status));
        if (detail != null) code = detail.optString("code", code);
        String message = status == 401 ? "계정 정보가 맞지 않거나 로그인이 만료됐어요. 다시 로그인해 주세요."
            : status == 409 ? "이미 사용 중인 계정 이름입니다. 다른 이름으로 등록하거나 로그인해 주세요."
            : status == 422 || status == 400 ? "입력 내용을 확인해 주세요. 계정 이름과 비밀번호 조건을 확인해 주세요."
            : status == 403 ? "현재 사용할 수 없는 로그인 방식입니다. 서버 설정을 확인해 주세요."
            : status == 429 ? "요청이 많아요. 잠시 후 다시 시도해 주세요."
            : "서버에서 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
        throw new ApiException(status, code, message);
      }
      return result;
    } catch (ApiException error) { throw error; }
    catch (Exception error) { throw new ApiException(0, "network_unavailable", "서버에 연결하지 못했어요. 개발 서버 실행과 네트워크 설정을 확인해 주세요."); }
    finally { if (connection != null) connection.disconnect(); }
  }

  public static final class ApiException extends Exception {
    public final int status;
    public final String errorCode;
    public ApiException(int status, String errorCode, String message) { super(message); this.status = status; this.errorCode = errorCode; }
  }
}
