package com.example.firststep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public final class RouteMapActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_route_map);
        Ui.applyInsets(findViewById(R.id.route_root));
        findViewById(R.id.route_back).setOnClickListener(v -> finish());
    }
    public static class RouteView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public RouteView(Context context, AttributeSet attrs) { super(context,attrs); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density=getResources().getDisplayMetrics().density;
            float left=36*density, right=getWidth()-36*density, top=32*density, step=86*density;
            float[] xs={left,right,left,right,left};
            String[] labels={"수업 건물","도서관","생활시설","야외 지점","활동 공간"};
            String[] inputs={"사진으로 관찰","빛으로 발견","목소리로 기억","방향 알아보기","움직임으로 완성"};
            Path path=new Path();path.moveTo(xs[0],top);
            for(int i=1;i<5;i++)path.cubicTo(xs[i-1],top+(i-.5f)*step,xs[i],top+(i-.5f)*step,xs[i],top+i*step);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*density);paint.setColor(0xFFE0D6C7);canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<5;i++){
                float y=top+i*step;paint.setColor(Ui.RUST);canvas.drawCircle(xs[i],y,20*density,paint);
                paint.setColor(0xFFFFFFFF);paint.setTextSize(16*density);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(""+(i+1),xs[i],y+6*density,paint);
                boolean leftSide=i%2==0;float tx=leftSide?xs[i]+34*density:xs[i]-34*density;
                paint.setTextAlign(leftSide?Paint.Align.LEFT:Paint.Align.RIGHT);paint.setColor(Ui.INK);paint.setTextSize(18*density);canvas.drawText(labels[i],tx,y-2*density,paint);
                paint.setColor(Ui.MUTED);paint.setTextSize(12*density);canvas.drawText(inputs[i],tx,y+19*density,paint);
            }
        }
    }
}
