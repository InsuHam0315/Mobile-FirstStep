package com.example.firststep.game.home;

import static org.junit.Assert.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.data.Mission;
import com.example.firststep.game.ui.home.GameHomeActivity;
import com.example.firststep.map.CampusMapView;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Isolated, explicitly synthetic markers near 0,0; no campus/catalog or progress changes. */
@RunWith(AndroidJUnit4.class)
public class MapTouchRegionsTest {
  @Test public void creditBandAndHiddenMarkerRadiusCannotSelect() throws Exception {
    try(Fixture f=new Fixture()) {
      Rect credit=f.credit(); f.marker(credit.centerX(),credit.centerY());
      f.capture("synthetic-hidden-marker-credit");
      f.tap(credit.centerX(),credit.centerY());
      assertEquals("Credit band must not select its covered marker",0,f.selected.get());
      f.tap(credit.centerX(),credit.top-2);
      assertEquals("A visible hit-radius edge must not select an occluded marker center",0,f.selected.get());
    }
  }

  @Test public void tinyNeutralViewportCannotSelect() throws Exception {
    try(Fixture f=new Fixture()) {
      f.clip(8); assertTrue(f.credit().isEmpty());
      float[] point=f.visibleCenter();f.marker(point[0],point[1]);
      f.capture("synthetic-neutral-viewport-no-selection");
      f.tap(point[0],point[1]);
      assertEquals("Neutral-only viewport must not select an invisible marker",0,f.selected.get());
    }
  }

  @Test public void gestureOriginAndChangedClipCannotSelect() throws Exception {
    try(Fixture f=new Fixture()) {
      Rect credit=f.credit();float x=credit.centerX(),y=credit.top-5;
      f.marker(x,y);f.down(x,credit.top+2);f.up(x,y);
      assertEquals("A gesture originating in credit cannot select map content",0,f.selected.get());
      float[] middle=f.visibleCenter();f.marker(middle[0],middle[1]);
      f.down(middle[0],middle[1]);f.clip(8,false);f.up(middle[0],middle[1]);
      assertEquals("Current clipping must invalidate a previously visible touch target",0,f.selected.get());
    }
  }

