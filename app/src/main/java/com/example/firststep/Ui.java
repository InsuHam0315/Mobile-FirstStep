package com.example.firststep;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual language for the route and mission screens. */
final class Ui {
  static final int INK = Color.rgb(30, 43, 57),
      MUTED = Color.rgb(99, 105, 105),
      RUST = Color.rgb(171, 83, 54),
      PAPER = Color.rgb(250, 247, 239),
      WHITE = Color.WHITE,
      LINE = Color.rgb(230, 224, 211);

  static int dp(Context c, float n) {
    return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
  }

  static GradientDrawable bg(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static TextView text(Context c, String s, int size, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(c, 3), 1);
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return t;
  }

  static Button button(Context c, String s, boolean primary) {
    Button b = new Button(c);
    b.setText(s);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setTextColor(primary ? WHITE : INK);
    b.setBackground(bg(primary ? RUST : Color.rgb(239, 234, 222), dp(c, 14)));
    b.setMinHeight(dp(c, 52));
    return b;
  }

  static void gap(LinearLayout l, int h) {
    View v = new View(l.getContext());
    l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), h)));
  }

  static LinearLayout card(Context c) {
    LinearLayout l = column(c);
    l.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
    l.setBackground(bg(WHITE, dp(c, 18)));
    return l;
  }

  static TextView label(Context c, String s) {
    TextView t = text(c, s, 12, RUST, true);
    t.setLetterSpacing(.1f);
    return t;
  }

  static void loadPhoto(android.widget.ImageView view, String path) {
    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    android.graphics.BitmapFactory.decodeFile(path, options);
    int sample = 1;
    while (options.outWidth / sample > 1200 || options.outHeight / sample > 1200) sample *= 2;
    options.inJustDecodeBounds = false;
    options.inSampleSize = sample;
    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, options);
    if (bitmap == null) {
      view.setImageResource(android.R.drawable.ic_menu_report_image);
      view.setContentDescription("사진 파일을 찾거나 읽을 수 없습니다");
      return;
    }
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    try {
      int orientation =
          new android.media.ExifInterface(path)
              .getAttributeInt(
                  android.media.ExifInterface.TAG_ORIENTATION,
                  android.media.ExifInterface.ORIENTATION_NORMAL);
      switch (orientation) {
        case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
          matrix.postScale(-1, 1);
          break;
        case android.media.ExifInterface.ORIENTATION_ROTATE_180:
          matrix.postRotate(180);
          break;
        case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL:
          matrix.postScale(1, -1);
          break;
        case android.media.ExifInterface.ORIENTATION_TRANSPOSE:
          matrix.postRotate(90);
          matrix.postScale(-1, 1);
          break;
        case android.media.ExifInterface.ORIENTATION_ROTATE_90:
          matrix.postRotate(90);
          break;
        case android.media.ExifInterface.ORIENTATION_TRANSVERSE:
          matrix.postRotate(-90);
          matrix.postScale(-1, 1);
          break;
        case android.media.ExifInterface.ORIENTATION_ROTATE_270:
          matrix.postRotate(-90);
          break;
        default:
          break;
      }
    } catch (java.io.IOException ignored) {
    }
    if (!matrix.isIdentity()) {
      android.graphics.Bitmap corrected =
          android.graphics.Bitmap.createBitmap(
              bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      if (corrected != bitmap) bitmap.recycle();
      bitmap = corrected;
    }
    view.setImageBitmap(bitmap);
    view.setContentDescription("직접 촬영한 탐방 사진");
  }

  static void applyInsets(View view) {
    final int left = view.getPaddingLeft(),
        top = view.getPaddingTop(),
        right = view.getPaddingRight(),
        bottom = view.getPaddingBottom();
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        view,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.ime());
          v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
          return insets;
        });
    androidx.core.view.ViewCompat.requestApplyInsets(view);
  }
}
