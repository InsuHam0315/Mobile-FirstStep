package com.example.firststep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.firststep.ar.ArRenderer;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;

/** Optional tracked AR; card discovery is explicitly reported as a different completion method. */
public final class ArDiscoveryActivity extends AppCompatActivity {
    private GLSurfaceView surface;
    private TextView status;
    private ArRenderer renderer;
    private Session session;
    private boolean installRequested, permissionRequested, active, finished, arRequested;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_ar_discovery);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ar_root), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        status = findViewById(R.id.ar_status); surface = findViewById(R.id.ar_surface);
        surface.setEGLContextClientVersion(2); surface.setPreserveEGLContextOnPause(true);
        renderer = new ArRenderer(new ArRenderer.Listener() {
            @Override public void onMessage(String text) { runOnUiThread(() -> status.setText(text)); }
            @Override public void onDiscovered() { runOnUiThread(() -> complete("AR")); }
        });
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        surface.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) { renderer.tap(event.getX(), event.getY()); view.performClick(); }
            return true;
        });
        findViewById(R.id.ar_start).setOnClickListener(v -> { arRequested = true; resumeAr(); });
        findViewById(R.id.ar_fallback).setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("빛나는 기억 카드").setMessage("길잡이가 남긴 기억을 발견했어요. 이어서 장소의 이야기와 미션을 확인해 보세요.\n\n일반 카드 방식으로 기록됩니다.")
                .setPositiveButton("기억 열기", (dialog, which) -> complete("CARD")).setNegativeButton("취소", null).show());
        findViewById(R.id.ar_cancel).setOnClickListener(v -> finish());
    }
    private void complete(String method) {
        if (finished) return; finished = true;
        setResult(RESULT_OK, new Intent().putExtra("discoveryMethod", method)); finish();
    }
    @Override protected void onResume() { super.onResume(); if (arRequested) resumeAr(); }
    private void resumeAr() {
        if (active || finished) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.setText("AR은 카메라 권한이 필요해요. 권한 없이 일반 카드를 선택할 수도 있어요.");
            if (!permissionRequested) { permissionRequested = true; ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 90); }
            return;
        }
        try {
            if (session == null) {
                ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
                if (availability.isUnsupported()) { status.setText("이 기기는 공간 추적 AR을 지원하지 않아요. 일반 카드로 진행해 주세요."); return; }
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true; return;
                }
                session = new Session(this);
                Config config = new Config(session); config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
                session.configure(config);
            }
            session.resume(); renderer.setSession(session); surface.onResume();
            surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY); active = true;
            renderer.setRotation(getWindowManager().getDefaultDisplay().getRotation());
        } catch (Exception error) {
            status.setText("AR을 시작할 수 없어요. Google Play AR 서비스와 카메라 상태를 확인하거나 일반 카드로 진행해 주세요.");
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants); if (request == 90) resumeAr();
    }
    @Override protected void onPause() {
        surface.onPause();
        if (active) { renderer.setSession(null); session.pause(); active = false; }
        super.onPause();
    }
    @Override protected void onDestroy() { if (session != null) session.close(); super.onDestroy(); }
}
