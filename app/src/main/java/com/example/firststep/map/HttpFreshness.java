package com.example.firststep.map;

import java.text.SimpleDateFormat;
import java.util.*;

final class HttpFreshness {
    final long expires;
    final boolean noStore,revalidate;
    HttpFreshness(String control,String expiresHeader,String ageHeader,long now) {
        boolean store=true,validate=false;Long seconds=null;
        if(control!=null)for(String token:control.toLowerCase(Locale.ROOT).split(",")){
            String p=token.trim();if(p.equals("no-store"))store=false;if(p.equals("no-cache"))validate=true;
            if(p.startsWith("max-age="))try{seconds=Math.max(0,Long.parseLong(p.substring(8).replace("\"","")));}catch(NumberFormatException ignored){}
        }
        long age=0;try{age=Math.max(0,Long.parseLong(ageHeader));}catch(Exception ignored){}
        long end=now+7L*24*60*60*1000;
        if(seconds!=null)end=now+Math.min(365L*24*60*60,Math.max(0,seconds-age))*1000;
        else if(expiresHeader!=null)try{
            SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);f.setTimeZone(TimeZone.getTimeZone("GMT"));end=f.parse(expiresHeader).getTime();
        }catch(Exception ignored){}
        noStore=!store;revalidate=validate;expires=validate?now:end;
    }
}
