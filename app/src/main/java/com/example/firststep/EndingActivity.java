package com.example.firststep;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.firststep.data.*;

/** Completion recap is derived from saved records; opening this screen never grants rewards. */
public final class EndingActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_mission);Ui.applyInsets(findViewById(R.id.mission_scroll));
        LinearLayout body=findViewById(R.id.mission_body);
        AppMode mode=getIntent().getBooleanExtra("demo",false)?AppMode.DEMO:AppMode.FIELD;
        MissionRepository repository=MissionRepository.getInstance(this);
        body.addView(Ui.text(this,"탐방 기록을 불러오고 있어요",18,Ui.INK,true));
        repository.getCompletions(mode,records->{
            if(isDestroyed()||isFinishing())return;body.removeAllViews();
            if(records.size()!=repository.getMissions().size()) {
                body.addView(Ui.text(this,"다섯 기억을 모으면 마지막 이야기가 열려요.",24,Ui.INK,true));
            }else{
                body.addView(Ui.label(this,mode==AppMode.DEMO?"테스트 코스 완료":"캠퍼스 탐방 완료"));Ui.gap(body,20);
                body.addView(Ui.text(this,"✦\n이제는 내가 아는\n캠퍼스",32,Ui.INK,true));Ui.gap(body,16);
                body.addView(Ui.text(this,"처음에는 이름뿐이었던 장소에 이제 기억이 생겼어요. 수업을 찾고, 배울 곳을 알고, 생활의 정보를 듣고, 방향을 익히고, 잠깐 숨을 돌린 시간까지. 오늘 남긴 첫발자국이 다음 길의 시작이 됩니다.",17,Ui.MUTED,false));Ui.gap(body,24);
                for(Mission mission:repository.getMissions())for(MissionCompletion record:records)if(record.missionId.equals(mission.id)){
                    LinearLayout card=Ui.card(this);card.addView(Ui.label(this,"기억 "+mission.order));Ui.gap(card,8);
                    card.addView(Ui.text(this,mission.place,22,Ui.INK,true));Ui.gap(card,8);card.addView(Ui.text(this,mission.explanation,15,Ui.MUTED,false));
                    if(record.photoUri!=null){ImageView photo=new ImageView(this);Ui.loadPhoto(photo,record.photoUri);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);Ui.gap(card,12);card.addView(photo,new LinearLayout.LayoutParams(-1,Ui.dp(this,180)));}
                    body.addView(card);Ui.gap(body,16);
                }
            }
            Button back=Ui.button(this,"코스로 돌아가기",true);body.addView(back);back.setOnClickListener(v->finish());
        });
    }
}
