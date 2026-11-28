package com.example.firststep;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.firststep.data.Mission;
import com.example.firststep.data.MissionRepository;
import com.example.firststep.facility.Facility;
import com.example.firststep.facility.FacilityRepository;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RouteMapActivity extends AppCompatActivity {
    private static final String EXTRA_BROWSE_ONLY = "firststep.campus_map.browse_only";
    private static final String EXTRA_FACILITY_ID = "firststep.campus_map.facility_id";
    private static final String EXTRA_MAIN_NAV = "firststep.campus_map.main_navigation";

    /** Main tab uses the approved browse-only map without constructing legacy missions. */
    public static Intent mainBrowseIntent(Context context) {
        return browseIntent(context).putExtra(EXTRA_MAIN_NAV, true);
    }

    /** Opens campus map browsing without a legacy route or mission repository. */
    public static Intent browseIntent(Context context) {
        return new Intent(context, RouteMapActivity.class).putExtra(EXTRA_BROWSE_ONLY, true);
    }

    /** Opens the real campus map with catalog context. Facility identity never sets a pin. */
    public static Intent facilityBrowseIntent(Context context, String facilityId) {
        return browseIntent(context).putExtra(EXTRA_FACILITY_ID, facilityId);
    }

    private boolean following, followCenter, locationWanted, browseOnly;
    private String facilityId;
    private int facilityRead;
    private final ExecutorService facilityWorker = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Intent> lookupFacility = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!browseOnly) return;
                findViewById(R.id.map_facility_lookup).setEnabled(true);
                if (result.getResultCode() != RESULT_OK) return;
                String selection = FacilityDirectoryActivity.selectedFacilityId(result.getData());
                if (selection == null) return;
                facilityId = selection;
                showFacilityContext();
            });

    private com.example.firststep.map.CampusMapView map;
    private com.example.firststep.location.LocationController location;
    private android.widget.TextView status;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        browseOnly = getIntent().getBooleanExtra(EXTRA_BROWSE_ONLY, false);
        if (browseOnly) {
            facilityId = state == null ? getIntent().getStringExtra(EXTRA_FACILITY_ID)
                    : state.getString(EXTRA_FACILITY_ID);
        }
        geographic();
    }
    private void geographic() {
        setContentView(R.layout.activity_geographic_map);Ui.applyInsets(findViewById(R.id.map_root));
        View navigation = findViewById(R.id.game_main_navigation);
        boolean mainNavigation = browseOnly && getIntent().getBooleanExtra(EXTRA_MAIN_NAV, false);
        navigation.setVisibility(mainNavigation ? View.VISIBLE : View.GONE);
        if (mainNavigation) {
            findViewById(R.id.game_nav_map).setSelected(true);
            findViewById(R.id.game_nav_home).setOnClickListener(v -> returnToMain(false));
            findViewById(R.id.game_nav_my).setOnClickListener(v -> returnToMain(true));
        }
        map=findViewById(R.id.campus_map);status=findViewById(R.id.map_status);
        List<Mission> missions = browseOnly ? Collections.emptyList() : MissionRepository.getInstance(this).getMissions();
        map.configure(missions,new com.example.firststep.map.CampusMapView.Listener(){
            public void status(String text){if(!following)status.setText(text);}
            public void panned(){followCenter=false;}
            public void selected(Mission m){new androidx.appcompat.app.AlertDialog.Builder(RouteMapActivity.this).setTitle(m.place).setMessage(m.explanation+"\n\n도착 확인과 미션 시작은 코스 화면에서 진행해 주세요.").setPositiveButton("확인",null).show();}
        });
        findViewById(R.id.map_zoom_in).setOnClickListener(v->map.zoom(1));
        findViewById(R.id.map_zoom_out).setOnClickListener(v->map.zoom(-1));
        findViewById(R.id.map_campus).setOnClickListener(v->{followCenter=false;map.campus();});
        findViewById(R.id.map_retry).setOnClickListener(v->map.retry());
        findViewById(R.id.map_attribution).setOnClickListener(v->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));
        findViewById(R.id.map_location).setOnClickListener(v->locate());
        View routeButton = findViewById(R.id.map_route);
        if (browseOnly) {
            routeButton.setVisibility(View.GONE);
            View facilityLookup = findViewById(R.id.map_facility_lookup);
            facilityLookup.setVisibility(View.VISIBLE);
            facilityLookup.setOnClickListener(v -> {
                v.setEnabled(false);
                lookupFacility.launch(FacilityDirectoryActivity.mapLookupIntent(this));
            });
            showFacilityContext();
        }
        else routeButton.setOnClickListener(v->{locationWanted=false;stopLocation();map.close();map=null;route();});
        findViewById(R.id.map_back).setOnClickListener(v->finish());
    }
    private void showFacilityContext() {
        TextView context = findViewById(R.id.map_facility_context);
        if (context == null) return;
        final int request = ++facilityRead;
        final String selected = facilityId;
        if (selected == null) {
            context.setVisibility(View.GONE);
            return;
        }
        context.setText(R.string.map_facility_context_loading);
        context.setVisibility(View.VISIBLE);
        Context app = getApplicationContext();
        facilityWorker.execute(() -> {
            String name = null;
            try {
                for (Facility facility : new FacilityRepository(app).load()) {
                    if (selected.equals(facility.id)) {
                        name = facility.name;
                        break;
                    }
                }
            } catch (Exception unavailable) {
                // Keep the map usable when the local catalog cannot be read.
            }
            String displayName = name;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != facilityRead) return;
                if (displayName == null) context.setText(R.string.map_facility_context_unavailable);
                else context.setText(getString(R.string.map_facility_context_unverified, displayName));
            });
        });
    }

    private void returnToMain(boolean myPage) {
        startActivity(com.example.firststep.game.ui.identity.IdentityActivity.tabIntent(this, myPage));
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (browseOnly) state.putString(EXTRA_FACILITY_ID, facilityId);
    }

    private void locate() {
        if(!com.example.firststep.location.LocationController.hasPermission(this)) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},730);return;
        }
        stopLocation();following=true;followCenter=true;locationWanted=true;
        location=new com.example.firststep.location.LocationController(this,(fix,message)->{
            if(map==null)return;
            if(fix==null){map.current(null);status.setText(message);return;}
            long age=android.os.SystemClock.elapsedRealtime()-fix.getElapsedRealtimeNanos()/1000000;
            if(!fix.hasAccuracy()||age<0||age>10000){map.current(null);status.setText("최신 위치를 기다리고 있어요.");return;}
            map.current(fix);if(followCenter)map.center(fix.getLatitude(),fix.getLongitude());
            boolean emulator=android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish");
            String origin=emulator?"에뮬레이터 위치 · 가상 좌표":fix.isFromMockProvider()?"모의 위치 · 시험용 좌표":"현재 위치";
            status.setText(String.format(java.util.Locale.KOREA,"%s\n위도 %.6f · 경도 %.6f · 정확도 약 %dm",origin,fix.getLatitude(),fix.getLongitude(),Math.round(fix.getAccuracy())));
            handler.removeCallbacksAndMessages(null);handler.postDelayed(()->{if(map!=null){map.current(null);status.setText("최신 위치 수신 대기 중 · 오래된 위치 표시는 지웠어요.");}},Math.max(0,10000-age));
        });location.start();
    }
    private void stopLocation(){following=false;if(location!=null)location.stop();handler.removeCallbacksAndMessages(null);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==730&&com.example.firststep.location.LocationController.hasPermission(this))locate();else if(request==730)status.setText("위치 권한 없이도 지도를 둘러볼 수 있어요.");}
    @Override protected void onPause(){stopLocation();if(map!=null){map.current(null);map.active(false);}super.onPause();}
    @Override protected void onResume(){super.onResume();if(map!=null){map.active(true);if(locationWanted){boolean keepFollowing=followCenter;locate();followCenter=keepFollowing;}}}
    @Override protected void onDestroy() {
        facilityRead++;
        facilityWorker.shutdownNow();
        if (map != null) map.close();
        super.onDestroy();
    }
    private void route(){setContentView(R.layout.activity_route_map);
        Ui.applyInsets(findViewById(R.id.route_root));
        ((android.widget.Button)findViewById(R.id.route_back)).setText("캠퍼스 지도로 돌아가기");
        findViewById(R.id.route_back).setOnClickListener(v -> geographic());
    }
    public static class RouteView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public RouteView(Context context, AttributeSet attrs) { super(context,attrs); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density=getResources().getDisplayMetrics().density;
            float left=36*density, right=getWidth()-36*density, top=32*density, step=86*density;
            float[] xs={left,right,left,right,left};
            String[] labels={"수업 건물","도서관","생활시설","야외 지점","활동 공간"};
            String[] inputs={"사진으로 관찰","빛으로 발견","목소리로 기억","방향 알아보기","움직임으로 완성"};
            Path path=new Path();path.moveTo(xs[0],top);
            for(int i=1;i<5;i++)path.cubicTo(xs[i-1],top+(i-.5f)*step,xs[i],top+(i-.5f)*step,xs[i],top+i*step);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*density);paint.setColor(0xFFE0D6C7);canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<5;i++){
                float y=top+i*step;paint.setColor(Ui.RUST);canvas.drawCircle(xs[i],y,20*density,paint);
                paint.setColor(0xFFFFFFFF);paint.setTextSize(16*density);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(""+(i+1),xs[i],y+6*density,paint);
                boolean leftSide=i%2==0;float tx=leftSide?xs[i]+34*density:xs[i]-34*density;
                paint.setTextAlign(leftSide?Paint.Align.LEFT:Paint.Align.RIGHT);paint.setColor(Ui.INK);paint.setTextSize(18*density);canvas.drawText(labels[i],tx,y-2*density,paint);
                paint.setColor(Ui.MUTED);paint.setTextSize(12*density);canvas.drawText(inputs[i],tx,y+19*density,paint);
            }
        }
    }
}
