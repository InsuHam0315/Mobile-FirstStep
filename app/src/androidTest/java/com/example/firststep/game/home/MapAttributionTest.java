package com.example.firststep.game.home;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.game.ui.home.GameHomeActivity;
import com.example.firststep.map.CampusMapView;
import java.io.*;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Matcher;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Actual native canvas attribution while the outer page clips the top/bottom of the map. */
@RunWith(AndroidJUnit4.class)
public class MapAttributionTest {
  @Test public void creditStaysInsideVisibleMapAcrossOuterScroll() throws Exception {
    Context context=ApplicationProvider.getApplicationContext();
    Intent intent=new Intent(context,GameHomeActivity.class)
        .putExtra("game_intro_debug_run",UUID.randomUUID().toString())
        .putExtra("game_intro_debug_clock",1_800_000_000_000L);
    try(ActivityScenario<GameHomeActivity> screen=ActivityScenario.launch(intent)) {
      int orientation=Integer.parseInt(InstrumentationRegistry.getArguments().getString("h05Orientation","1"));
      screen.onActivity(a->a.setRequestedOrientation(orientation==2
          ?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      long deadline=SystemClock.elapsedRealtime()+10000;boolean[] ready={false};
      do{
        screen.onActivity(a->ready[0]=a.snapshot()!=null&&!a.snapshot().loading&&a.snapshot().progress!=null
            &&a.getResources().getConfiguration().orientation==orientation);
        if(ready[0])break;SystemClock.sleep(50);
      }while(SystemClock.elapsedRealtime()<deadline);
      assertTrue(ready[0]);
      screen.onActivity(a->assertEquals(Float.parseFloat(InstrumentationRegistry.getArguments()
          .getString("h05FontScale","1.0")),a.getResources().getConfiguration().fontScale,0.01f));
      onView(withId(R.id.game_home_map)).perform(reveal(),click());
      for(int position=0;position<2;position++) {
        final int part=position;
        screen.onActivity(a->{
          CampusMapView map=a.findViewById(R.id.game_map_view);
          ScrollView scroll=a.findViewById(R.id.game_home_scroll);
          Rect bounds=new Rect();map.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(map,bounds);
          scroll.scrollTo(0,part==0?Math.max(0,bounds.top-scroll.getHeight()/3):bounds.top+map.getHeight()/3);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        screen.onActivity(a->{
          CampusMapView map=a.findViewById(R.id.game_map_view);Rect visible=new Rect();
          assertTrue(map.getLocalVisibleRect(visible));Rect credit=map.attributionBoundsForTest();
          assertFalse("Map must actually render a credit band",credit.isEmpty());
          assertTrue("Credit must remain inside visible map, not its offscreen bottom",visible.contains(credit));
        });
        String prefix=InstrumentationRegistry.getArguments().getString("h05EvidencePrefix","repair");
        assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
        File folder=new File(context.getExternalFilesDir(null),"h05-evidence");assertTrue(folder.isDirectory()||folder.mkdirs());
        Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);
        try(FileOutputStream out=new FileOutputStream(new File(folder,prefix+"-map-credit-scroll-"+position+".png"))){
          assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
        }finally{screenshot.recycle();}
      }
      onView(withId(R.id.game_map_zoom_in)).perform(reveal(),click());
      onView(withId(R.id.game_map_zoom_out)).perform(reveal(),click());
      androidx.test.espresso.Espresso.pressBack();
    }
  }

  private static ViewAction reveal(){return new ViewAction(){
    public Matcher<View> getConstraints(){return isAssignableFrom(View.class);}
    public String getDescription(){return "reveal control within inset scroll viewport";}
    public void perform(UiController ui,View view){
      ViewParent parent=view.getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();
      assertTrue(parent instanceof ScrollView);ScrollView scroll=(ScrollView)parent;
      Rect bounds=new Rect();view.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(view,bounds);
      int viewport=scroll.getHeight()-scroll.getPaddingTop()-scroll.getPaddingBottom();
      scroll.scrollTo(0,Math.max(0,bounds.centerY()-viewport/2-scroll.getPaddingTop()));
      ui.loopMainThreadForAtLeast(250);
      assertTrue("Control must actually be visible",isDisplayingAtLeast(90).matches(view));
    }
  };}
}
