package com.example.firststep.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.firststep.auth.AuthSession;
import com.example.firststep.auth.CloudApi;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real MySQL/API round-trip, including account boundaries and JPEG restoration. Never resets guest. */
@RunWith(AndroidJUnit4.class)
public class AccountSyncIntegrationTest {
  private interface Operation { void start(MissionRepository.CompletionCallback callback); }

  @Test public void accountPhotosRestoreFromServerAndNeverCrossAccountOrGuestBoundaries() throws Exception {
    Context context=ApplicationProvider.getApplicationContext();
    AuthSession session=new AuthSession(context);
    Assume.assumeTrue("Run account integration checks while signed out", "guest".equals(session.currentUserId()));
    CloudApi publicApi=new CloudApi(context,"");
    assertTrue("Development login must be enabled on the actual test server",
        publicApi.request("GET","/v1/config",null).optBoolean("development_login_enabled"));
    MissionRepository guest=MissionRepository.getInstance(context);
    Map<String,String> guestBaseline=baseline(guest);
    String tokenA="",tokenB="";
    MissionRepository accountA=null;
    File original=null;
    try {
      JSONObject first=register(publicApi,"a");
      session.save(first);tokenA=session.getAccessToken();
      accountA=MissionRepository.getInstance(context);
      final MissionRepository targetA=accountA;
      assertTrue(accountA.isAccountRepository());assertNotSame(guest,accountA);
      assertTrue(records(accountA,AppMode.DEMO).isEmpty());
      original=new File(context.getFilesDir(),"account_sync_test_"+UUID.randomUUID()+".jpg");
      Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
      image.eraseColor(android.graphics.Color.rgb(220,180,190));
      try(FileOutputStream output=new FileOutputStream(original)){
        assertTrue("Test JPEG encoding failed",image.compress(Bitmap.CompressFormat.JPEG,90,output));
      }finally{image.recycle();}
      final String localPath=original.getAbsolutePath();
      assertTrue("Account completion must save locally",await(callback->targetA.completeMission(
          AppMode.DEMO,"M01",localPath,"CARD","DEMO_PHOTO",callback)));
      assertTrue("Account completion must sync to the real server",await(targetA::syncAccount));
      CloudApi apiA=new CloudApi(context,tokenA);
      JSONObject remote=apiA.request("GET","/v1/progress/DEMO",null);
      JSONArray rows=remote.getJSONArray("completions");
      assertEquals(1,rows.length());
      JSONObject saved=rows.getJSONObject(0);
      assertEquals("M01",saved.getString("mission_id"));
      assertEquals("DEMO_PHOTO",saved.getString("completion_method"));
      byte[] jpeg=Base64.decode(saved.getString("photo_base64"),Base64.NO_WRAP);
      assertJpeg(jpeg);
      long savedAt=saved.getLong("saved_at");

      // Fence against auto-sync and remove only this account's local cache, not server progress.
      targetA.syncExecutor.submit(()->targetA.dao.deleteMode("DEMO")).get(30,TimeUnit.SECONDS);
      assertTrue(records(targetA,AppMode.DEMO).isEmpty());
      assertTrue("Server snapshot must restore a missing local cache",await(targetA::syncAccount));
      List<MissionCompletion> restored=records(targetA,AppMode.DEMO);
      assertEquals(1,restored.size());
      assertEquals(savedAt,restored.get(0).savedAt);
      assertEquals("DEMO_PHOTO",restored.get(0).completionMethod);
      assertTrue("Restored photo must use a downloaded file",!localPath.equals(restored.get(0).photoUri));
      File recovered=new File(restored.get(0).photoUri);assertTrue(recovered.isFile());
      try(java.io.FileInputStream input=new java.io.FileInputStream(recovered);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
        byte[] buffer=new byte[4096];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);assertJpeg(output.toByteArray());
      }

      JSONObject second=register(publicApi,"b");
      session.save(second);tokenB=session.getAccessToken();
      MissionRepository accountB=MissionRepository.getInstance(context);
      assertNotSame(targetA,accountB);assertNotEquals(targetA.namespace,accountB.namespace);
      assertTrue(records(accountB,AppMode.DEMO).isEmpty());assertTrue(records(accountB,AppMode.FIELD).isEmpty());
      assertFalse("An old repository cannot use the new account's token",await(targetA::syncAccount));
      assertEquals(0,new CloudApi(context,tokenB).request("GET","/v1/progress/DEMO",null).getJSONArray("completions").length());
      assertEquals(1,apiA.request("GET","/v1/progress/DEMO",null).getJSONArray("completions").length());
      session.clear();
      assertSame(guest,MissionRepository.getInstance(context));
      assertEquals("Guest records must stay unchanged",guestBaseline,baseline(guest));
    } finally {
      session.clear();
      revoke(context,tokenA);revoke(context,tokenB);
      if(original!=null&&accountA!=null){
        final MissionRepository cleanupRepository=accountA;
        final String cleanupPath=original.getAbsolutePath();
        boolean referenced=cleanupRepository.syncExecutor.submit(()->cleanupRepository.dao.allPhotoPaths().contains(cleanupPath)).get(30,TimeUnit.SECONDS);
        if(!referenced)original.delete();
      }
    }
  }

  private JSONObject register(CloudApi api,String suffix)throws Exception{
    return api.request("POST","/v1/auth/development/register",new JSONObject()
        .put("username","sync_"+suffix+"_"+UUID.randomUUID().toString().replace("-",""))
        .put("password",UUID.randomUUID().toString()).put("nickname","계정 분리 검증"));
  }
  private boolean await(Operation operation)throws Exception{
    CountDownLatch latch=new CountDownLatch(1);AtomicBoolean result=new AtomicBoolean();
    operation.start(ok->{result.set(ok);latch.countDown();});
    assertTrue("Account operation timed out",latch.await(60,TimeUnit.SECONDS));return result.get();
  }
  private List<MissionCompletion> records(MissionRepository repo,AppMode mode)throws Exception{
    CountDownLatch latch=new CountDownLatch(1);AtomicReference<List<MissionCompletion>> result=new AtomicReference<>();
    repo.getCompletions(mode,list->{result.set(list);latch.countDown();});
    assertTrue("Record read timed out",latch.await(30,TimeUnit.SECONDS));return result.get();
  }
  private Map<String,String> baseline(MissionRepository repo)throws Exception{
    Map<String,String> result=new TreeMap<>();
    for(AppMode mode:AppMode.values())for(MissionCompletion record:records(repo,mode))
      result.put(mode+":"+record.missionId,record.savedAt+":"+record.photoUri+":"+record.discoveryMethod+":"+record.completionMethod);
    return result;
  }
  private void assertJpeg(byte[] bytes){
    assertTrue("JPEG content must be present",bytes.length>2&&(bytes[0]&255)==255&&(bytes[1]&255)==216);
    Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
    assertNotNull("JPEG must decode completely",image);
    try{assertEquals(8,image.getWidth());assertEquals(8,image.getHeight());}finally{image.recycle();}
  }
  private void revoke(Context context,String token){
    if(token.isEmpty())return;
    try{new CloudApi(context,token).request("POST","/v1/auth/logout",new JSONObject());}catch(CloudApi.ApiException ignored){}
  }
}
