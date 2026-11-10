package com.example.firststep;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.firststep.auth.AuthSession;

public class WelcomeActivity extends AppCompatActivity {

    private LinearLayout card;
    private boolean startStep = false;
    private String previousAccount = "guest";

    private final int PINK = Color.rgb(249, 169, 193);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(220, 207, 216);
    private final int DARK = Color.rgb(42, 27, 41);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        previousAccount =
                new AuthSession(this).currentUserId();

        if (state != null) {
            startStep = state.getBoolean("startStep", false);
        } else if (!"guest".equals(previousAccount)) {
            startStep = true;
        }

        createScreen();
        render();
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }

        return t;
    }

    private Button button(
            String value,
            int background,
            int foreground
    ) {
        Button b = new Button(this);

        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(foreground);
        b.setBackgroundTintList(null);
        b.setBackground(shape(background, 18));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(54)
                );

        params.topMargin = dp(12);
        b.setLayoutParams(params);

        return b;
    }

    private void createScreen() {

        FrameLayout root = new FrameLayout(this);

        ImageView background = new ImageView(this);

        background.setImageResource(R.drawable.home);
        background.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        root.addView(
                background,
                new FrameLayout.LayoutParams(-1, -1)
        );

        View overlay = new View(this);

        overlay.setBackground(
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {
                                0x66000000,
                                0x17000000,
                                0xAA130D19,
                                0xF0110B15
                        }
                )
        );

        root.addView(
                overlay,
                new FrameLayout.LayoutParams(-1, -1)
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(
                dp(24),
                dp(45),
                dp(24),
                dp(28)
        );

        scroll.addView(page);

        TextView brand = label(
                "FIRST STEP  /  KUNSAN NATIONAL UNIVERSITY",
                11,
                WHITE,
                true
        );

        brand.setLetterSpacing(0.08f);
        page.addView(brand);

        TextView tagline = label(
                "캠퍼스의 일상을\n새로운 모험으로",
                18,
                WHITE,
                true
        );

        LinearLayout.LayoutParams taglineParams =
                new LinearLayout.LayoutParams(-1, -2);

        taglineParams.topMargin = dp(20);

        page.addView(tagline, taglineParams);

        View space = new View(this);

        page.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        0,
                        1f
                )
        );

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(23),
                dp(26),
                dp(23),
                dp(24)
        );

        card.setBackground(
                shape(0xED211923, 28)
        );

        page.addView(card);

        TextView footer = label(
                "EXPLORE  ·  DISCOVER  ·  REMEMBER",
                10,
                MUTED,
                true
        );

        footer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-1, -2);

        footerParams.topMargin = dp(20);

        page.addView(footer, footerParams);

        root.addView(
                scroll,
                new FrameLayout.LayoutParams(-1, -1)
        );

        setContentView(root);
    }

    private void render() {

        card.removeAllViews();

        TextView badge = label(
                "CAMPUS ADVENTURE",
                11,
                PINK,
                true
        );

        badge.setLetterSpacing(0.13f);
        card.addView(badge);

        TextView title = label(
                "첫발자국",
                36,
                WHITE,
                true
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);

        titleParams.topMargin = dp(9);
        card.addView(title, titleParams);

        TextView subtitle = label(
                startStep
                        ? "이제 캠퍼스의 다섯 가지 기억을 찾아볼까요?"
                        : "나만의 캠퍼스 탐험을 시작해 보세요.",
                14,
                MUTED,
                false
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(-1, -2);

        subtitleParams.topMargin = dp(10);
        subtitleParams.bottomMargin = dp(14);

        card.addView(subtitle, subtitleParams);

        if (startStep) {

            card.addView(label(
                    "01  지도에서 탐험 장소 선택\n\n"
                    + "02  숨겨진 캐릭터 발견\n\n"
                    + "03  미션 수행과 기억 수집",
                    14,
                    WHITE,
                    false
            ));

            Button start = button(
                    "탐험 시작하기  →",
                    PINK,
                    DARK
            );

            card.addView(start);

            start.setOnClickListener(v -> {
                startActivity(
                        new Intent(
                                this,
                                MainActivity.class
                        )
                );
                finish();
            });

            Button back = button(
                    "이전으로",
                    0xFF40313F,
                    WHITE
            );

            card.addView(back);

            back.setOnClickListener(v -> {
                startStep = false;
                render();
            });

        } else {

            Button login = button(
                    "계정으로 로그인",
                    PINK,
                    DARK
            );

            card.addView(login);

            login.setOnClickListener(v ->
                    startActivity(
                            new Intent(
                                    this,
                                    AuthActivity.class
                            )
                    )
            );

            Button guest = button(
                    "게스트로 시작하기  →",
                    0xFF40313F,
                    WHITE
            );

            card.addView(guest);

            guest.setOnClickListener(v -> {
                startStep = true;
                render();
            });
        }

        Button guide = button(
                "탐험 방법 알아보기",
                0xFF312533,
                MUTED
        );

        card.addView(guide);

        guide.setOnClickListener(v -> showGuide());
    }

    private void showGuide() {

        new AlertDialog.Builder(this)
                .setTitle("첫발자국 탐험 가이드")
                .setMessage(
                        "첫발자국은 캠퍼스를 직접 탐험하며 "
                        + "미션을 수행하는 앱이에요.\n\n"
                        + "1. 지도에서 탐험할 건물을 선택해요.\n\n"
                        + "2. 캐릭터 발견 미션에 도전해요.\n\n"
                        + "3. 퀴즈와 사진 미션을 수행해요.\n\n"
                        + "4. 완료한 장소의 기억을 수집해요.\n\n"
                        + "현장 기능은 기기의 위치 및 "
                        + "센서 지원 상태에 따라 달라질 수 있어요."
                )
                .setPositiveButton("확인", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        String account =
                new AuthSession(this).currentUserId();

        if (card != null
                && !"guest".equals(account)
                && !account.equals(previousAccount)) {

            startStep = true;
            render();
        }

        previousAccount = account;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("startStep", startStep);
        super.onSaveInstanceState(state);
    }
}