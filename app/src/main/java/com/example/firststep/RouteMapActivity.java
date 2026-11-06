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
    private boolean following, followCenter, locationWanted;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); geographic();
    }
    private void geographic() {
        setContentView(R.layout.activity_geographic_map);Ui.applyInsets(findViewById(R.id.map_root));
        map=findViewById(R.id.campus_map);status=findViewById(R.id.map_status);
        map.configure(com.example.firststep.data.MissionRepository.getInstance(this).getMissions(),new com.example.firststep.map.CampusMapView.Listener(){
            public void status(String text){if(!following)status.setText(text);}
            public void panned(){followCenter=false;}
            public void selected(com.example.firststep.data.Mission m){new androidx.appcompat.app.AlertDialog.Builder(RouteMapActivity.this).setTitle(m.place).setMessage(m.explanation+"\n\n도착 확인과 미션 시작은 코스 화면에서 진행해 주세요.").setPositiveButton("확인",null).show();}
        });
        findViewById(R.id.map_zoom_in).setOnClickListener(v->map.zoom(1));
        findViewById(R.id.map_zoom_out).setOnClickListener(v->map.zoom(-1));
        findViewById(R.id.map_campus).setOnClickListener(v->{followCenter=false;map.campus();});
        findViewById(R.id.map_retry).setOnClickListener(v->map.retry());
        findViewById(R.id.map_attribution).setOnClickListener(v->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))));
        findViewById(R.id.map_location).setOnClickListener(v->locate());
        findViewById(R.id.map_route).setOnClickListener(v->{locationWanted=false;stopLocation();map.close();map=null;route();});
        findViewById(R.id.map_back).setOnClickListener(v->finish());
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
