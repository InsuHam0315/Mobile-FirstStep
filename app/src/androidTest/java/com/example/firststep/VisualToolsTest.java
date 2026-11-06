package com.example.firststep;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class VisualToolsTest {
    @Test public void facilityAssetSearchFavoritesAndRecreation() {
        Context context=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("facility_favorites",Context.MODE_PRIVATE);
        Set<String> previous=new HashSet<>(prefs.getStringSet("ids",new HashSet<>()));
        prefs.edit().putStringSet("ids",new HashSet<>()).commit();
        try(ActivityScenario<FacilityDirectoryActivity> screen=ActivityScenario.launch(new Intent(context,FacilityDirectoryActivity.class))){
            onView(withText("8개 시설")).check(matches(isDisplayed()));
            onView(isAssignableFrom(EditText.class)).perform(replaceText("공부 도서관"));closeSoftKeyboard();
            onView(withText("2개 시설")).check(matches(isDisplayed()));
            onView(withContentDescription("중앙도서관 즐겨찾기 추가")).perform(scrollTo(),click());
            onView(withText("☆ 즐겨찾기만")).perform(scrollTo(),click());
            onView(withText("1개 시설")).check(matches(isDisplayed()));
            screen.recreate();
            onView(withText("1개 시설")).check(matches(isDisplayed()));
            onView(isAssignableFrom(EditText.class)).check(matches(withText("공부 도서관")));
            onView(withContentDescription("중앙도서관 즐겨찾기 해제")).perform(scrollTo(),click());
            onView(withText("아직 즐겨찾기가 없어요")).check(matches(isDisplayed()));
        } finally {prefs.edit().putStringSet("ids",previous).commit();}
    }
    @Test public void cameraCaptureCreatesDecodableJpegAndSurvivesRecreation() {
        Context context=ApplicationProvider.getApplicationContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.CAMERA);
        try(ActivityScenario<CameraCheckActivity> screen=ActivityScenario.launch(new Intent(context,CameraCheckActivity.class))){
            waitFor(allOf(withText("촬영하기"),isEnabled()));
            onView(withText("촬영하기")).perform(click());
            waitFor(withText(startsWith("촬영 완료 · JPEG")));
            File[] photos=context.getCacheDir().listFiles((dir,name)->name.startsWith("camera-check-")&&name.endsWith(".jpg"));
            assertNotNull(photos);assertTrue(photos.length>0);
            boolean valid=false;
            for(File file:photos){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);valid|=bounds.outWidth>0&&bounds.outHeight>0;}
            assertTrue("CameraX must produce a readable image",valid);
            screen.recreate();
            waitFor(withText("촬영한 사진 · 앱 임시 저장소에 보관 중"));
        }
    }
    private void waitFor(org.hamcrest.Matcher<android.view.View> matcher){
        long end=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<end){try{onView(matcher).check(matches(isDisplayed()));return;}catch(AssertionError|RuntimeException notReady){SystemClock.sleep(200);}}
        onView(matcher).check(matches(isDisplayed()));
    }
}
