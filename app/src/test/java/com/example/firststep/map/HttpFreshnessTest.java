package com.example.firststep.map;
import org.junit.Test;
import static org.junit.Assert.*;
public class HttpFreshnessTest {
    @Test public void maxAgeAccountsForAlreadyCachedAge(){assertEquals(61000,new HttpFreshness("public, max-age=120",null,"60",1000).expires);}
    @Test public void nonPersistentAndRevalidationDirectives(){HttpFreshness f=new HttpFreshness("no-store, no-cache",null,null,1000);assertTrue(f.noStore);assertTrue(f.revalidate);assertEquals(1000,f.expires);}
    @Test public void expiryAndFallback(){assertEquals(0,new HttpFreshness(null,"Thu, 01 Jan 1970 00:00:00 GMT",null,1000).expires);assertEquals(604801000,new HttpFreshness(null,null,null,1000).expires);}
}
