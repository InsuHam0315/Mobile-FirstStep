package com.example.firststep;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

/** Actual CameraX capture, deliberately no synthetic image fallback in this verification screen. */
public final class CameraCheckActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ImageView result;
    private TextView status;
    private Button capture;
    private ProcessCameraProvider provider;
    private Preview preview;
    private ImageCapture imageCapture;
    private boolean starting, taking;
    private int captureGeneration;
    private File photo;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);if(!BuildConfig.DEBUG){finish();return;}
        LinearLayout page=Ui.column(this);page.setBackgroundColor(Ui.PAPER);
        int pad=Ui.dp(this,16);page.setPadding(pad,pad,pad,pad);setContentView(page);Ui.applyInsets(page);
        page.addView(Ui.text(this,"카메라 촬영 확인",25,Ui.INK,true));Ui.gap(page,8);
        page.addView(Ui.text(this,"연결된 카메라 입력을 촬영합니다. 가상 장면인지 실제 피사체인지 확인해 주세요. 탐방 기록에는 저장하지 않아요.",13,Ui.MUTED,false));Ui.gap(page,12);
        android.widget.FrameLayout area=new android.widget.FrameLayout(this);
        page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        previewView=new PreviewView(this);previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        area.addView(previewView,new android.widget.FrameLayout.LayoutParams(-1,-1));
        result=new ImageView(this);result.setScaleType(ImageView.ScaleType.FIT_CENTER);result.setBackgroundColor(Ui.PAPER);
        result.setVisibility(android.view.View.GONE);area.addView(result,new android.widget.FrameLayout.LayoutParams(-1,-1));
        Ui.gap(page,12);status=Ui.text(this,"카메라를 준비하고 있어요.",14,Ui.MUTED,false);page.addView(status);Ui.gap(page,12);
        capture=Ui.button(this,"촬영하기",true);capture.setEnabled(false);page.addView(capture);Ui.gap(page,8);
        Button retry=Ui.button(this,"카메라 다시 열기",false);page.addView(retry);Ui.gap(page,8);
        Button back=Ui.button(this,"돌아가기",false);page.addView(back);back.setOnClickListener(v->finish());
        capture.setOnClickListener(v->capture());retry.setOnClickListener(v->{if(!taking){result.setVisibility(android.view.View.GONE);openCamera();}});
        if(state!=null){String path=state.getString("photo");if(path!=null){File saved=new File(path);if(saved.getParentFile().equals(getCacheDir())&&saved.getName().startsWith("camera-check-")&&saved.isFile())photo=saved;}}
        openCamera();
        if(photo!=null){Ui.loadPhoto(result,photo.getAbsolutePath());result.setVisibility(android.view.View.VISIBLE);status.setText("촬영한 사진 · 앱 임시 저장소에 보관 중");}
    }
    private void openCamera() {
        if(starting||taking||isDestroyed())return;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            status.setText("촬영하려면 카메라 권한이 필요해요.");requestPermissions(new String[]{Manifest.permission.CAMERA},840);return;
        }
        starting=true;capture.setEnabled(false);
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            starting=false;if(isFinishing()||isDestroyed())return;
            try {
                provider=future.get();
                if(preview!=null)provider.unbind(preview);if(imageCapture!=null)provider.unbind(imageCapture);
                CameraSelector camera=provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;
                if(!provider.hasCamera(camera)){status.setText("사용 가능한 카메라가 없어요. 에뮬레이터의 Back camera를 VirtualScene으로 설정해 주세요.");return;}
                preview=new Preview.Builder().build();imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.bindToLifecycle(this,camera,preview,imageCapture);capture.setEnabled(true);
                if(result.getVisibility()!=android.view.View.VISIBLE)status.setText("카메라 연결됨 · 아래 촬영하기를 눌러 사진 파일을 확인하세요.");
            } catch(Exception failure){imageCapture=null;status.setText("카메라 연결 실패 · 권한과 카메라 장치를 확인하고 다시 열어 주세요.");}
        },ContextCompat.getMainExecutor(this));
    }
    private void capture() {
        if(imageCapture==null||taking)return;
        taking=true;final int generation=captureGeneration;capture.setEnabled(false);status.setText("사진을 촬영하고 있어요.");
        File next=new File(getCacheDir(),"camera-check-"+java.util.UUID.randomUUID()+".jpg");
        imageCapture.setTargetRotation(previewView.getDisplay().getRotation());
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(next).build(),ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){
            @Override public void onImageSaved(ImageCapture.OutputFileResults output){
                taking=false;if(isDestroyed()||isFinishing()){next.delete();return;}
                if(generation!=captureGeneration){next.delete();capture.setEnabled(true);status.setText("화면 전환으로 촬영을 중단했어요. 다시 촬영해 주세요.");return;}
                if(photo!=null)photo.delete();photo=next;capture.setEnabled(true);Ui.loadPhoto(result,next.getAbsolutePath());result.setVisibility(android.view.View.VISIBLE);
                status.setText("촬영 완료 · JPEG "+Math.max(1,next.length()/1024)+" KB\n카메라가 만든 사진 파일을 다시 읽어 표시했어요.");
            }
            @Override public void onError(ImageCaptureException error){taking=false;next.delete();if(!isDestroyed()){capture.setEnabled(true);status.setText("사진 촬영 실패 · 카메라 다시 열기로 연결을 확인해 주세요.");}}
        });
    }
    @Override protected void onSaveInstanceState(Bundle state){captureGeneration++;if(photo!=null)state.putString("photo",photo.getAbsolutePath());super.onSaveInstanceState(state);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==840){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();else status.setText("카메라 권한이 거부됐어요. 앱 설정에서 허용한 뒤 다시 열 수 있어요.");}}
    @Override protected void onDestroy(){if(provider!=null){if(preview!=null)provider.unbind(preview);if(imageCapture!=null)provider.unbind(imageCapture);}if(isFinishing()&&photo!=null)photo.delete();super.onDestroy();}
}
