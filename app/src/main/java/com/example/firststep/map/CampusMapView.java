package com.example.firststep.map;

import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.util.AttributeSet;
import android.view.*;
import com.example.firststep.data.Mission;
import java.util.*;

/**
 * Native tile rendering. Campus center is public map metadata, never a verified mission coordinate.
 */
public final class CampusMapView extends View {
  public interface Listener {
    void status(String message);

    void selected(Mission mission);

    default void panned() {}
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint attributionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect attributionBounds = new Rect();
  private final Rect viewport = new Rect(),
      currentMapRect = new Rect(),
      lastRenderedMapRect = new Rect();
  private final Paint.FontMetrics attributionMetrics = new Paint.FontMetrics();
  private static final String CREDIT = "© OpenStreetMap 기여자";
  private final ViewTreeObserver.OnScrollChangedListener attributionScroll = this::invalidate;
  private final TileStore store;
  private Listener listener;
  private List<Mission> missions = Collections.emptyList();
  private int zoom = 16;
  private double cx = Mercator.x(126.6802365, 16), cy = Mercator.y(35.9456011, 16);
  private Location current;
  private float previousX, previousY, downX, downY;
  private boolean moved, mapGesture, selectableGesture;
  private final float scale;

  public CampusMapView(Context context, AttributeSet attributes) {
    super(context, attributes);
    scale = getResources().getDisplayMetrics().density;
    store =
        new TileStore(
            context,
            failed -> {
              invalidate();
              if (listener != null)
                listener.status(
                    failed
                        ? "일부 지도를 불러오지 못했어요. 네트워크를 확인하고 다시 불러오기를 눌러 주세요."
                        : "지도 데이터: OpenStreetMap · 검증된 미션만 지점으로 표시합니다.");
            });
    setContentDescription("군산대학교 주변 지도. 드래그로 이동하고 확대·축소 버튼을 사용할 수 있습니다.");
  }

  public void configure(List<Mission> missions, Listener listener) {
    this.missions = missions;
    this.listener = listener;
    invalidate();
  }

  public void campus() {
    center(35.9456011, 126.6802365);
  }

  public void center(double lat, double lon) {
    cx = Mercator.x(lon, zoom);
    cy = Mercator.y(lat, zoom);
    invalidate();
  }

  public void current(Location location) {
    current = location;
    invalidate();
  }

  public void zoom(int delta) {
    int next = Math.max(3, Math.min(19, zoom + delta));
    double ratio = Math.pow(2, next - zoom);
    cx *= ratio;
    cy *= ratio;
    zoom = next;
    invalidate();
  }

  public void retry() {
    store.retry();
    invalidate();
  }

  public void active(boolean value) {
    store.active(value);
    if (value) invalidate();
  }

  public void close() {
    store.close();
  }

  /** Bounds of the label rendered in the last frame, useful for viewport/accessibility checks. */
  public Rect attributionBoundsForTest() {
    return new Rect(attributionBounds);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnScrollChangedListener(attributionScroll);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (getViewTreeObserver().isAlive())
      getViewTreeObserver().removeOnScrollChangedListener(attributionScroll);
    super.onDetachedFromWindow();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawColor(0xFFEDE9DF);
    attributionBounds.setEmpty();
    lastRenderedMapRect.setEmpty();
    if (!calculateMapRegion()) return;
    lastRenderedMapRect.set(currentMapRect);
    double left = cx - getWidth() / scale / 2, top = cy - getHeight() / scale / 2;
    int minX = (int) Math.floor(left / 256),
        maxX = (int) Math.floor((left + getWidth() / scale) / 256);
    int minY = (int) Math.floor(top / 256),
        maxY = (int) Math.floor((top + getHeight() / scale) / 256),
        n = 1 << zoom;
    Set<String> visible = new HashSet<>();
    for (int x = minX; x <= maxX; x++)
      for (int y = minY; y <= maxY; y++)
        if (y >= 0 && y < n) visible.add(zoom + "_" + Math.floorMod(x, n) + "_" + y);
    store.visible(visible);
    for (int x = minX; x <= maxX; x++)
      for (int y = minY; y <= maxY; y++) {
        if (y < 0 || y >= n) continue;
        Bitmap tile = store.tile(zoom + "_" + Math.floorMod(x, n) + "_" + y);
        RectF rect =
            new RectF(
                (float) ((x * 256 - left) * scale),
                (float) ((y * 256 - top) * scale),
                (float) (((x + 1) * 256 - left) * scale),
                (float) (((y + 1) * 256 - top) * scale));
        if (tile != null) canvas.drawBitmap(tile, null, rect, paint);
        else {
          paint.setColor(0xFFD8D1C4);
          paint.setStyle(Paint.Style.STROKE);
          canvas.drawRect(rect, paint);
          paint.setStyle(Paint.Style.FILL);
        }
      }
    for (Mission mission : missions)
      if (mission.coordinateVerified) {
        float x = (float) ((Mercator.x(mission.longitude, zoom) - left) * scale),
            y = (float) ((Mercator.y(mission.latitude, zoom) - top) * scale);
        paint.setColor(0xFFAB5336);
        canvas.drawCircle(x, y, 16 * scale, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * scale);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("" + mission.order, x, y + 5 * scale, paint);
      }
    if (current != null) {
      float x = (float) ((Mercator.x(current.getLongitude(), zoom) - left) * scale),
          y = (float) ((Mercator.y(current.getLatitude(), zoom) - top) * scale);
      double metersPerPixel =
          Math.cos(Math.toRadians(current.getLatitude())) * 40075016.686 / (256 * n);
      paint.setColor(0x333A78B8);
      canvas.drawCircle(
          x,
          y,
          (float) Math.min(getWidth(), current.getAccuracy() / metersPerPixel * scale),
          paint);
      paint.setColor(Color.WHITE);
      canvas.drawCircle(x, y, 8 * scale, paint);
      paint.setColor(0xFF2262A2);
      canvas.drawCircle(x, y, 5 * scale, paint);
    }
    attributionBounds.set(viewport.left, currentMapRect.bottom, viewport.right, viewport.bottom);
    attributionPaint.setColor(Color.WHITE);
    canvas.drawRect(attributionBounds, attributionPaint);
    attributionPaint.setColor(0xFF1E2B39);
    canvas.drawText(
        CREDIT,
        viewport.left + 8 * scale,
        viewport.bottom - 4 * scale - attributionMetrics.bottom,
        attributionPaint);
  }

