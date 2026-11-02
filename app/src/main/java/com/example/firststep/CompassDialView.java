package com.example.firststep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Direction practice dial: cardinals rotate with the phone; rust arrow points toward the target.
 */
final class CompassDialView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float heading, target;

  CompassDialView(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  void setHeading(float heading, float target) {
    this.heading = heading;
    this.target = target;
    setContentDescription(
        String.format(java.util.Locale.KOREA, "현재 방향 %.0f도, 목표 %.0f도", heading, target));
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) * .40f;
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(Ui.LINE);
    paint.setStrokeWidth(Ui.dp(getContext(), 2));
    canvas.drawCircle(cx, cy, r, paint);
    canvas.save();
    canvas.rotate(-heading, cx, cy);
    for (int degree = 0; degree < 360; degree += 30) {
      double radians = Math.toRadians(degree);
      float inner = r - (degree % 90 == 0 ? 18 : 9);
      canvas.drawLine(
          cx + (float) Math.sin(radians) * inner,
          cy - (float) Math.cos(radians) * inner,
          cx + (float) Math.sin(radians) * r,
          cy - (float) Math.cos(radians) * r,
          paint);
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setTextSize(Ui.dp(getContext(), 16));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setColor(Ui.INK);
    canvas.drawText("N", cx, cy - r + Ui.dp(getContext(), 30), paint);
    canvas.drawText("S", cx, cy + r - Ui.dp(getContext(), 18), paint);
    canvas.drawText("E", cx + r - Ui.dp(getContext(), 24), cy + Ui.dp(getContext(), 6), paint);
    canvas.drawText("W", cx - r + Ui.dp(getContext(), 24), cy + Ui.dp(getContext(), 6), paint);
    canvas.restore();
    canvas.save();
    canvas.rotate(target - heading, cx, cy);
    paint.setColor(Ui.RUST);
    paint.setStrokeWidth(Ui.dp(getContext(), 5));
    canvas.drawLine(cx, cy + Ui.dp(getContext(), 24), cx, cy - r * .55f, paint);
    Path arrow = new Path();
    arrow.moveTo(cx, cy - r * .76f);
    arrow.lineTo(cx - Ui.dp(getContext(), 12), cy - r * .48f);
    arrow.lineTo(cx + Ui.dp(getContext(), 12), cy - r * .48f);
    arrow.close();
    canvas.drawPath(arrow, paint);
    canvas.restore();
    paint.setColor(Ui.INK);
    canvas.drawCircle(cx, cy, Ui.dp(getContext(), 6), paint);
  }
}
