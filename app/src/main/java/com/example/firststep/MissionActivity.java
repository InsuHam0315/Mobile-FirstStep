package com.example.firststep;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.Locale;

public class MissionActivity extends AppCompatActivity implements SensorEventListener {
  private static final int AR = 410, CAMERA = 411, SPEECH = 412, LOCATION = 413;
  private com.example.firststep.location.LocationController locationController;
  private final com.example.firststep.location.ArrivalPolicy arrivalPolicy =
      new com.example.firststep.location.ArrivalPolicy();
  private com.example.firststep.data.Mission mission;
  private com.example.firststep.data.MissionRepository missionRepository;
  private String missionOwner;
  private int index;
  private boolean demo,
      checkingArrival,
      voiceActive,
      fieldArrived,
      locationGateVisible,
      wasBackgrounded;
  private android.location.Location currentArrivalLocation;
  private com.example.firststep.sensors.CompassSensorController compassController;
  private com.example.firststep.sensors.CompassChallenge compassChallenge;
  private float compassTarget, compassDeclination;
  private ProgressBar compassProgress;
  private CompassDialView compassDial;
  private String method = "CARD", photoPath, completionMethod;
  private boolean saving = false,
      photoPersisted = false,
      capturePending = false,
      reconciling = false;
  private long captureGeneration = 0;
  private Button captureButton, holdButton;
  private LinearLayout body;
  private TextView status;
  private SensorManager sensors;
  private Sensor light, accel;
  private com.example.firststep.sensors.LightChallenge lightChallenge;
  private int sensorStage = 0;
  private final com.example.firststep.sensors.MotionChallenge motionChallenge =
      new com.example.firststep.sensors.MotionChallenge();
  private TextToSpeech tts;
  private boolean ttsReady = false;
  private TextView voiceStatus;
  private ImageCapture capture;
  private ProcessCameraProvider provider;
  private final Handler handler = new Handler();
  private MissionCheckpointStore checkpoints;
  private MissionCheckpointStore.State checkpoint;
  private String resumeStage = "DISCOVERY";
  private boolean leavingExplicitly;

  private void checkpoint(String stage) {
    resumeStage = stage;
    checkpoint.stage = stage;
    checkpoint.discovery = method;
    checkpoint.completion = completionMethod;
    checkpoint.photo = photoPath;
    checkpoints.save(checkpoint);
  }

  private void leaveMission() {
    if (saving || reconciling) {
      Toast.makeText(this, "저장이 끝날 때까지 잠시 기다려 주세요.", Toast.LENGTH_SHORT).show();
      return;
    }
    leavingExplicitly = true;
    checkpoints.clear();
    discardDraftPhoto();
    finish();
  }

  private void restoreStage() {
    switch (resumeStage) {
      case "STORY":
        story();
        break;
      case "GAME":
        game();
        break;
      case "PHOTO_PREVIEW":
        if (photoPath != null && new File(photoPath).isFile()) photoPreview();
        else photo();
        break;
      case "QUIZ":
        if (completionMethod != null
            && (index != 0 || (photoPath != null && new File(photoPath).isFile()))) quiz();
        else game();
        break;
      case "SAVING":
        complete();
        break;
      case "SUCCESS":
        showCompletion();
        break;
      default:
        discovery();
    }
  }

