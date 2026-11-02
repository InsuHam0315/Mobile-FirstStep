package com.example.firststep.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    @Test public void versionOneRecordsSurviveWithLegacyMethodAndModeIsolation() {
        Context context=ApplicationProvider.getApplicationContext();
        String name="migration-proof.db";context.deleteDatabase(name);
        SQLiteDatabase old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null);
        old.execSQL("CREATE TABLE IF NOT EXISTS mission_completions (mode TEXT NOT NULL,missionId TEXT NOT NULL,savedAt INTEGER NOT NULL,photoUri TEXT,discoveryMethod TEXT,PRIMARY KEY(mode,missionId))");
        old.execSQL("INSERT INTO mission_completions VALUES ('DEMO','M01',100,'photo.jpg','CARD')");
        old.execSQL("INSERT INTO mission_completions VALUES ('FIELD','M01',200,NULL,'AR')");
        old.setVersion(1);old.close();
        MissionRepository.ProgressDatabase database=Room.databaseBuilder(context,MissionRepository.ProgressDatabase.class,name).addMigrations(MissionRepository.MIGRATION_1_2).build();
        try {
            MissionRepository.CompletionRow demo=database.completionDao().byMode("DEMO").get(0);
            assertEquals("LEGACY",demo.completionMethod);assertEquals("photo.jpg",demo.photoUri);assertEquals(100,demo.savedAt);
            assertEquals("AR",database.completionDao().byMode("FIELD").get(0).discoveryMethod);
            assertEquals(-1,database.completionDao().completeIfAbsent(new MissionRepository.CompletionRow("DEMO","M01",999,null,"CARD","PHOTO")));
            assertEquals(100,database.completionDao().byMode("DEMO").get(0).savedAt);
        } finally {database.close();context.deleteDatabase(name);}
    }
}
