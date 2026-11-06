package com.example.firststep.facility;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual shared preferences/public repositories, with an explicitly controlled read interleaving. */
@RunWith(AndroidJUnit4.class)
public class FacilityRepositoryAtomicityTest {
  @Test public void legacyMainAndWorkerRepositoryWritesPreserveEachOther() throws Exception {
    Context context=ApplicationProvider.getApplicationContext();
    FacilityRepository ordinary=new FacilityRepository(context);
    Set<String> original=ordinary.favorites();
    String a="library-central", b="library-hwangryong";
    try {
      for(boolean removeB:new boolean[]{false,true}) {
        ordinary.setFavorite(a,false); ordinary.setFavorite(b,removeB);
        Set<String> expected=new HashSet<>(ordinary.favorites());
        expected.add(a); if(removeB)expected.remove(b);else expected.add(b);
        interleave(context,expected,a,b,!removeB);
      }
    } finally {
      ordinary.setFavorite(a,original.contains(a));ordinary.setFavorite(b,original.contains(b));
    }
    assertEquals("Every original unrelated ID must survive",original,ordinary.favorites());
  }

  private static void interleave(Context context,Set<String> expected,String a,String b,
      boolean desiredB)throws Exception {
    CountDownLatch captured=new CountDownLatch(1),release=new CountDownLatch(1),
        secondRead=new CountDownLatch(1),enteredMain=new CountDownLatch(1),mainDone=new CountDownLatch(1);
    AtomicInteger reads=new AtomicInteger();AtomicReference<Throwable> error=new AtomicReference<>();
    AtomicReference<Thread> mainThread=new AtomicReference<>();
    SharedPreferences delegate=context.getSharedPreferences("facility_favorites",Context.MODE_PRIVATE);
    SharedPreferences observed=new ObservedPreferences(delegate){
      @Override public Set<String> getStringSet(String key,Set<String> fallback){
        Set<String> snapshot=new HashSet<>(super.getStringSet(key,fallback));
        if(reads.incrementAndGet()==1){
          captured.countDown();
          try {if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Release missing");}
          catch(InterruptedException e){throw new AssertionError(e);}
        } else secondRead.countDown();
        return snapshot;
      }
    };
    Context wrapped=new ContextWrapper(context){
      @Override public Context getApplicationContext(){return this;}
      @Override public SharedPreferences getSharedPreferences(String name,int mode){
        assertEquals("facility_favorites",name);return observed;
      }
    };
    FacilityRepository workerRepo=new FacilityRepository(wrapped),mainRepo=new FacilityRepository(wrapped);
    Thread worker=new Thread(()->{try{workerRepo.setFavorite(a,true);}catch(Throwable e){error.set(e);}},"h05-repository-worker");
    worker.start();
    try {
      assertTrue("Worker must capture its old set",captured.await(3,TimeUnit.SECONDS));
      new Handler(Looper.getMainLooper()).post(()->{
        mainThread.set(Thread.currentThread());enteredMain.countDown();
        try{mainRepo.setFavorite(b,desiredB);}catch(Throwable e){error.set(e);}finally{mainDone.countDown();}
      });
      assertTrue(enteredMain.await(3,TimeUnit.SECONDS));
      boolean blocked=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
      while(secondRead.getCount()!=0&&System.nanoTime()<deadline){
        Thread ui=mainThread.get();StackTraceElement[] stack=ui.getStackTrace();
        if(ui.getState()==Thread.State.BLOCKED&&stack.length>0
            &&stack[0].getClassName().equals(FacilityRepository.class.getName())){blocked=true;break;}
        Thread.yield();
      }
      assertTrue("Observe either old-code second read or actual repository-monitor blocking, never timeout-as-pass",
          secondRead.getCount()==0||blocked);
      if(secondRead.getCount()==0)assertTrue("Old-code main writer must commit before releasing worker",mainDone.await(2,TimeUnit.SECONDS));
    } finally {release.countDown();worker.join(4000);assertTrue(mainDone.await(4,TimeUnit.SECONDS));}
    assertFalse("Worker must finish",worker.isAlive());
    if(error.get()!=null)throw new AssertionError(error.get());
    assertEquals("Two repository callers must preserve both desired changes and unrelated IDs",
        expected,new FacilityRepository(context).favorites());
  }

  private static class ObservedPreferences implements SharedPreferences {
    final SharedPreferences delegate;
    ObservedPreferences(SharedPreferences delegate){this.delegate=delegate;}
    public Map<String,?> getAll(){return delegate.getAll();}
    public String getString(String key,String fallback){return delegate.getString(key,fallback);}
    public Set<String> getStringSet(String key,Set<String> fallback){return delegate.getStringSet(key,fallback);}
    public int getInt(String key,int fallback){return delegate.getInt(key,fallback);}
    public long getLong(String key,long fallback){return delegate.getLong(key,fallback);}
    public float getFloat(String key,float fallback){return delegate.getFloat(key,fallback);}
    public boolean getBoolean(String key,boolean fallback){return delegate.getBoolean(key,fallback);}
    public boolean contains(String key){return delegate.contains(key);}
    public Editor edit(){return delegate.edit();}
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){delegate.registerOnSharedPreferenceChangeListener(listener);}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){delegate.unregisterOnSharedPreferenceChangeListener(listener);}
  }
}
