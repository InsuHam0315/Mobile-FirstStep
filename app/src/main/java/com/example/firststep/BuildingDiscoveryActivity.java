package com.example.firststep;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class BuildingDiscoveryActivity extends AppCompatActivity
        implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextToSpeech tts;

    private boolean ttsReady = false;
    private boolean discovered = false;
    private String buildingName = "디지털정보관";
    private long lastShake = 0;

    private TextView character;
    private TextView status;
    private Button discoverButton;
    private Button voiceButton;
    private Button missionStartButton;

    private final String introduction =
            "안녕! 첫발자국 캠퍼스 탐험에 온 걸 환영해. "
            + "여기는 디지털정보관 탐험 화면이야. "
            + "건물의 정확한 시설 정보와 이용 방법은 "
            + "공식 자료를 확인한 후 안내할 예정이야.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 선택한 미션 번호에 해당하는 건물 표시
        int selectedMission = getIntent().getIntExtra("missionIndex", -1);

        String[] buildingNames = {
            "디지털정보관",
            "중앙도서관",
            "학생회관",
            "황룡도서관",
            "학생생활관"
        };

        if (selectedMission >= 0 &&
                selectedMission < buildingNames.length) {
            buildingName = buildingNames[selectedMission];
        }

        createScreen();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {
            accelerometer =
                    sensorManager.getDefaultSensor(
                            Sensor.TYPE_ACCELEROMETER);
        }

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int language = tts.setLanguage(Locale.KOREAN);
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        
    }

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void createScreen() {

        int ink = Color.rgb(35, 42, 55);
        int pink = Color.rgb(178, 77, 112);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(255, 246, 249));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(30));

        scroll.addView(root);

        TextView label = text(
                "FIRST STEP / CAMPUS DISCOVERY",
                14, pink);

        root.addView(label);

        TextView title = text(
                "\n" + buildingName + " 탐험\n",
                29, ink);

        root.addView(title);

        TextView description = text(
                "휴대폰을 흔들어 숨어 있는 안내 캐릭터를 찾아보세요.\n",
                17, Color.DKGRAY);

        root.addView(description);

        character = text("?", 100, pink);
        character.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams characterParams =
                new LinearLayout.LayoutParams(
                        -1, dp(200));

        root.addView(character, characterParams);

        status = text(
                "아직 캐릭터를 발견하지 못했어요.",
                18, ink);

        status.setGravity(Gravity.CENTER);
        root.addView(status);

        discoverButton = new Button(this);
        discoverButton.setText("캐릭터 발견하기 (테스트)");

        root.addView(discoverButton);

        discoverButton.setOnClickListener(v -> discover());

        voiceButton = new Button(this);
        voiceButton.setText("건물 소개 다시 듣기");
        voiceButton.setEnabled(false);

        root.addView(voiceButton);

        // FIRSTSTEP_MISSION_LINK_V1

        LinearLayout missionCard = new LinearLayout(this);
        missionCard.setOrientation(LinearLayout.VERTICAL);
        missionCard.setPadding(
                dp(20), dp(22), dp(20), dp(22)
        );

        android.graphics.drawable.GradientDrawable cardBg =
                new android.graphics.drawable.GradientDrawable();

        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), 0xFFFFD8E6);

        missionCard.setBackground(cardBg);

        LinearLayout.LayoutParams missionCardParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        missionCardParams.topMargin = dp(24);

        root.addView(missionCard, missionCardParams);

        TextView missionLabel = text(
                "NEXT ADVENTURE",
                12,
                0xFFB55378
        );

        missionCard.addView(missionLabel);

        TextView missionTitle = text(
                "캐릭터를 발견하고 미션에 도전하세요",
                21,
                0xFF252B38
        );

        missionTitle.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);

        titleParams.topMargin = dp(12);

        missionCard.addView(
                missionTitle,
                titleParams
        );

        TextView missionDescription = text(
                "숨겨진 캐릭터를 발견하면 "
                + "건물 탐험 미션을 시작할 수 있어요.",
                14,
                0xFF777985
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(-1, -2);

        descriptionParams.topMargin = dp(10);

        missionCard.addView(
                missionDescription,
                descriptionParams
        );

        missionStartButton = new Button(this);

        missionStartButton.setText(
                "캐릭터를 먼저 발견해 주세요"
        );

        missionStartButton.setAllCaps(false);
        missionStartButton.setTextSize(16);
        missionStartButton.setTextColor(Color.WHITE);

        android.graphics.drawable.GradientDrawable buttonBg =
                new android.graphics.drawable.GradientDrawable();

        buttonBg.setColor(0xFFC45883);
        buttonBg.setCornerRadius(dp(16));

        missionStartButton.setBackground(buttonBg);
        missionStartButton.setEnabled(false);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(56)
                );

        buttonParams.topMargin = dp(20);

        missionCard.addView(
                missionStartButton,
                buttonParams
        );

        missionStartButton.setOnClickListener(
                v -> openLinkedMission()
        );


        voiceButton.setOnClickListener(v -> speak());

        TextView notice = text(
                "\n[개발 테스트]\n"
                + "현재는 카메라 AR 화면이 아닌 캐릭터 발견 기능의 "
                + "시제품입니다.\n\n"
                + "건물 정보, GPS 도착 판정, 퀴즈 및 완료 처리는 "
                + "후속 단계에서 연결합니다.",
                14, Color.GRAY);

        root.addView(notice);

        Button back = new Button(this);
        back.setText("홈으로 돌아가기");
        root.addView(back);

        back.setOnClickListener(v -> finish());

        setContentView(scroll);
    }

        // FIRSTSTEP_MISSION_NAVIGATION_V1
    private void openLinkedMission() {

        com.example.firststep.data.MissionRepository repository =
                com.example.firststep.data.MissionRepository
                        .getInstance(this);

        java.util.List<com.example.firststep.data.Mission> missions =
                repository.getMissions();

        if (missions == null || missions.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("미션 정보 없음")
                    .setMessage("현재 이용할 수 있는 미션이 없어요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        int missionIndex =
                getIntent().getIntExtra("missionIndex", -1);

        if (missionIndex >= 0 && missionIndex < missions.size()) {
            launchMission(missionIndex);
            return;
        }

        // 건물과 미션 번호가 연결되지 않은 경우에는
        // 잘못된 미션을 자동 실행하지 않고 선택하도록 안내한다.
        String[] choices = new String[missions.size()];

        for (int i = 0; i < missions.size(); i++) {
            choices[i] = (i + 1) + "번 장소 미션";
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("탐험 미션 선택")
                .setMessage("진행할 장소의 미션을 선택해 주세요.")
                .setItems(
                        choices,
                        (dialog, which) -> launchMission(which)
                )
                .setNegativeButton("취소", null)
                .show();
    }

    private void launchMission(int missionIndex) {

        boolean demo = getIntent().getBooleanExtra(
                "demo",
                com.example.firststep.data.MissionRepository
                        .getInstance(this)
                        .getMode()
                        == com.example.firststep.data.AppMode.DEMO
        );

        android.content.Intent intent =
                new android.content.Intent(
                        this,
                        MissionActivity.class
                );

        intent.putExtra("missionIndex", missionIndex);
        intent.putExtra("demo", demo);

        startActivity(intent);
    }
    private void discover() {
        if (discovered) return;

        discovered = true;

        character.setText("용");
        status.setText(
                "캐릭터 발견 성공!\n\n"
                + buildingName + " 탐험에 오신 것을 환영합니다.");

        discoverButton.setEnabled(false);
        missionStartButton.setEnabled(true);
        missionStartButton.setText("미션 시작하기  →");
        voiceButton.setEnabled(true);

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        speak();
    }

    private void speak() {
        if (!ttsReady || tts == null) {
            android.widget.Toast.makeText(
                    this,
                    "한국어 음성 엔진을 사용할 수 없습니다.",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }

        tts.speak(
                introduction,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "building_intro");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!discovered
                && sensorManager != null
                && accelerometer != null) {
            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (tts != null) {
            tts.stop();
        }

        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (discovered) return;

        if (event.sensor.getType()
                != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double force = Math.sqrt(x*x + y*y + z*z);

        long now = android.os.SystemClock.elapsedRealtime();

        if (force > 18 && now - lastShake > 1500) {
            lastShake = now;
            discover();
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        super.onDestroy();
    }
}