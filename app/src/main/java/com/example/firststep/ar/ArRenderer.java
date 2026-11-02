package com.example.firststep.ar;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.google.ar.core.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Camera OES texture + a world-anchored diamond. No screen overlay is presented as tracked AR. */
public final class ArRenderer implements GLSurfaceView.Renderer {
    public interface Listener { void onMessage(String message); void onDiscovered(); }
    private final Listener listener;
    private volatile Session session;
    private volatile float[] pendingTap;
    private volatile int rotation;
    private int width = 1, height = 1, texture, backgroundProgram, objectProgram;
    private Anchor anchor;
    private String lastMessage = "";
    private final FloatBuffer quad = buffer(-1,-1, 1,-1, -1,1, 1,1);
    private final FloatBuffer uv = buffer(0,0, 1,0, 0,1, 1,1);
    private final FloatBuffer diamond = buffer(0,.20f,0, -.09f,.10f,0, .09f,.10f,0,
            -.09f,.10f,0, 0,0,0, .09f,.10f,0,
            0,.20f,0, 0,.10f,-.09f, 0,.10f,.09f,
            0,.10f,-.09f, 0,0,0, 0,.10f,.09f);
    private final float[] projection = new float[16], view = new float[16], model = new float[16], mv = new float[16], mvp = new float[16];
    public ArRenderer(Listener listener) { this.listener = listener; }
    public void setSession(Session session) { this.session = session; }
    public void setRotation(int rotation) { this.rotation = rotation; }
    public void tap(float x, float y) { pendingTap = new float[]{x,y}; }
    private void message(String value) { if (!lastMessage.equals(value)) { lastMessage = value; listener.onMessage(value); } }
    private static FloatBuffer buffer(float... values) {
        FloatBuffer result = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values).position(0); return result;
    }
    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        int[] textures = new int[1]; GLES20.glGenTextures(1, textures, 0); texture = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        backgroundProgram = program("attribute vec2 p; attribute vec2 t; varying vec2 uv; void main(){gl_Position=vec4(p,0.,1.);uv=t;}",
                "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 uv; void main(){gl_FragColor=texture2D(camera,uv);}");
        objectProgram = program("attribute vec3 p; uniform mat4 mvp; void main(){gl_Position=mvp*vec4(p,1.);}",
                "precision mediump float; void main(){gl_FragColor=vec4(1.,.64,.18,1.);}");
    }
    @Override public void onSurfaceChanged(GL10 unused, int width, int height) { this.width=width;this.height=height; GLES20.glViewport(0,0,width,height); }
    @Override public void onDrawFrame(GL10 unused) {
        GLES20.glClearColor(.12f,.17f,.25f,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session current = session; if (current == null) return;
        try {
            current.setDisplayGeometry(rotation,width,height); current.setCameraTextureName(texture);
            Frame frame = current.update(); Camera camera = frame.getCamera();
            if (frame.getTimestamp() != 0) drawBackground(frame);
            if (camera.getTrackingState() != TrackingState.TRACKING) { pendingTap=null; message("바닥을 천천히 비춰 주세요. 밝고 무늬가 있는 공간에서 잘 인식돼요."); return; }
            camera.getProjectionMatrix(projection,0,.1f,100); camera.getViewMatrix(view,0);
            float[] tap = pendingTap; pendingTap=null;
            boolean placedThisFrame = false;
            if (anchor == null) {
                message("바닥을 충분히 비춘 뒤 화면의 바닥을 눌러 기억 조각을 놓아 주세요.");
                if (tap != null) for (HitResult hit : frame.hitTest(tap[0],tap[1])) {
                    if (hit.getTrackable() instanceof Plane && ((Plane)hit.getTrackable()).isPoseInPolygon(hit.getHitPose())) {
                        anchor=hit.createAnchor(); placedThisFrame = true; break;
                    }
                }
            }
            if (anchor != null && anchor.getTrackingState() == TrackingState.TRACKING) {
                anchor.getPose().toMatrix(model,0); Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,projection,0,mv,0);
                drawDiamond(); message("공간에 놓인 황금빛 기억 조각을 눌러 발견해 보세요.");
                if (tap != null && !placedThisFrame) {
                    float[] point = new float[4]; Matrix.multiplyMV(point,0,mvp,0,new float[]{0,.1f,0,1},0);
                    if (point[3] > 0) {
                        float x=(point[0]/point[3]+1)*width/2, y=(1-point[1]/point[3])*height/2;
                        float radius=Math.max(50, Math.min(width,height)*.09f);
                        if (Math.hypot(tap[0]-x,tap[1]-y) < radius) { listener.onDiscovered(); pendingTap=null; }
                    }
                }
            }
        } catch (Exception error) { message("카메라 또는 공간 추적이 중단됐어요. 다시 열거나 일반 카드로 진행해 주세요."); }
    }
    private void drawBackground(Frame frame) {
        quad.position(0); uv.position(0);
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, uv);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(false); GLES20.glUseProgram(backgroundProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(backgroundProgram,"camera"),0);
        attribute(backgroundProgram,"p",2,quad); attribute(backgroundProgram,"t",2,uv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glDepthMask(true);
    }
    private void drawDiamond() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glUseProgram(objectProgram);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(objectProgram,"mvp"),1,false,mvp,0);
        attribute(objectProgram,"p",3,diamond); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,12);
    }
    private static void attribute(int program,String name,int size,FloatBuffer buffer) {
        int location=GLES20.glGetAttribLocation(program,name); buffer.position(0);
        GLES20.glVertexAttribPointer(location,size,GLES20.GL_FLOAT,false,0,buffer); GLES20.glEnableVertexAttribArray(location);
    }
    private static int program(String vertex,String fragment) {
        int result=GLES20.glCreateProgram(); GLES20.glAttachShader(result,shader(GLES20.GL_VERTEX_SHADER,vertex));
        GLES20.glAttachShader(result,shader(GLES20.GL_FRAGMENT_SHADER,fragment)); GLES20.glLinkProgram(result);
        int[] ok=new int[1]; GLES20.glGetProgramiv(result,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(result)); return result;
    }
    private static int shader(int type,String code) {
        int shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,code); GLES20.glCompileShader(shader);
        int[] ok=new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader)); return shader;
    }
}
