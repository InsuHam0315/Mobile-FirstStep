package com.example.firststep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A visibly labelled illustration for DEMO only; never a camera or field observation. */
final class DemoPhoto {
  private DemoPhoto() {}

  static File create(Context context) throws IOException {
    Bitmap image = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888);
    File file = new File(context.getFilesDir(), "demo_photo_" + java.util.UUID.randomUUID() + ".jpg");
    try {
      Canvas canvas = new Canvas(image);
      canvas.drawColor(Color.rgb(253, 242, 244));
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setColor(Color.rgb(230, 196, 207));
      canvas.drawRoundRect(170, 260, 790, 490, 18, 18, paint);
      paint.setColor(Color.rgb(47, 57, 80));
      Path roof = new Path();
      roof.moveTo(125, 260); roof.lineTo(480, 130); roof.lineTo(835, 260); roof.close();
      canvas.drawPath(roof, paint);
      for (int x = 230; x < 790; x += 120) canvas.drawRect(x, 302, x + 40, 446, paint);
      paint.setColor(Color.rgb(255, 255, 255));
      canvas.drawRoundRect(272, 180, 688, 229, 10, 10, paint);
      paint.setColor(Color.rgb(47, 57, 80));
      paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(28);
      canvas.drawText("CAMPUS · TEST IMAGE", 480, 215, paint);
      paint.setColor(Color.rgb(179, 70, 110));
      paint.setTextSize(29);
      canvas.drawText("테스트용 이미지 · 실제 촬영 아님", 480, 555, paint);
      paint.setTypeface(Typeface.DEFAULT);
      paint.setTextSize(22);
      canvas.drawText("사진 확인 → 질문 → 저장 흐름을 확인해요", 480, 595, paint);
      try (FileOutputStream stream = new FileOutputStream(file)) {
        if (!image.compress(Bitmap.CompressFormat.JPEG, 90, stream))
          throw new IOException("Cannot encode demo image");
      }
      return file;
    } catch (IOException | RuntimeException e) {
      file.delete();
      throw e;
    } finally {
      image.recycle();
    }
  }
}
