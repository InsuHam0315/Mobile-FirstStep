package com.example.firststep.game.device;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import com.example.firststep.game.presentation.discovery.DiscoveryState.CameraStatus;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Consumer;

/** Owns only its Preview binding. All callbacks are main-thread and invalidated on close. */
public final class DiscoveryCamera {
  private final Context context;
  private final LifecycleOwner owner;
  private final PreviewView view;
  private final Consumer<CameraStatus> callback;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean closed;
  private ProcessCameraProvider provider;
  private Preview preview;
  private Camera camera;
  private boolean sawIdle, streaming;
  private Observer<PreviewView.StreamState> streamObserver;
  private Observer<androidx.camera.core.CameraState> errorObserver;
  private final Runnable timeout;

  public DiscoveryCamera(
      Context context, LifecycleOwner owner, PreviewView view, Consumer<CameraStatus> callback) {
    this.context = context;
    this.owner = owner;
    this.view = view;
    this.callback = callback;
    this.timeout =
        () -> {
          if (!closed) this.callback.accept(CameraStatus.ERROR);
        };
  }

  public void start() {
    callback.accept(CameraStatus.STARTING);
    handler.postDelayed(timeout, 8000);
    ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(context);
    f.addListener(
        () -> {
          if (closed) return;
          try {
            ProcessCameraProvider p = f.get();
            if (closed) return;
            if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
              handler.removeCallbacks(timeout);
              callback.accept(CameraStatus.UNAVAILABLE);
              return;
            }
            provider = p;
            streamObserver =
                state -> {
                  if (closed) return;
                  if (state == PreviewView.StreamState.IDLE) {
                    sawIdle = true;
                    if (streaming) {
                      streaming = false;
                      callback.accept(CameraStatus.STARTING);
                      handler.removeCallbacks(timeout);
                      handler.postDelayed(timeout, 8000);
                    }
                  }
                  if (sawIdle && state == PreviewView.StreamState.STREAMING) {
                    streaming = true;
                    handler.removeCallbacks(timeout);
                    callback.accept(CameraStatus.READY);
                  }
                };
            view.getPreviewStreamState().observe(owner, streamObserver);
            preview = new Preview.Builder().build();
            preview.setSurfaceProvider(view.getSurfaceProvider());
            if (closed) return;
            camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            errorObserver =
                s -> {
                  if (!closed && s.getError() != null) {
                    handler.removeCallbacks(timeout);
                    callback.accept(CameraStatus.ERROR);
                  }
                };
            camera.getCameraInfo().getCameraState().observe(owner, errorObserver);
          } catch (Exception e) {
            if (!closed) {
              handler.removeCallbacks(timeout);
              callback.accept(CameraStatus.ERROR);
            }
          }
        },
        ContextCompat.getMainExecutor(context));
  }

  public void close() {
    closed = true;
    handler.removeCallbacks(timeout);
    if (streamObserver != null) view.getPreviewStreamState().removeObserver(streamObserver);
    if (camera != null && errorObserver != null)
      camera.getCameraInfo().getCameraState().removeObserver(errorObserver);
    if (provider != null && preview != null) provider.unbind(preview);
  }
}
