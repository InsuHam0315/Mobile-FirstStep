package com.example.firststep.game.home;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.firststep.R;
import com.example.firststep.facility.*;
import com.example.firststep.game.data.local.*;
import com.example.firststep.game.domain.*;
import com.example.firststep.game.presentation.home.HomeState;
import com.example.firststep.game.ui.home.GameHomeActivity;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual APK with labeled isolated real Room records/fake clock; legacy favorite IDs preserved. */
@RunWith(AndroidJUnit4.class)
public class HomeSmokeTest {
  private static final long START=1_800_000_000_000L;
  private static final String TARGET="digital_information_software", LIBRARY="library-central";
  private static Context context(){return ApplicationProvider.getApplicationContext();}
  private static Intent intent(String run){return new Intent(context(),GameHomeActivity.class)
      .putExtra("game_intro_debug_run",run).putExtra("game_intro_debug_clock",START);}

  @Test public void permissionResultWaitsForResumeAndIgnoresOtherScreen() throws Exception {
    try(ActivityScenario<GameHomeActivity> screen=launch(UUID.randomUUID().toString())) {
      ready(screen); press(R.id.game_home_map);
      await(screen,s->s.screen==HomeState.Screen.MAP);
      screen.moveToState(Lifecycle.State.CREATED);
      screen.onActivity(a->a.onRequestPermissionsResult(505,
          new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},
          new int[]{-1,-1})); // Explicit simulated lifecycle callback; actual grants remain unchanged.
      screen.moveToState(Lifecycle.State.RESUMED);
      await(screen,s->!s.locationMessage.isEmpty()&&!s.locationMessage.contains("표시를 지웠습니다"));
      assertFalse(state(screen).locationMessage.contains("요청할 때만"));
      home(screen); press(R.id.game_home_profile);
      screen.moveToState(Lifecycle.State.CREATED);
      screen.onActivity(a->a.onRequestPermissionsResult(505,new String[0],new int[0]));
      screen.moveToState(Lifecycle.State.RESUMED); ready(screen);
      assertEquals(HomeState.Screen.PROFILE,state(screen).screen);
      assertTrue("Delayed result must not start location outside the map",
          state(screen).locationMessage.isEmpty()||state(screen).locationMessage.contains("표시를 지웠습니다"));
    }
  }

  @Test public void corruptProgressShowsRetryWithoutErasingRecord() throws Exception {
    String run=UUID.randomUUID().toString();
    String payload="{\"schema\":\"invalid\"}";
    GameProgressDatabase db=androidx.room.Room.databaseBuilder(context(),GameProgressDatabase.class,
        "h04-debug-"+run+".db").build();
    try {db.progressDao().save(new GameProgressDatabase.Row("NORMAL",TARGET,payload));}
    finally {db.close();}
    try(ActivityScenario<GameHomeActivity> screen=launch(run)){
      await(screen,s->!s.loading&&s.error!=null);
      press(R.id.game_home_retry);
      await(screen,s->!s.loading&&s.error!=null);
      capture("corrupt-progress-error-retry-fixture");
    }
    db=androidx.room.Room.databaseBuilder(context(),GameProgressDatabase.class,
        "h04-debug-"+run+".db").build();
    try {assertEquals(payload,db.progressDao().find("NORMAL",TARGET).payload);}
    finally {db.close();}
  }

  @Test public void searchDetailFavoriteAndRecreationPreserveLegacyIdentity() throws Exception {
    FacilityRepository facilities=new FacilityRepository(context());
    boolean originally=facilities.favorites().contains(LIBRARY);
    String run=UUID.randomUUID().toString();
    try {
      try(ActivityScenario<GameHomeActivity> screen=launch(run)){
        ready(screen);
        assertEquals(8,state(screen).facilities.size());
        capture("home-synthetic-progress");
        press(R.id.game_home_search);
        onView(withId(R.id.game_home_search_query)).perform(reveal(),replaceText("공부"),closeSoftKeyboard());
        await(screen,s->!s.loading&&s.query.equals("공부"));
        assertFalse(state(screen).results.isEmpty());
        for(Facility f:state(screen).results)assertTrue(FacilitySearch.matches(f,"공부"));
        screen.recreate(); ready(screen);
        assertEquals("공부",state(screen).query);
        onView(withId(R.id.game_home_search_query)).perform(reveal(),replaceText("중앙도서관"),closeSoftKeyboard());
        await(screen,s->!s.loading&&s.query.equals("중앙도서관"));
        assertEquals(LIBRARY,state(screen).results.get(0).id);
        capture("facility-search");
        onView(allOf(withTagValue(is((Object)LIBRARY)),isDisplayed())).perform(reveal(),click());
        await(screen,s->s.screen==HomeState.Screen.DETAIL);
        assertEquals(LIBRARY,state(screen).selectedId);
        press(R.id.game_home_detail_favorite);
        await(screen,s->!s.loading&&s.favoriteIds.contains(LIBRARY)!=originally);
        capture("facility-detail-favorite-missing-coordinates");
        screen.recreate(); ready(screen);
        assertEquals(LIBRARY,state(screen).selectedId);
        assertEquals(!originally,state(screen).favoriteIds.contains(LIBRARY));
        press(R.id.game_home_detail_map);
        await(screen,s->s.screen==HomeState.Screen.MAP);
        assertEquals(LIBRARY,state(screen).selectedId);
        capture("selected-facility-map-without-destination-coordinate");
        androidx.test.espresso.Espresso.pressBack();
        await(screen,s->s.screen==HomeState.Screen.DETAIL);
        press(R.id.game_home_back);
        await(screen,s->s.screen==HomeState.Screen.SEARCH);
        press(R.id.game_home_search_favorites);
        await(screen,s->!s.loading&&s.favoritesOnly);
        assertEquals(originally?0:1,state(screen).results.size());
        for(Facility f:state(screen).results)assertTrue(state(screen).favoriteIds.contains(f.id));
        capture("facility-favorites-filter");
      }
      try(ActivityScenario<GameHomeActivity> reopened=launch(run)){
        ready(reopened);
        assertEquals("Favorite survives Activity restart through existing repository",!originally,
            state(reopened).favoriteIds.contains(LIBRARY));
      }
    } finally { facilities.setFavorite(LIBRARY,originally); }
  }

  @Test public void mapProfileEndingAndSimulatedLocationRemainHonest() throws Exception {
    try(ActivityScenario<GameHomeActivity> screen=launch(UUID.randomUUID().toString())){
      ready(screen);
      press(R.id.game_home_map);
      await(screen,s->s.screen==HomeState.Screen.MAP);
      SystemClock.sleep(3000); // Actual viewport tile fetch, not a guarantee of network success.
      onView(withId(R.id.game_map_view)).perform(reveal());
      capture("real-map-viewport-no-facility-pins");
      press(R.id.game_map_debug_location);
      await(screen,s->s.locationMessage.contains("시뮬레이션"));
      capture("explicit-simulated-current-location");
      screen.recreate(); ready(screen);
      assertFalse("Recreation must clear old current-location indication",
          state(screen).locationMessage.contains("시뮬레이션"));
      home(screen);
      press(R.id.game_home_profile);
      await(screen,s->s.screen==HomeState.Screen.PROFILE);
      onView(withId(R.id.game_profile_content)).check(matches(withText(containsString("학과: 알 수 없음"))));
      capture("local-profile-unknown-department");
      home(screen);
      press(R.id.game_home_ending);
      await(screen,s->s.screen==HomeState.Screen.ENDING);
      assertFalse(state(screen).endingAvailable);
      onView(withId(R.id.game_ending_content)).check(matches(withText(containsString("엔딩 잠김"))));
      capture("ending-locked-single-target");
    }
  }

  @Test public void homeRefreshReadsSameH04TargetAndSeparatesModes() throws Exception {
    String run=UUID.randomUUID().toString();
    try(ActivityScenario<GameHomeActivity> screen=launch(run)){
      ready(screen);
      assertEquals(TARGET,state(screen).progress.buildingId);
      assertNull(state(screen).progress.introDoneAt);
      screen.moveToState(Lifecycle.State.CREATED);
      GameContent catalog;
      try(InputStream in=context().getAssets().open("game_intro/catalog.json")){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
        while((n=in.read(b))!=-1)bytes.write(b,0,n);
        catalog=GameJson.catalog(bytes.toString("UTF-8"));
      }
      try(RoomProgressRepository repo=new RoomProgressRepository(androidx.room.Room.databaseBuilder(
          context(),GameProgressDatabase.class,"h04-debug-"+run+".db").build())){
        ProgressService service=new ProgressService(catalog,repo,()->START,new ProgressService.AttemptFactory(){
          public String nextId(){return UUID.randomUUID().toString();}
          public void shuffle(List<?> values){Collections.shuffle(values);}
        });
        service.intro(Progress.Mode.NORMAL,TARGET,false);
      }
      screen.moveToState(Lifecycle.State.RESUMED);
      await(screen,s->!s.loading&&s.progress.introDoneAt!=null);
      assertEquals(Long.valueOf(START),state(screen).progress.introDoneAt);
      assertFalse(state(screen).eligible);
      capture("home-waiting-fake-clock-shared-h04-record");
      press(R.id.game_home_search);
      await(screen,s->s.screen==HomeState.Screen.SEARCH);
      assertEquals(8,state(screen).results.size());
      home(screen);
      screen.onActivity(a->a.setDebugClockForTest(START+ProgressService.WAIT_MILLIS-1));
      await(screen,s->s.nowMillis==START+ProgressService.WAIT_MILLIS-1);
      assertFalse(state(screen).eligible);
      screen.onActivity(a->a.setDebugClockForTest(START+ProgressService.WAIT_MILLIS));
      await(screen,s->s.eligible);
      screen.recreate(); ready(screen);
      assertTrue(state(screen).eligible);
      capture("home-eligible-fake-clock-boundary");
      press(R.id.game_home_mode);
      await(screen,s->!s.loading&&s.mode==Progress.Mode.DEMO);
      assertNull(state(screen).progress.introDoneAt);
      screen.recreate(); ready(screen);
      assertEquals(Progress.Mode.DEMO,state(screen).mode);
      press(R.id.game_home_mode);
      await(screen,s->!s.loading&&s.mode==Progress.Mode.NORMAL);
      assertEquals(Long.valueOf(START),state(screen).progress.introDoneAt);
      android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
      android.app.Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(
          com.example.firststep.game.ui.intro.IntroQuizActivity.class.getName(),null,false);
      android.app.Activity opened=null;
      try {
        press(R.id.game_home_prototype);
        opened=instrumentation.waitForMonitorWithTimeout(monitor,5000);
        assertNotNull("Supported prototype route must open the actual H04 Activity",opened);
        android.app.Activity target=opened;
        instrumentation.runOnMainSync(()->{
          Intent route=target.getIntent();
          assertEquals(TARGET,route.getStringExtra("game_intro_building_id"));
          assertEquals("NORMAL",route.getStringExtra("game_intro_mode"));
          assertEquals(run,route.getStringExtra("game_intro_debug_run"));
          assertEquals(START+ProgressService.WAIT_MILLIS,route.getLongExtra("game_intro_debug_clock",-1));
        });
        capture("supported-h04-prototype-entry-fake-clock");
      } finally {
        if(opened!=null){android.app.Activity target=opened;instrumentation.runOnMainSync(target::finish);}
        instrumentation.removeMonitor(monitor);
      }
      ready(screen);
      assertEquals(Long.valueOf(START),state(screen).progress.introDoneAt);
    }
  }

  private static ActivityScenario<GameHomeActivity> launch(String run){
    ActivityScenario<GameHomeActivity> screen=ActivityScenario.launch(intent(run));
    int orientation=Integer.parseInt(InstrumentationRegistry.getArguments().getString("h05Orientation","1"));
    screen.onActivity(a->a.setRequestedOrientation(orientation==2
        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    long deadline=SystemClock.elapsedRealtime()+5000; int[] actual={0};
    do {screen.onActivity(a->actual[0]=a.getResources().getConfiguration().orientation);
      if(actual[0]==orientation)break;SystemClock.sleep(50);
    }while(SystemClock.elapsedRealtime()<deadline);
    assertEquals(orientation,actual[0]);
    screen.onActivity(a->assertEquals(Float.parseFloat(InstrumentationRegistry.getArguments()
        .getString("h05FontScale","1.0")),a.getResources().getConfiguration().fontScale,0.01f));
    return screen;
  }

  private static HomeState state(ActivityScenario<GameHomeActivity> screen){
    HomeState[] value={null};screen.onActivity(a->value[0]=a.getSnapshotForTest());return value[0];
  }
  private static void ready(ActivityScenario<GameHomeActivity> screen){await(screen,s->!s.loading&&s.error==null&&s.progress!=null);}
  private static void await(ActivityScenario<GameHomeActivity> screen,Predicate<HomeState> condition){
    long deadline=SystemClock.elapsedRealtime()+10000;
    do { HomeState s=state(screen);if(s!=null&&condition.test(s))return;SystemClock.sleep(50); }
    while(SystemClock.elapsedRealtime()<deadline);
    HomeState s=state(screen);assertNotNull(s);assertTrue("Actual screen="+s.screen+" error="+s.error,condition.test(s));
  }
  private static void home(ActivityScenario<GameHomeActivity> screen){
    for(int n=0;n<4&&state(screen).screen!=HomeState.Screen.HOME;n++)press(R.id.game_home_back);
    assertEquals(HomeState.Screen.HOME,state(screen).screen);
  }
  private static void press(int id){onView(withId(id)).perform(reveal(),click());}
  private static ViewAction reveal(){return new ViewAction(){
    public Matcher<View> getConstraints(){return isAssignableFrom(View.class);}
    public String getDescription(){return "reveal H05 control in scroll viewport";}
    public void perform(UiController ui,View view){
      ViewParent p=view.getParent();while(p!=null&&!(p instanceof ScrollView))p=p.getParent();
      if(p instanceof ScrollView){ScrollView scroll=(ScrollView)p;Rect r=new Rect();view.getDrawingRect(r);
        scroll.offsetDescendantRectToMyCoords(view,r);scroll.scrollTo(0,Math.max(0,r.centerY()-scroll.getHeight()/2));}
      ui.loopMainThreadForAtLeast(200);
    }
  };}
  private static void capture(String name)throws Exception{
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
    String prefix=InstrumentationRegistry.getArguments().getString("h05EvidencePrefix","default");
    assertTrue(prefix.matches("[A-Za-z0-9_-]+"));
    File dir=new File(context().getExternalFilesDir(null),"h05-evidence");assertTrue(dir.isDirectory()||dir.mkdirs());
    Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
    try(FileOutputStream out=new FileOutputStream(new File(dir,prefix+"-"+name+".png"))){
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
    }finally{bitmap.recycle();}
  }
}