  /** Recomputes the map region without altering the bounds from the last rendered frame. */
  private boolean calculateMapRegion() {
    currentMapRect.setEmpty();
    if (!getLocalVisibleRect(viewport)) return false;
    attributionPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    attributionPaint.setTextAlign(Paint.Align.LEFT);
    attributionPaint.getFontMetrics(attributionMetrics);
    int bandHeight =
        (int) Math.ceil(attributionMetrics.bottom - attributionMetrics.top + 8 * scale);
    if (viewport.height() < bandHeight
        || viewport.width() < attributionPaint.measureText(CREDIT) + 16 * scale) return false;
    currentMapRect.set(viewport.left, viewport.top, viewport.right, viewport.bottom - bandHeight);
    return true;
  }

  private static boolean contains(Rect rect, float x, float y) {
    return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
  }

  private boolean visibleMapPoint(float x, float y) {
    return contains(lastRenderedMapRect, x, y) && contains(currentMapRect, x, y);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX(), y = event.getY();
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        previousX = downX = x;
        previousY = downY = y;
        moved = false;
        mapGesture = calculateMapRegion() && visibleMapPoint(x, y);
        selectableGesture = mapGesture;
        if (mapGesture) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      case MotionEvent.ACTION_MOVE:
        if (!mapGesture) return true;
        if (!calculateMapRegion() || !visibleMapPoint(x, y) || !visibleMapPoint(downX, downY))
          selectableGesture = false;
        if (Math.hypot(x - downX, y - downY) > 8 * scale) {
          moved = true;
          if (listener != null) listener.panned();
        }
        cx = Mercator.wrapX(cx - (x - previousX) / scale, zoom);
        cy = Math.max(0, Math.min((1 << zoom) * 256.0, cy - (y - previousY) / scale));
        previousX = x;
        previousY = y;
        invalidate();
        return true;
      case MotionEvent.ACTION_UP:
        if (mapGesture) getParent().requestDisallowInterceptTouchEvent(false);
        boolean eligible =
            mapGesture
                && selectableGesture
                && !moved
                && calculateMapRegion()
                && visibleMapPoint(downX, downY)
                && visibleMapPoint(x, y);
        mapGesture = false;
        selectableGesture = false;
        if (eligible) {
          performClick();
          for (Mission m : missions)
            if (m.coordinateVerified) {
              float mx = (float) ((Mercator.x(m.longitude, zoom) - cx) * scale + getWidth() / 2f),
                  my = (float) ((Mercator.y(m.latitude, zoom) - cy) * scale + getHeight() / 2f);
              if (visibleMapPoint(mx, my)
                  && Math.hypot(mx - x, my - y) < 24 * scale
                  && listener != null) {
                listener.selected(m);
                break;
              }
            }
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        if (mapGesture) getParent().requestDisallowInterceptTouchEvent(false);
        mapGesture = false;
        selectableGesture = false;
        return true;
      default:
        return true;
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
