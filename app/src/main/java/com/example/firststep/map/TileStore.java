package com.example.firststep.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Viewport-only OSM tiles. No prefetch or offline download. HTTP metadata survives app restarts. */
final class TileStore {
    interface Listener { void updated(boolean failed); }
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private volatile Set<String> visible=Collections.emptySet();
    private final Map<String,Long> failures=new ConcurrentHashMap<>();
    private final Map<String,Long> expiry=new ConcurrentHashMap<>();
    private final Set<String> singleView=ConcurrentHashMap.newKeySet();
    private final Set<HttpURLConnection> connections=ConcurrentHashMap.newKeySet();
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(16*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final Listener listener;
    private volatile boolean closed,active=true;
    TileStore(Context context,Listener listener) {
        directory=new File(context.getCacheDir(),"maptiles");directory.mkdirs();this.listener=listener;
    }
    void visible(Set<String> keys) {
        for(String key:singleView)if(!keys.contains(key)){memory.remove(key);singleView.remove(key);expiry.remove(key);}
        visible=new HashSet<>(keys);
    }
    void active(boolean value){active=value;if(!value)for(String key:singleView){memory.remove(key);expiry.remove(key);}if(!value)singleView.clear();}
    Bitmap tile(String key) {
        Bitmap bitmap=memory.get(key);
        if(bitmap!=null&&!singleView.contains(key)&&System.currentTimeMillis()>=expiry.getOrDefault(key,Long.MAX_VALUE)){memory.remove(key);bitmap=null;}
        if(bitmap==null && !closed && active && System.currentTimeMillis()-failures.getOrDefault(key,0L)>60_000 && pending.add(key)) {
            executor.execute(()->load(key));
        }
        return bitmap;
    }
    private void load(String key) {
        boolean failed=false,allowStale=true;
        try {
            if(closed || !active || !visible.contains(key)) return;
            File file=new File(directory,key+".png"),metadata=new File(directory,key+".properties");
            Properties props=new Properties();
            if(metadata.exists())try(InputStream stream=new FileInputStream(metadata)){props.load(stream);}
            allowStale=!Boolean.parseBoolean(props.getProperty("strict","false"));
            long now=System.currentTimeMillis(),expires=Long.parseLong(props.getProperty("expires","0"));
            if(!file.exists() || now>=expires) {
                String[] parts=key.split("_");
                HttpURLConnection connection=(HttpURLConnection)new URL("https://tile.openstreetmap.org/"+parts[0]+"/"+parts[1]+"/"+parts[2]+".png").openConnection();
                connections.add(connection);
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent","FirstStep/1.1 (https://github.com/InsuHam0315/Mobile-FirstStep)");
                if(file.exists()) {
                    if(props.getProperty("etag")!=null)connection.setRequestProperty("If-None-Match",props.getProperty("etag"));
                    if(props.getProperty("modified")!=null)connection.setRequestProperty("If-Modified-Since",props.getProperty("modified"));
                }
                try {
                    int code=connection.getResponseCode();
                    HttpFreshness freshness=new HttpFreshness(connection.getHeaderField("Cache-Control"),connection.getHeaderField("Expires"),connection.getHeaderField("Age"),now);
                    String control=connection.getHeaderField("Cache-Control");
                    if(control!=null){allowStale=!freshness.revalidate&&!freshness.noStore&&!control.toLowerCase(Locale.ROOT).contains("must-revalidate");props.setProperty("strict",Boolean.toString(!allowStale));}
                    if(code==200) {
                        File temp=File.createTempFile(key+"-",".tmp",directory);
                        try {
                        try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temp)) {
                            byte[] buffer=new byte[8192];int length,total=0;
                            while((length=in.read(buffer))!=-1){total+=length;if(total>2*1024*1024)throw new IOException("Tile too large");out.write(buffer,0,length);}
                        }
                        Bitmap check=BitmapFactory.decodeFile(temp.getPath());
                        if(check==null){temp.delete();throw new IOException("Invalid image");}check.recycle();
                        if(closed)return;
                        if(freshness.noStore){
                            Bitmap display=BitmapFactory.decodeFile(temp.getPath());memory.put(key,display);singleView.add(key);file.delete();metadata.delete();return;
                        }
                        if(!temp.renameTo(file)){temp.delete();throw new IOException("Tile cache write failed");}
                        } finally {temp.delete();}
                    } else if(code!=304 || !file.exists()) throw new IOException("Tile HTTP "+code);
                    if(closed)return;
                    props.setProperty("expires",Long.toString(freshness.expires));
                    if(freshness.revalidate||freshness.noStore||freshness.expires<=now)singleView.add(key);
                    for(String[] header:new String[][]{{"ETag","etag"},{"Last-Modified","modified"}}) {
                        String value=connection.getHeaderField(header[0]);if(value!=null)props.setProperty(header[1],value);
                    }
                    if(freshness.noStore){Bitmap display=BitmapFactory.decodeFile(file.getPath());if(display!=null)memory.put(key,display);file.delete();metadata.delete();return;}
                    File metaTemp=File.createTempFile(key+"-meta-",".tmp",directory);
                    try{try(OutputStream out=new FileOutputStream(metaTemp)){props.store(out,null);}if(!closed)metaTemp.renameTo(metadata);}finally{metaTemp.delete();}
                } finally {connections.remove(connection);connection.disconnect();}
            }
            Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());
            if(bitmap==null)throw new IOException("Missing tile");
            if(!closed){memory.put(key,bitmap);expiry.put(key,Long.parseLong(props.getProperty("expires","0")));}
        } catch(Exception error) {
            failed=true;failures.put(key,System.currentTimeMillis());
            // Already viewed tiles remain usable on a network failure; never prefetch for offline use.
            Bitmap stale=allowStale?BitmapFactory.decodeFile(new File(directory,key+".png").getPath()):null;
            if(stale!=null&&!closed){memory.put(key,stale);expiry.put(key,System.currentTimeMillis()+60_000);}
        }
        finally {pending.remove(key);final boolean problem=failed; if(!closed&&active)main.post(()->{if(!closed&&active)listener.updated(problem);});}
    }
    void retry(){failures.clear();}
    void close(){closed=true;for(HttpURLConnection c:connections)c.disconnect();connections.clear();executor.shutdownNow();memory.evictAll();main.removeCallbacksAndMessages(null);}
}
