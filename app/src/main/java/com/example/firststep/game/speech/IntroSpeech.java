package com.example.firststep.game.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.example.firststep.R;
import java.util.Locale;
import java.util.function.Consumer;

/** Speech is optional; callbacks only report status and never advance the introduction. */
public final class IntroSpeech implements AutoCloseable {
  private TextToSpeech tts;
  private boolean ready, closed;
  private final SpeechRequestGate gate = new SpeechRequestGate();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Consumer<String> status;
  private final Context context;
  private final String testMode;

  public IntroSpeech(Context context, String testMode, Consumer<String> status) {
    this.status = status;
    this.context = context;
    this.testMode = testMode;
    if ("absent".equals(testMode) || "init_error".equals(testMode)) {
      deliver(gate.current(), R.string.game_intro_speech_1);
      return;
    }
    if ("play_error".equals(testMode)) return;
    final int initToken = gate.current();
    tts =
        new TextToSpeech(
            context,
            code -> {
              if (closed) return;
              if (code != TextToSpeech.SUCCESS || tts == null) {
                if (gate.accepts(initToken)) deliver(initToken, R.string.game_intro_speech_2);
                return;
              }
              ready = tts.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE;
              if (!ready && gate.accepts(initToken))
                deliver(initToken, R.string.game_intro_speech_3);
            });
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          public void onStart(String id) {}

          public void onDone(String id) {}

          public void onError(String id) {
            if (gate.accepts(id))
              deliver(Integer.parseInt(id.substring(4)), R.string.game_intro_speech_4);
          }
        });
  }

  public void speak(String sentence) {
    stop();
    if ("play_error".equals(testMode)) {
      deliver(gate.current(), R.string.game_intro_speech_4);
      return;
    }
    if (ready && tts != null) {
      if (tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "h04-" + gate.current())
          == TextToSpeech.ERROR) deliver(gate.current(), R.string.game_intro_speech_4);
    } else deliver(gate.current(), R.string.game_intro_speech_1);
  }

  public int generationForTest() {
    return gate.current();
  }

  public void errorForTest(int token) {
    if (gate.accepts(token)) deliver(token, R.string.game_intro_speech_4);
  }

  private void deliver(int token, int message) {
    main.post(
        () -> {
          if (gate.accepts(token)) status.accept(context.getString(message));
        });
  }

  public void stop() {
    gate.invalidate();
    if (tts != null) tts.stop();
  }

  public void close() {
    closed = true;
    gate.close();
    if (tts != null) tts.stop();
    if (tts != null) {
      tts.shutdown();
      tts = null;
    }
    ready = false;
  }
}