  private void reconcileAndResume() {
    if (!"SAVING".equals(resumeStage) && !"SUCCESS".equals(resumeStage)) {
      restoreStage();
      return;
    }
    reconciling = true;
    // The loaded path may already belong to Room. Protect it until ownership is known.
    photoPersisted = true;
    screen("SAVE", "기록을 확인하고 있어요", "잠시만 기다려 주세요.");
    com.example.firststep.data.MissionRepository.getInstance(this)
        .getCompletions(
            demo
                ? com.example.firststep.data.AppMode.DEMO
                : com.example.firststep.data.AppMode.FIELD,
            records -> {
              if (isDestroyed() || isFinishing()) return;
              reconciling = false;
              for (com.example.firststep.data.MissionCompletion item : records) {
                if (item.missionId.equals(mission.id)) {
                  if (photoPath != null && !photoPath.equals(item.photoUri))
                    MissionCheckpointStore.cleanupDraft(this, photoPath);
                  photoPath = item.photoUri;
                  photoPersisted = true;
                  resumeStage = "SUCCESS";
                  showCompletion();
                  return;
                }
              }
              photoPersisted = false;
              if ("SUCCESS".equals(resumeStage)) {
                photoPersisted = false;
                discardDraftPhoto();
                resumeStage = "DISCOVERY";
              }
              restoreStage();
            });
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    missionRepository = com.example.firststep.data.MissionRepository.getInstance(this);
    missionOwner = missionRepository.getOwnerId();
    index = Math.max(0, Math.min(4, getIntent().getIntExtra("missionIndex", 0)));
    demo = getIntent().getBooleanExtra("demo", false);
    mission =
        missionRepository.getMissions().get(index);
    checkpoints = new MissionCheckpointStore(this, demo, mission.id);
    checkpoint = checkpoints.load();
    resumeStage = checkpoint.stage;
    method = checkpoint.discovery == null ? "CARD" : checkpoint.discovery;
    completionMethod = checkpoint.completion;
    photoPath = checkpoint.photo;
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                leaveMission();
              }
            });
    sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
    light = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
    accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (!demo) {
      if (!mission.coordinateVerified || mission.latitude == null || mission.longitude == null) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("현장 좌표 확인 전")
            .setMessage("이 장소의 좌표는 아직 현장 검증되지 않아 방문을 확인할 수 없습니다. 테스트 모드를 이용해 주세요.")
            .setPositiveButton("확인", (d, w) -> finish())
            .setCancelable(false)
            .show();
        return;
      }
      locationGate();
    } else reconcileAndResume();
  }

  private void locationGate() {
    sensorStage = 0;
    sensors.unregisterListener(this);
    if (compassController != null) compassController.stop();
    checkingArrival = false;
    if (locationController != null) locationController.stop();
    screen("FIELD  ·  도착 확인", "장소에 도착했나요?", "안전한 곳에 멈춰 정확한 현재 위치를 확인해 주세요.");
    locationGateVisible = true;
    TextView locationStatus = Ui.text(this, "현재 위치 확인을 시작해 주세요.", 16, Ui.INK, true);
    LinearLayout card = Ui.card(this);
    card.addView(locationStatus);
    body.addView(card);
    Ui.gap(body, 20);
    action(
        "현재 위치 확인",
        () -> {
          if (!com.example.firststep.location.LocationController.hasPermission(this)) {
            requestPermissions(
                new String[] {
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION);
            return;
          }
          arrivalPolicy.reset();
          checkingArrival = true;
          if (locationController != null) locationController.stop();
          locationController =
              new com.example.firststep.location.LocationController(
                  this,
                  (location, message) -> {
                    if (!checkingArrival || isDestroyed() || isFinishing()) return;
                    if (location == null) {
                      arrivalPolicy.reset();
                      locationStatus.setText(message);
                      return;
                    }
                    float[] distance = new float[1];
                    android.location.Location.distanceBetween(
                        location.getLatitude(),
                        location.getLongitude(),
                        mission.latitude,
                        mission.longitude,
                        distance);
                    long now = android.os.SystemClock.elapsedRealtime();
                    long age = now - location.getElapsedRealtimeNanos() / 1000000L;
                    boolean arrived = false;
                    if (location.hasAccuracy())
                      arrived =
                          arrivalPolicy.evaluate(distance[0], location.getAccuracy(), age, now);
                    else arrivalPolicy.reset();
                    locationStatus.setText(
                        "거리 약 "
                            + Math.round(distance[0])
                            + "m · 정확도 약 "
                            + Math.round(location.getAccuracy())
                            + "m\n"
                            + (arrived ? "도착을 확인했어요." : "정확한 위치에서 잠시 기다려 주세요."));
                    if (arrived) {
                      checkingArrival = false;
                      fieldArrived = true;
                      currentArrivalLocation = new android.location.Location(location);
                      locationController.stop();
                      reconcileAndResume();
                    }
                  });
          locationController.start();
        });
  }

  private void screen(String eyebrow, String title, String desc) {
    // Ignore late camera callbacks after a retry, fallback, or screen change.
    captureGeneration++;
    capture = null;
    capturePending = false;
    if (provider != null) provider.unbindAll();
    voiceActive = false;
    locationGateVisible = false;
    setContentView(R.layout.activity_mission);
    ScrollView scroll = findViewById(R.id.mission_scroll);
    Ui.applyInsets(scroll);
    body = findViewById(R.id.mission_body);
    TextView back = Ui.text(this, "←  코스로 돌아가기", 14, Ui.MUTED, true);
    back.setOnClickListener(v -> {
      leaveMission();
    });
    body.addView(back);
    Ui.gap(body, 32);
    body.addView(Ui.label(this, eyebrow));
    Ui.gap(body, 10);
    body.addView(Ui.text(this, title, 30, Ui.INK, true));
    Ui.gap(body, 12);
    body.addView(Ui.text(this, desc, 15, Ui.MUTED, false));
    Ui.gap(body, 24);
  }

  private void action(String title, Runnable r) {
    Button b = Ui.button(this, title, true);
    body.addView(b, new LinearLayout.LayoutParams(-1, -2));
    b.setOnClickListener(v -> r.run());
  }

  private void discovery() {
    checkpoint("DISCOVERY");
    screen(
        (demo ? "DEMO" : "FIELD") + "  ·  기억 " + (index + 1) + " / 5",
        mission.place + "의 기억",
        demo
            ? "테스트 모드에서는 위치 확인을 건너뜁니다. AR 또는 대체 카드로 기억을 발견해 보세요."
            : "도착을 확인했어요. AR 또는 대체 카드로 기억을 발견해 보세요.");
    LinearLayout card = Ui.card(this);
    card.addView(Ui.label(this, "DISCOVER"));
    Ui.gap(card, 12);
    card.addView(Ui.text(this, "잠시 멈춰 주변을 살펴보세요", 20, Ui.INK, true));
    Ui.gap(card, 10);
    card.addView(Ui.text(this, "카메라를 사용해 기억 조각을 찾거나, 카드로 이야기를 이어갈 수 있어요.", 15, Ui.MUTED, false));
    body.addView(card);
    Ui.gap(body, 24);
    action(
        "기억 발견하기",
        () -> {
          Intent i = new Intent(this, ArDiscoveryActivity.class);
          startActivityForResult(i, AR);
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == AR && result == RESULT_OK) {
      method = data != null ? data.getStringExtra("discoveryMethod") : "CARD";
      if (method == null) method = "CARD";
      if (!demo && !fieldArrived) {
        resumeStage = "STORY";
        checkpoint("STORY");
        locationGate();
      } else story();
    } else if (request == SPEECH && result == RESULT_OK && data != null) {
      java.util.ArrayList<String> list =
          data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (list != null && !list.isEmpty()) {
        String s = list.get(0);
        if (com.example.firststep.engine.QuizValidator.matches("생활", s)) {
          completionMethod = "SPEECH";
          if (!demo && !fieldArrived) {
            resumeStage = "QUIZ";
            checkpoint("QUIZ");
            locationGate();
          } else quiz();
        } else
          Toast.makeText(this, "인식된 말: " + s + ". 다시 시도하거나 직접 입력해 주세요.", Toast.LENGTH_LONG).show();
      }
    }
  }

  private void story() {
    checkpoint("STORY");
    screen("STORY  ·  " + (index + 1) + "번째 기억", mission.place, mission.story);
    LinearLayout c = Ui.card(this);
    c.addView(Ui.label(this, "길잡이의 이야기"));
    Ui.gap(c, 14);
    c.addView(Ui.text(this, "“" + mission.story + "”", 19, Ui.INK, true));
    body.addView(c);
    Ui.gap(body, 24);
    action("미션 시작하기", this::game);
  }

  private void game() {
    checkpoint("GAME");
    if (index == 0) photo();
    else if (index == 1) lightGame();
    else if (index == 2) voice();
    else if (index == 3) direction();
    else motion();
  }

  private void photo() {
    checkpoint("GAME");
    screen("MISSION 01  ·  관찰", "이름을 한 장에", "건물 이름 표식이나 촬영이 허용된 안내판을 담아 주세요.");
    if (demo) {
      LinearLayout fallback = Ui.card(this);
      fallback.addView(Ui.label(this, "카메라 없이 테스트"));
      Ui.gap(fallback, 8);
      fallback.addView(Ui.text(this, "카메라가 없어도 테스트 이미지로 사진 확인과 질문·저장을 진행할 수 있어요. 실제 촬영 기록과는 구분됩니다.", 14, Ui.MUTED, false));
      Ui.gap(fallback, 12);
      Button sample = Ui.button(this, "테스트 이미지로 진행", false);
      sample.setOnClickListener(v -> useDemoPhoto());
      fallback.addView(sample);
      body.addView(fallback);
      Ui.gap(body, 20);
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      action(
          "카메라 권한 허용", () -> requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA));
      return;
    }
    TextView cameraStatus = Ui.text(this, "카메라를 연결하고 있어요…", 14, Ui.MUTED, false);
    body.addView(cameraStatus);
    Ui.gap(body, 10);
    PreviewView preview = new PreviewView(this);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    body.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 280)));
    Ui.gap(body, 16);
    Button take = Ui.button(this, "촬영하기", true);
    captureButton = take;
    take.setEnabled(false);
    body.addView(take);
    Ui.gap(body, 10);
    Button retryCamera = Ui.button(this, "카메라 다시 연결", false);
    retryCamera.setOnClickListener(v -> photo());
    body.addView(retryCamera);
    final long generation = captureGeneration;
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          if (generation != captureGeneration || isFinishing() || isDestroyed()) return;
          try {
            provider = future.get();
            provider.unbindAll();
            CameraSelector camera;
            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
              camera = CameraSelector.DEFAULT_BACK_CAMERA;
            else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
              camera = CameraSelector.DEFAULT_FRONT_CAMERA;
            else {
              preview.setVisibility(android.view.View.GONE);
              cameraStatus.setText(demo ? "사용할 수 있는 카메라가 없어요. 위의 테스트 이미지로 계속해 주세요." : "사용할 수 있는 카메라가 없어요. 촬영 가능한 기기에서 진행해 주세요.");
              return;
            }
            Preview p = new Preview.Builder().build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            capture = new ImageCapture.Builder().build();
            provider.bindToLifecycle(this, camera, p, capture);
            if (isFinishing() || isDestroyed()) return;
            take.setEnabled(true);
            cameraStatus.setText("카메라가 연결됐어요. 안내판이나 표식을 담아 주세요.");
            take.setOnClickListener(v -> capturePhoto());
          } catch (Exception e) {
            capture = null;
            preview.setVisibility(android.view.View.GONE);
            cameraStatus.setText(demo ? "카메라를 열지 못했어요. 연결을 다시 시도하거나 위의 테스트 이미지로 계속해 주세요." : "카메라를 열지 못했어요. 다른 앱의 카메라 사용을 종료하고 카메라 연결을 다시 시도해 주세요.");
            android.util.Log.w("FirstStepCamera", "Camera binding failed", e);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void useDemoPhoto() {
    if (!demo || saving || isFinishing() || isDestroyed()) return;
    // A stalled camera capture must not block the explicit test-only escape path.
    captureGeneration++;
    capturePending = false;
    capture = null;
    if (provider != null) provider.unbindAll();
    try {
      File sample = DemoPhoto.create(this);
      discardDraftPhoto();
      photoPath = sample.getAbsolutePath();
      photoPersisted = false;
      completionMethod = "DEMO_PHOTO";
      photoPreview();
    } catch (java.io.IOException e) {
      Toast.makeText(this, "테스트 이미지를 저장하지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요.", Toast.LENGTH_LONG).show();
    }
  }

  private void capturePhoto() {
    if (capture == null || capturePending || isFinishing() || isDestroyed()) return;
    capturePending = true;
    if (captureButton != null) captureButton.setEnabled(false);
    final long generation = ++captureGeneration;
    File f = new File(getFilesDir(), "mission_" + java.util.UUID.randomUUID() + ".jpg");
    ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(f).build();
    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageSavedCallback() {
          public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
            if (generation != captureGeneration || isFinishing() || isDestroyed()) {
              f.delete();
              return;
            }
            capturePending = false;
            discardDraftPhoto();
            photoPath = f.getAbsolutePath();
            photoPersisted = false;
            completionMethod = "PHOTO";
            if (provider != null) provider.unbindAll();
            photoPreview();
          }

          public void onError(@NonNull ImageCaptureException error) {
            f.delete();
            if (generation != captureGeneration || isFinishing() || isDestroyed()) return;
            capturePending = false;
            if (captureButton != null) captureButton.setEnabled(true);
            Toast.makeText(MissionActivity.this, "사진을 저장하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_LONG)
                .show();
          }
        });
  }

  private void discardDraftPhoto() {
    if (!photoPersisted && photoPath != null) {
      File draft = new File(photoPath);
      if (draft.getParentFile() != null && draft.getParentFile().equals(getFilesDir()))
        draft.delete();
    }
    photoPath = null;
  }

  private void photoPreview() {
    checkpoint("PHOTO_PREVIEW");
    boolean sample = demo && "DEMO_PHOTO".equals(completionMethod);
    screen("MISSION 01  ·  확인", sample ? "테스트 이미지 확인" : "잘 담겼나요?", sample ? "실제 촬영한 사진이 아니에요. 이미지 확인 후 질문과 저장 흐름을 테스트합니다." : "표식이 보이는지 확인한 뒤 다음으로 넘어가세요.");
    ImageView image = new ImageView(this);
    Ui.loadPhoto(image, photoPath);
    if (sample) image.setContentDescription("테스트용 캠퍼스 그림. 실제 촬영 사진이 아님");
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    body.addView(image, new LinearLayout.LayoutParams(-1, Ui.dp(this, 300)));
    Ui.gap(body, 16);
    action(
        "사진 확인하고 계속",
        () -> {
          completionMethod = sample ? "DEMO_PHOTO" : "PHOTO";
          quiz();
        });
    Ui.gap(body, 10);
    Button retry = Ui.button(this, "다시 촬영", false);
    body.addView(retry);
    retry.setOnClickListener(v -> {
      discardDraftPhoto();
      completionMethod = null;
      checkpoint("GAME");
      photo();
    });
  }

  private void lightGame() {
    screen(
        "MISSION 02  ·  빛",
        "빛의 기억을 열어요",
        "1.5초 동안 주변 밝기를 측정해요. 센서를 0.8초 가린 뒤 다시 0.8초 동안 빛을 보여 주세요.");
    status =
        Ui.text(
            this,
            light == null ? "조도센서가 없어요. 아래 대체 조작을 이용해 주세요." : "센서를 가리지 말고 기준 밝기를 기다려 주세요…",
            18,
            Ui.INK,
            true);
    LinearLayout c = Ui.card(this);
    c.addView(status);
    body.addView(c);
    Ui.gap(body, 24);
    sensorStage = 1;
    startSensorStage();
    holdFallback();
  }

  private final Runnable lightTick =
      new Runnable() {
        public void run() {
          if (sensorStage != 1 || lightChallenge == null || status == null) return;
          com.example.firststep.sensors.LightChallenge.Stage stage =
              lightChallenge.tick(android.os.SystemClock.elapsedRealtime());
          switch (stage) {
            case CALIBRATING:
              status.setText("센서를 가리지 말고 기준 밝기를 기다려 주세요…");
              break;
            case TOO_DARK:
              status.setText("주변이 너무 어두워요. 밝은 곳에서 미션을 다시 열거나 대체 조작을 이용해 주세요.");
              break;
            case COVER:
              status.setText(
                  "기준 " + Math.round(lightChallenge.getBaseline()) + " lx · 센서를 0.8초 가려 주세요");
              break;
            case RECOVER:
              status.setText("좋아요! 센서를 드러내 빛을 0.8초 보여 주세요.");
              break;
            case COMPLETE:
              completionMethod = "LIGHT";
              quiz();
              return;
          }
          handler.postDelayed(this, 50);
        }
      };

  private void startSensorStage() {
    if (sensorStage == 1 && light != null) {
      lightChallenge = new com.example.firststep.sensors.LightChallenge();
      if (sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)) {
        handler.removeCallbacks(lightTick);
        handler.post(lightTick);
      } else if (status != null) status.setText("조도센서를 시작하지 못했어요. 대체 조작을 이용해 주세요.");
    } else if (sensorStage == 2 && accel != null) {
      motionChallenge.reset();
      if (status != null) status.setText("움직임 0 / 5 · 잠시 멈춘 뒤 가볍게 움직여 주세요");
      sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    } else if (sensorStage == 3 && compassController != null) {
      compassChallenge = new com.example.firststep.sensors.CompassChallenge(compassTarget);
      if (compassProgress != null) compassProgress.setProgress(0);
      compassController.start();
    }
  }

  private void holdFallback() {
    body.addView(Ui.text(this, "대체 조작 · 버튼을 놓지 않고 3초 동안 길게 눌러 주세요", 14, Ui.MUTED, false));
    Ui.gap(body, 8);
    Button hold = Ui.button(this, "길게 눌러 기억 열기", false);
    holdButton = hold;
    body.addView(hold);
    final long[] began = {0};
    Runnable done =
        () -> {
          began[0] = 0;
          hold.setPressed(false);
          completionMethod = "HOLD";
          quiz();
        };
    hold.setOnTouchListener((v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        handler.removeCallbacks(done);
        handler.removeCallbacks(lightTick);
        sensors.unregisterListener(this);
        if (compassController != null) compassController.stop();
        began[0] = android.os.SystemClock.elapsedRealtime();
        hold.setPressed(true);
        hold.setText("누르는 중… 3초 동안 유지해 주세요");
        handler.postDelayed(done, 3000);
        return true;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP
          || e.getActionMasked() == MotionEvent.ACTION_CANCEL
          || (e.getActionMasked() == MotionEvent.ACTION_MOVE
              && (e.getX() < 0
                  || e.getY() < 0
                  || e.getX() > v.getWidth()
                  || e.getY() > v.getHeight()))) {
        handler.removeCallbacks(done);
        if (began[0] != 0) startSensorStage();
        began[0] = 0;
        hold.setPressed(false);
        hold.setText("길게 눌러 기억 열기");
        return true;
      }
      return true;
    });
  }

  private void voice() {
    screen("MISSION 03  ·  듣기", "생활의 단서를 들어요", "안내를 듣거나 읽은 뒤 빈칸에 들어갈 단어를 말하거나 입력해 주세요.");
    voiceActive = true;
    String prompt = "학생 생활시설은 대학생활에 필요한 정보와 편의를 찾는 곳입니다. 빈칸: 학생 ____ 시설. 빈칸의 정답은 생활입니다.";
    LinearLayout c = Ui.card(this);
    c.addView(Ui.text(this, prompt, 18, Ui.INK, true));
    body.addView(c);
    Ui.gap(body, 12);
    body.addView(
        Ui.text(
            this,
            "음성 서비스는 기기 설정에 따라 네트워크를 사용할 수 있어요. 직접 입력으로도 같은 미션을 수행할 수 있습니다.",
            13,
            Ui.MUTED,
            false));
    voiceStatus = Ui.text(this, "소리 없이 읽고 진행해도 괜찮아요.", 14, Ui.MUTED, false);
    body.addView(voiceStatus);
    Ui.gap(body, 16);
    Button listen = Ui.button(this, "안내 듣기", false);
    body.addView(listen);
    listen.setOnClickListener(v -> {
      if (tts == null) {
        voiceStatus.setText("음성 안내를 준비하고 있어요…");
        tts =
            new TextToSpeech(
                this,
                result -> {
                  if (isDestroyed()) return;
                  if (result != TextToSpeech.SUCCESS) {
                    voiceStatus.setText("음성 안내를 사용할 수 없어요. 위의 글을 읽고 진행해 주세요.");
                    return;
                  }
                  int language = tts.setLanguage(Locale.KOREAN);
                  if (language == TextToSpeech.LANG_MISSING_DATA
                      || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    voiceStatus.setText("한국어 음성 데이터가 없어요. 기기 음성 설정을 확인하거나 글을 읽어 주세요.");
                    return;
                  }
                  ttsReady = true;
                  if (voiceActive
                      && getLifecycle()
                          .getCurrentState()
                          .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                    speakGuide(prompt);
                });
      } else if (ttsReady) speakGuide(prompt);
      else voiceStatus.setText("음성 안내가 준비되지 않았어요. 글을 읽고 진행할 수 있어요.");
    });
    Ui.gap(body, 10);
    Button speak = Ui.button(this, "말하기", true);
    body.addView(speak);
    speak.setOnClickListener(v ->
        new android.app.AlertDialog.Builder(this)
            .setTitle("음성 인식 사용")
            .setMessage("기기의 음성 서비스가 음성을 처리하며, 서비스 설정에 따라 제공업체 서버로 전송될 수 있습니다. 계속하거나 직접 입력해 주세요.")
            .setPositiveButton("말하기", (d, w) -> startSpeech())
            .setNegativeButton("직접 입력", null)
            .show());
    Ui.gap(body, 16);
    EditText input = new EditText(this);
    input.setHint("빈칸에 들어갈 두 글자");
    input.setSingleLine(true);
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(32)});
    input.setText(checkpoint.typed);
    input.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

          public void onTextChanged(CharSequence text, int start, int before, int count) {
            checkpoint.typed = text.toString();
            checkpoints.save(checkpoint);
          }

          public void afterTextChanged(android.text.Editable text) {}
        });
    body.addView(input);
    Ui.gap(body, 8);
    action(
        "답 확인",
        () -> {
          if (com.example.firststep.engine.QuizValidator.matches(
              "생활", input.getText().toString())) {
            completionMethod = "TYPED";
            quiz();
          } else input.setError("힌트: 대학의 일상과 관련된 두 글자예요.");
        });
  }

  private void speakGuide(String prompt) {
    if (tts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "guide") == TextToSpeech.ERROR)
      voiceStatus.setText("음성을 재생하지 못했어요. 글을 읽고 진행해 주세요.");
    else voiceStatus.setText("안내를 읽고 있어요. 기기 음량을 확인해 주세요.");
  }

  private void startSpeech() {
    try {
      Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
      i.putExtra(RecognizerIntent.EXTRA_PROMPT, "학생 ____ 시설: 빈칸의 두 글자를 말해 주세요");
      startActivityForResult(i, SPEECH);
    } catch (android.content.ActivityNotFoundException e) {
      voiceStatus.setText("음성 인식 앱이 없어요. 직접 입력해 주세요.");
    }
  }

  private void direction() {
    screen(
        "MISSION 04  ·  방향",
        "나의 방향을 찾다",
        demo
            ? "휴대전화를 평평하게 들고 화면의 윗부분을 자북(0°)으로 맞춰 1.5초 유지해 주세요. 실제 방향센서를 사용하는 연습이며 캠퍼스 경로를 뜻하지 않아요."
            : "안전한 곳에 멈춰 다음 검증 지점의 방향으로 화면 윗부분을 맞추고 1.5초 유지해 주세요.");
    LinearLayout card = Ui.card(this);
    status = Ui.text(this, "방향센서를 준비하고 있어요…", 16, Ui.INK, true);
    card.addView(status);
    Ui.gap(card, 12);
    compassDial = new CompassDialView(this);
    card.addView(compassDial, new LinearLayout.LayoutParams(-1, Ui.dp(this, 240)));
    compassProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    compassProgress.setMax(100);
    card.addView(compassProgress);
    body.addView(card);
    Ui.gap(body, 20);
    compassTarget = 0;
    compassDeclination = 0;
    boolean usable = demo;
    if (!demo) {
      java.util.List<com.example.firststep.data.Mission> catalog =
          com.example.firststep.data.MissionRepository.getInstance(this).getMissions();
      com.example.firststep.data.Mission next =
          index + 1 < catalog.size() ? catalog.get(index + 1) : null;
      if (currentArrivalLocation != null
          && next != null
          && next.coordinateVerified
          && next.latitude != null
          && next.longitude != null) {
        compassTarget =
            com.example.firststep.sensors.CompassMath.bearingDegrees(
                currentArrivalLocation.getLatitude(),
                currentArrivalLocation.getLongitude(),
                next.latitude,
                next.longitude);
        android.hardware.GeomagneticField field =
            new android.hardware.GeomagneticField(
                (float) currentArrivalLocation.getLatitude(),
                (float) currentArrivalLocation.getLongitude(),
                currentArrivalLocation.hasAltitude()
                    ? (float) currentArrivalLocation.getAltitude()
                    : 0,
                System.currentTimeMillis());
        compassDeclination = field.getDeclination();
        usable = Float.isFinite(compassTarget);
      }
    }
    if (usable) {
      compassChallenge = new com.example.firststep.sensors.CompassChallenge(compassTarget);
      compassController =
          new com.example.firststep.sensors.CompassSensorController(
              this,
              new com.example.firststep.sensors.CompassSensorController.Listener() {
                public void onHeading(float magneticDegrees, int accuracy, String message) {
                  if (sensorStage != 3 || isDestroyed() || isFinishing()) return;
                  float heading =
                      com.example.firststep.sensors.CompassMath.normalizeDegrees(
                          magneticDegrees + compassDeclination);
                  com.example.firststep.sensors.CompassChallenge.State result =
                      compassChallenge.update(
                          heading, accuracy, android.os.SystemClock.elapsedRealtime());
                  compassDial.setHeading(heading, compassTarget);
                  compassProgress.setProgress(Math.round(result.progress * 100));
                  status.setText(
                      String.format(
                          java.util.Locale.KOREA,
                          "%s %.0f° · 목표 %.0f°\n%s",
                          demo ? "자북 기준" : "진북 기준",
                          heading,
                          compassTarget,
                          result.message));
                  if (result.complete) {
                    completionMethod = "COMPASS";
                    quiz();
                  }
                }

                public void onUnavailable(String reason) {
                  if (sensorStage != 3 || isDestroyed()) return;
                  compassChallenge.reset();
                  compassProgress.setProgress(0);
                  status.setText(reason + "\n금속 물체에서 떨어져 휴대전화를 천천히 8자 모양으로 움직이거나 대체 조작을 이용해 주세요.");
                }
              });
      sensorStage = 0;
      status.setText("방향 측정 시작을 누른 뒤 표시된 목표를 맞춰 주세요. 허용 오차는 ±12°이며 정확도가 낮으면 다시 보정합니다.");
      action(
          "방향 측정 시작",
          () -> {
            if (!demo
                && (currentArrivalLocation == null
                    || !fieldArrived
                    || android.os.SystemClock.elapsedRealtime()
                            - currentArrivalLocation.getElapsedRealtimeNanos() / 1000000L
                        > 10000)) {
              resumeStage = "GAME";
              locationGate();
              return;
            }
            sensorStage = 3;
            startSensorStage();
          });
      Ui.gap(body, 12);
    } else {
      sensorStage = 0;
      status.setText("다음 지점의 좌표가 아직 검증되지 않았어요. 실제 길안내는 제공하지 않으며 대체 조작으로 시설 안내를 확인할 수 있어요.");
    }
    holdFallback();
  }

  private void motion() {
    screen("MISSION 05  ·  움직임", "활력의 기억", "안전한 곳에 멈춰 움직임 측정 시작을 누른 뒤, 휴대전화를 가볍게 다섯 번 움직여 주세요.");
    status =
        Ui.text(this, accel == null ? "가속도센서가 없어 대체 조작을 이용해 주세요." : "움직임 0 / 5", 20, Ui.INK, true);
    LinearLayout c = Ui.card(this);
    c.addView(status);
    body.addView(c);
    Ui.gap(body, 20);
    motionChallenge.reset();
    sensorStage = 0;
    if (accel != null) {
      action(
          "움직임 측정 시작",
          () -> {
            sensorStage = 2;
            startSensorStage();
          });
      Ui.gap(body, 12);
    }
    holdFallback();
  }

  @Override
  public void onSensorChanged(SensorEvent e) {
    if (sensorStage == 1 && e.sensor.getType() == Sensor.TYPE_LIGHT && lightChallenge != null) {
      lightChallenge.reading(e.values[0]);
    } else if (sensorStage == 2
        && e.sensor.getType() == Sensor.TYPE_ACCELEROMETER
        && status != null) {
      int count =
          motionChallenge.reading(
              e.values[0], e.values[1], e.values[2], android.os.SystemClock.elapsedRealtime());
      status.setText("움직임 " + count + " / 5");
      if (count >= 5) {
        completionMethod = "ACCELEROMETER";
        quiz();
      }
    }
  }

  @Override
  public void onAccuracyChanged(Sensor s, int a) {}

  private void quiz() {
    checkpoint("QUIZ");
    if (tts != null) tts.stop();
    if (compassController != null) compassController.stop();
    sensorStage = 0;
    handler.removeCallbacksAndMessages(null);
    sensors.unregisterListener(this);
    if (provider != null) provider.unbindAll();
    screen("CHECK  ·  시설 알아보기", "기억을 완성해요", mission.question);
    for (String choice : mission.options) {
      Button b = Ui.button(this, choice, false);
      body.addView(b, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
      Ui.gap(body, 8);
      b.setOnClickListener(v -> {
        if (choice.equals(mission.answer)) complete();
        else Toast.makeText(this, "다시 생각해 볼까요? 앞의 이야기가 힌트예요.", Toast.LENGTH_SHORT).show();
      });
    }
  }

  private void complete() {
    if (saving) return;
    if (!missionOwner.equals(new com.example.firststep.auth.AuthSession(this).currentUserId()) || !checkpoints.isCurrentGeneration()) {
      Toast.makeText(this, "계정이나 서버 기록이 변경됐어요. 코스에서 미션을 다시 열어 주세요.", Toast.LENGTH_LONG).show();
      finish();
      return;
    }
    if (completionMethod == null) {
      Toast.makeText(this, "미션 조작을 먼저 완료해 주세요.", Toast.LENGTH_LONG).show();
      game();
      return;
    }
    if (index == 0 && (photoPath == null || !new File(photoPath).isFile())) {
      Toast.makeText(this, "사진을 먼저 촬영하고 확인해 주세요.", Toast.LENGTH_LONG).show();
      photo();
      return;
    }
    saving = true;
    checkpoint("SAVING");
    screen("SAVE", "기억을 저장하고 있어요", "이 기기에 탐방 기록을 안전하게 남기고 있어요.");
    missionRepository
        .completeMission(
            demo
                ? com.example.firststep.data.AppMode.DEMO
                : com.example.firststep.data.AppMode.FIELD,
            mission.id,
            photoPath,
            method,
            completionMethod,
            ok -> {
              saving = false;
              photoPersisted = ok;
              if (isFinishing() || isDestroyed()) return;
              if (ok) showCompletion();
              else {
                screen("SAVE", "기록을 저장하지 못했어요", "이전 미션의 완료 여부와 모드를 확인한 뒤 다시 시도해 주세요.");
                action("저장 다시 시도", this::complete);
              }
            });
  }

  private void showCompletion() {
    checkpoint("SUCCESS");
    screen("MEMORY  ·  획득", "기억 조각을 찾았어요", mission.place + "의 이야기가 도감에 기록됩니다.");
    LinearLayout c = Ui.card(this);
    c.addView(Ui.label(this, "" + (index + 1) + " / 5"));
    Ui.gap(c, 12);
    c.addView(Ui.text(this, mission.place, 24, Ui.INK, true));
    Ui.gap(c, 8);
    c.addView(Ui.text(this, mission.explanation, 15, Ui.MUTED, false));
    body.addView(c);
    Ui.gap(body, 24);
    action(
        "코스로 돌아가기",
        () -> {
          Intent out = new Intent();
          out.putExtra("missionIndex", index);
          out.putExtra("discoveryMethod", method);
          out.putExtra("photoPath", photoPath);
          setResult(Activity.RESULT_OK, out);
          leavingExplicitly = true;
          checkpoints.clear();
          finish();
        });
  }

  @Override
  public void onRequestPermissionsResult(
      int request, @NonNull String[] permissions, @NonNull int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == CAMERA) {
      if (!demo && !fieldArrived) locationGate();
      else photo();
    } else if (request == LOCATION) locationGate();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle state) {
    if (checkpoints != null && checkpoint != null && !leavingExplicitly)
      checkpoints.save(checkpoint);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onPause() {
    super.onPause();
    wasBackgrounded = true;
    fieldArrived = false;
    if (compassController != null) compassController.stop();
    if (sensors != null) sensors.unregisterListener(this);
    checkingArrival = false;
    arrivalPolicy.reset();
    if (locationController != null) locationController.stop();
    handler.removeCallbacksAndMessages(null);
    if (holdButton != null) {
      holdButton.setPressed(false);
      holdButton.setText("길게 눌러 기억 열기");
    }
    if (tts != null) tts.stop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (missionOwner != null && !missionOwner.equals(new com.example.firststep.auth.AuthSession(this).currentUserId())) {
      finish();
      return;
    }
    if (checkpoints != null && !checkpoints.isCurrentGeneration()) { finish(); return; }
    if (!demo
        && wasBackgrounded
        && !fieldArrived
        && !locationGateVisible
        && mission.coordinateVerified
        && !saving) {
      resumeStage = checkpoint.stage;
      locationGate();
      return;
    }
    if (sensorStage != 0) startSensorStage();
  }

  @Override
  protected void onDestroy() {
    captureGeneration++;
    if (compassController != null) compassController.stop();
    // Drafts remain owned by the checkpoint across recreation; explicit exits clean them.
    if (provider != null) provider.unbindAll();
    if (tts != null) tts.shutdown();
    super.onDestroy();
  }
}