  @Test public void visibleMarkerAndMapPanStillWork() throws Exception {
    try(Fixture f=new Fixture()) {
      float[] middle=f.visibleCenter();f.marker(middle[0],middle[1]);
      f.capture("synthetic-visible-marker");f.tap(middle[0],middle[1]);
      assertEquals(1,f.selected.get());
      f.down(middle[0],middle[1]);f.move(middle[0]+f.dp(40),middle[1]);f.up(middle[0]+f.dp(40),middle[1]);
      assertTrue("Map-origin drag must still pan",f.panned.get()>0);
      assertEquals("Drag must not select a mission",1,f.selected.get());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final ActivityScenario<GameHomeActivity> screen;
    final AtomicInteger selected=new AtomicInteger(),panned=new AtomicInteger();
    CampusMapView map;FrameLayout clip;float density;long downTime;
    Fixture()throws Exception {
      Context context=ApplicationProvider.getApplicationContext();
      screen=ActivityScenario.launch(new Intent(context,GameHomeActivity.class)
          .putExtra("game_intro_debug_run",UUID.randomUUID().toString())
          .putExtra("game_intro_debug_clock",1_800_000_000_000L));
      int orientation=Integer.parseInt(InstrumentationRegistry.getArguments().getString("h05Orientation","1"));
      screen.onActivity(a->a.setRequestedOrientation(orientation==2?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      long deadline=SystemClock.elapsedRealtime()+10000;boolean[] ready={false};
      do {screen.onActivity(a->ready[0]=a.snapshot()!=null&&!a.snapshot().loading&&a.snapshot().progress!=null
          &&a.getResources().getConfiguration().orientation==orientation);
        if(ready[0])break;SystemClock.sleep(50);
      }while(SystemClock.elapsedRealtime()<deadline);
      assertTrue(ready[0]);
      screen.onActivity(a->{
        assertEquals(Float.parseFloat(InstrumentationRegistry.getArguments().getString("h05FontScale","1.0")),a.getResources().getConfiguration().fontScale,0.01f);
        density=a.getResources().getDisplayMetrics().density;
        map=a.findViewById(R.id.game_map_view);map.active(false);map.center(0,0);
        ((ViewGroup)map.getParent()).removeView(map);
        FrameLayout overlay=new FrameLayout(a);overlay.setBackgroundColor(Color.WHITE);
        overlay.setPadding(dp(16),dp(32),dp(16),dp(16));
        TextView label=new TextView(a);label.setText("합성 미션 마커 시험\n시험 좌표 (0,0 부근) · 실제 학교 좌표 아님");
        label.setTextColor(Color.BLACK);label.setTextSize(14);
        overlay.addView(label,new FrameLayout.LayoutParams(-1,dp(80)));
        clip=new FrameLayout(a);clip.setClipChildren(true);
        FrameLayout.LayoutParams frame=new FrameLayout.LayoutParams(-1,dp(300));frame.topMargin=dp(96);
        overlay.addView(clip,frame);clip.addView(map,new FrameLayout.LayoutParams(-1,dp(300)));
        a.addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
        map.configure(Collections.emptyList(),new CampusMapView.Listener(){
          public void status(String message){}
          public void selected(Mission mission){assertEquals("synthetic-touch-only",mission.id);selected.incrementAndGet();}
          public void panned(){panned.incrementAndGet();}
        });
      });settle();assertFalse(credit().isEmpty());
    }
    int dp(float value){return Math.round(value*density);}
    void settle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(150);}
    Rect credit(){Rect[] value={null};screen.onActivity(a->value[0]=map.attributionBoundsForTest());return value[0];}
    float[] visibleCenter(){float[] point=new float[2];screen.onActivity(a->{Rect visible=new Rect();assertTrue(map.getLocalVisibleRect(visible));
      Rect credit=map.attributionBoundsForTest();if(!credit.isEmpty())visible.bottom=credit.top;
      point[0]=visible.exactCenterX();point[1]=visible.exactCenterY();});return point;}
    void marker(float x,float y){screen.onActivity(a->{
      double size=256.0*(1<<16),wx=size/2+(x-map.getWidth()/2.0)/density,wy=size/2+(y-map.getHeight()/2.0)/density;
      double lon=wx/size*360-180,lat=Math.toDegrees(Math.atan(Math.sinh(Math.PI*(1-2*wy/size))));
      Mission m=new Mission("synthetic-touch-only","합성 시험 마커","실제 학교 위치 아님","fixture","","","",
          Collections.emptyList(),Collections.emptyList(),"",1,true,lat,lon);
      map.configure(Collections.singletonList(m),new CampusMapView.Listener(){
        public void status(String message){}
        public void selected(Mission mission){assertEquals(m.id,mission.id);selected.incrementAndGet();}
        public void panned(){panned.incrementAndGet();}
      });
    });settle();}
    void clip(int heightDp){clip(heightDp,true);}
    void clip(int heightDp,boolean redraw){screen.onActivity(a->{ViewGroup.LayoutParams p=clip.getLayoutParams();p.height=dp(heightDp);clip.setLayoutParams(p);});settle();
      if(redraw){screen.onActivity(a->map.invalidate());settle();}}
    void tap(float x,float y){down(x,y);up(x,y);}
    void down(float x,float y){downTime=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y);}
    void move(float x,float y){event(MotionEvent.ACTION_MOVE,x,y);}
    void up(float x,float y){event(MotionEvent.ACTION_UP,x,y);}
    void event(int action,float x,float y){int[] origin=new int[2];screen.onActivity(a->map.getLocationOnScreen(origin));
      MotionEvent event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,origin[0]+x,origin[1]+y,0);
      try{InstrumentationRegistry.getInstrumentation().sendPointerSync(event);}finally{event.recycle();}settle();}
    void capture(String name)throws Exception {
      String prefix=InstrumentationRegistry.getArguments().getString("h05EvidencePrefix","touch");assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
      Context context=ApplicationProvider.getApplicationContext();File folder=new File(context.getExternalFilesDir(null),"h05-evidence");assertTrue(folder.isDirectory()||folder.mkdirs());
      Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
      try(FileOutputStream out=new FileOutputStream(new File(folder,prefix+"-"+name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    public void close(){screen.close();}
  }
}
