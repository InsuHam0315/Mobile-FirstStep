package com.example.firststep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public final class RouteMapActivity extends AppCompatActivity {
    private com.example.firststep.map.CampusMapView map;
    private com.example.firststep.location.LocationController location;
    private android.widget.TextView status;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean following;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); geographic();
    }
    private void geographic() {
        setContentView(R.layout.activity_geographic_map);Ui.applyInsets(findViewById(R.id.map_root));
        map=findViewById(R.id.campus_map);status=findViewById(R.id.map_status);
        map.configure(com.example.firststep.data.MissionRepository.getInstance(this).getMissions(),new com.example.firststep.map.CampusMapView.Listener(){
            public void status(String text){if(!following)status.setText(text);}
            public void selected(com.example.firststep.data.Mission m){new androidx.appcompat.app.AlertDialog.Builder(RouteMapActivity.this).setTitle(m.place).setMessage(m.explanation+"\n\n도착 확인과 미션 시작은 코스 화면에서 진행해 주세요.").setPositiveButton("확인",null).show();}
        });
        findViewById(R.id.map_zoom_in).setOnClickListener(v->map.zoom(1));
        findViewById(R.id.map_zoom_out).setOnClickListener(v->map.zoom(-1));
        findViewById(R.id.map_campus).setOnClickListener(v->{stopLocation();map.current(null);map.campus();status.setText("군산대학교 캠퍼스 중심으로 이동했습니다.");});
        findViewById(R.id.map_retry).setOnClickListener(v->map.retry());
        findViewById(R.id.map_attribution).setOnClickListener(v->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));
        findViewById(R.id.map_location).setOnClickListener(v->locate());
        findViewById(R.id.map_buildings).setOnClickListener(v->showBuildingList());
        findViewById(R.id.map_route).setOnClickListener(v->{stopLocation();map.close();map=null;route();});
        findViewById(R.id.map_back).setOnClickListener(v->finish());
    }

    private void showBuildingList() {

        java.util.List<com.example.firststep.data.Mission> missions =
            com.example.firststep.data.MissionRepository
                .getInstance(this).getMissions();

        if (missions == null || missions.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("건물 목록")
                .setMessage("등록된 건물이 없습니다.")
                .setPositiveButton("확인", null)
                .show();
            return;
        }

        String[] names = new String[missions.size()];

        for (int i = 0; i < missions.size(); i++) {
            com.example.firststep.data.Mission m = missions.get(i);

            names[i] = (i + 1) + ". " + m.place;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("탐험할 건물 선택")
            .setItems(names, (dialog, which) -> {

                com.example.firststep.data.Mission selected =
                    missions.get(which);

                showBuildingInfo(selected, which);
            })
            .setNegativeButton("닫기", null)
            .show();
    }

    private void showBuildingInfo(
        com.example.firststep.data.Mission mission,
        int index
    ) {

        String message =
            mission.explanation
            + "\n\n현재 건물 정보는 MVP 임시 데이터입니다."
            + "\n실제 위치 및 시설 정보는 검증 전입니다."
            + "\n\n테스트 모드에서는 현장 도착 없이 체험할 수 있습니다.";

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(mission.place)
            .setMessage(message)
            .setPositiveButton("테스트 미션 시작", (dialog, which) -> {

                android.content.Intent intent =
                    new android.content.Intent(
                        this,
                        BuildingDiscoveryActivity.class
                    );

                intent.putExtra("missionIndex", index);
                intent.putExtra("demo", true);

                startActivity(intent);
            })
            .setNegativeButton("취소", null)
            .show();
    }
    private void locate() {
        if(!com.example.firststep.location.LocationController.hasPermission(this)) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},730);return;
        }
        stopLocation();following=true;
        location=new com.example.firststep.location.LocationController(this,(fix,message)->{
            if(map==null)return;
            if(fix==null){map.current(null);status.setText(message);return;}
            long age=android.os.SystemClock.elapsedRealtime()-fix.getElapsedRealtimeNanos()/1000000;
            if(!fix.hasAccuracy()||age<0||age>10000){map.current(null);status.setText("최신 위치를 기다리고 있어요.");return;}
            map.current(fix);map.center(fix.getLatitude(),fix.getLongitude());
            status.setText("현재 위치 · 정확도 약 "+Math.round(fix.getAccuracy())+"m · 도착 판정은 코스에서 확인하세요.");
            handler.removeCallbacksAndMessages(null);handler.postDelayed(()->{if(map!=null){map.current(null);status.setText("위치가 오래되어 표시를 지웠어요. 다시 내 위치를 눌러 주세요.");}stopLocation();},Math.max(0,10000-age));
        });location.start();
    }
    private void stopLocation(){following=false;if(location!=null)location.stop();handler.removeCallbacksAndMessages(null);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==730&&com.example.firststep.location.LocationController.hasPermission(this))locate();else if(request==730)status.setText("위치 권한 없이도 지도를 둘러볼 수 있어요.");}
    @Override protected void onPause(){stopLocation();if(map!=null){map.current(null);map.active(false);}super.onPause();}
    @Override protected void onResume(){super.onResume();if(map!=null)map.active(true);}
    @Override protected void onDestroy(){if(map!=null)map.close();super.onDestroy();}
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
