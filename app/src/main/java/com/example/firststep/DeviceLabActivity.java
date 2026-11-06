package com.example.firststep;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

/** Debug-only entry points: exercises device APIs without changing exploration progress. */
public final class DeviceLabActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensors;
    private TextView readings;
    private String light = "수신 대기", movement = "수신 대기", magnetic = "수신 대기";
    private long lastDisplay;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG) { finish(); return; }
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.column(this);
        int padding = Ui.dp(this, 24);
        page.setPadding(padding,padding,padding,padding);page.setBackgroundColor(Ui.PAPER);
        scroll.addView(page);setContentView(scroll);Ui.applyInsets(scroll);
        page.addView(Ui.label(this,"DEVICE LAB · 개발용"));Ui.gap(page,12);
        page.addView(Ui.text(this,"직접 보고 확인하기",28,Ui.INK,true));Ui.gap(page,12);
        page.addView(Ui.text(this,"촬영·공간 AR·지도 위치를 각각 실행해 보세요. 이 화면의 확인 결과는 탐방 완료 기록에 추가되지 않습니다.",15,Ui.MUTED,false));Ui.gap(page,20);
        addButton(page,"카메라 촬영 확인",CameraCheckActivity.class);
        addButton(page,"공간 AR 확인",ArDiscoveryActivity.class);
        addButton(page,"군산대 지도 · 위치 확인",RouteMapActivity.class);
        Ui.gap(page,16);
        LinearLayout card=Ui.card(this);
        card.addView(Ui.text(this,"기기에서 수신한 센서 값",19,Ui.INK,true));Ui.gap(card,10);
        readings=Ui.text(this,"센서 확인 중",14,Ui.MUTED,false);card.addView(readings);page.addView(card);
        Ui.gap(page,18);
        page.addView(Ui.text(this,"가상 카메라 장면·주입한 위치·센서 값은 현장 검증과 구분해 주세요. 실제 촬영은 PC에 연결된 카메라 입력이 필요합니다.",14,Ui.MUTED,false));
        Ui.gap(page,16);Button back=Ui.button(this,"돌아가기",false);page.addView(back);back.setOnClickListener(v->finish());
    }
    private void addButton(LinearLayout page,String title,Class<?> activity) {
        Button button=Ui.button(this,title,true);page.addView(button);Ui.gap(page,12);
        button.setOnClickListener(v->startActivity(new Intent(this,activity)));
    }
    @Override protected void onResume() {
        super.onResume();if(sensors==null)return;
        for(int type:new int[]{Sensor.TYPE_LIGHT,Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_MAGNETIC_FIELD}) {
            Sensor sensor=sensors.getDefaultSensor(type);
            if(sensor!=null)sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
            else if(type==Sensor.TYPE_LIGHT)light="센서 없음";
            else if(type==Sensor.TYPE_ACCELEROMETER)movement="센서 없음";
            else magnetic="센서 없음";
        }
        display();
    }
    @Override protected void onPause(){if(sensors!=null)sensors.unregisterListener(this);super.onPause();}
    @Override public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType()==Sensor.TYPE_LIGHT)light=String.format(Locale.KOREA,"%.1f lx",event.values[0]);
        else if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)movement=vector(event.values,"m/s²");
        else if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)magnetic=vector(event.values,"μT");
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastDisplay>250){lastDisplay=now;display();}
    }
    private String vector(float[] values,String unit){return String.format(Locale.KOREA,"%.1f / %.1f / %.1f %s",values[0],values[1],values[2],unit);}
    private void display(){readings.setText("조도  "+light+"\n가속도 XYZ  "+movement+"\n자기장 XYZ  "+magnetic);}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
