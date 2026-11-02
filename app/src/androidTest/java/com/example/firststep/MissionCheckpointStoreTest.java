package com.example.firststep;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MissionCheckpointStoreTest {
  @Test
  public void clearingModeRemovesUncommittedSavingDraftAndKeepsOtherMode() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    File draft =
        new File(context.getFilesDir(), "mission_checkpoint_cleanup_" + System.nanoTime() + ".jpg");
    try (FileOutputStream output = new FileOutputStream(draft)) {
      output.write(1);
    }
    MissionCheckpointStore demo = new MissionCheckpointStore(context, true, "DRAFT_ONLY");
    MissionCheckpointStore.State saving = new MissionCheckpointStore.State();
    saving.stage = "SAVING";
    saving.photo = draft.getAbsolutePath();
    demo.save(saving);
    MissionCheckpointStore field = new MissionCheckpointStore(context, false, "DRAFT_ONLY");
    MissionCheckpointStore.State separate = new MissionCheckpointStore.State();
    separate.stage = "GAME";
    separate.typed = "현장 별도 기록";
    field.save(separate);
    try {
      MissionCheckpointStore.clearMode(context, true);
      assertEquals("DISCOVERY", demo.load().stage);
      assertEquals("현장 별도 기록", field.load().typed);
      long until = SystemClock.uptimeMillis() + 10000;
      while (draft.exists() && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
      assertFalse(
          "An interrupted save without a DB owner must not leave an orphan photo", draft.exists());
    } finally {
      field.clear();
      demo.clear();
      draft.delete();
    }
  }
}
