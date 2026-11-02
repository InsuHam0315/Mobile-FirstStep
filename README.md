# 첫발자국 · FirstStep

Java와 XML Android Views로 만드는 국립군산대학교 캠퍼스 탐방 앱. 교수 피드백 반영 기획서의 **5개 장소·단일 이야기**를 기준으로 구현한다. 제안 발표에 등장한 6개소/10개 미션은 현재 구현 기준에 섞지 않는다.

## 실행

1. Android Studio에서 이 폴더를 **Open**한다. 별도 새 프로젝트 생성은 필요 없다.
2. Gradle 동기화를 기다리고 `app` 실행 구성을 선택한다.
3. Android 7.0(API 24) 이상의 기기나 에뮬레이터를 선택하고 Run을 누른다.
   이 PC에는 실행 검증을 마친 `FirstStep_API35` 에뮬레이터가 추가되어 있다.
4. 닉네임을 입력하면 기본 **테스트 모드**가 열린다. 첫 미션부터 순서대로 체험한다.
5. 발견 화면은 `AR로 찾기`와 `일반 카드로 기억 발견하기` 중 선택한다. 카드 방식은 AR 설치 없이 사용할 수 있다.

처음 생성할 때의 템플릿은 **Empty Views Activity**, 언어 **Java**, 최소 SDK **24**이다. `build.gradle.kts`는 빌드 설정이며 앱 화면과 로직은 Java/XML이다. 현재 생성 환경에 맞춰 compile/target SDK 37, AGP 9.4.1, Gradle 9.6.0을 유지한다. SDK 37 및 Gradle 지정 JDK를 설치해야 한다.

## 구현한 흐름

- 시작/닉네임 → 코스 목록과 잠금 → 발견 → 이야기 → 행동 → 시설 질문 → 완료 저장 → 도감/다음 미션.
- CameraX 사진 촬영, 재촬영, 확인, 관찰 질문, 앱 전용 사진 저장/도감 표시.
- 조도 기준값 측정, 가림·회복 유지 시간 판정, 3초 누르기 대체 조작.
- 한국어 TTS와 자막, 음성 인식 앱 호출, 직접 입력 대체, 질문 정답 확인.
- 가속도 변화 5회 판정과 대체 조작. 방향 미션은 현재 **대체 조작만** 제공한다.
- ARCore 카메라 배경, 평면 hit-test, 앵커에 기억 조각 배치/선택. 일반 카드는 별도 발견 방식으로 기록한다.
- GPS 정확도/최신성/체류/수신 공백 판정. 확인되지 않은 장소는 현장 시작을 막는다.
- Room 진행 저장, 선행 미션 확인, 중복 보상 방지, 테스트/현장 기록 분리, 발견·조작 방식 기록, 사진 포함 초기화.
- 시설 도감 검색, 기록/닉네임 관리, 개념 코스 안내도.

## 데이터와 남은 현장 작업

`app/src/main/assets/missions.json`의 모든 장소는 **후보**이고 좌표가 `null`, `coordinateVerified=false`이다. 실제 건물명·출입구·안전한 경로·시설 안내를 조사한 다음 좌표와 콘텐츠를 반영해야 한다. 좌표를 임의로 채워 현장 완료처럼 표시하지 않는다.

현재 코스 안내도는 순서를 설명하는 도식이며 실제 지리 지도나 보행 경로가 아니다. 실제 방향 센서 미션과 검증된 캠퍼스 안내도는 후속 작업이다. AR 공간 추적, 실물 표식 촬영, GPS 도착, 한국어 음성 서비스와 센서 임계값은 지원 실기기/현장 테스트가 필요하다. 화면 회전/프로세스 재생성 시 미완료 미션은 발견 단계부터 재시작하며, 완료 기록은 보존된다.

앱은 계정/서버 없이 기록을 기기에 저장하며 자동 클라우드 백업을 제외한다. 앱을 삭제하면 기록과 사진도 삭제된다. 시스템 음성 인식 서비스는 네트워크를 사용할 수 있으며 앱이 원본 음성을 저장하지는 않는다.

## 검증

```powershell
.\scripts\verify.ps1
```

단위 테스트, 디버그 APK, Android Lint를 순서대로 실행한다. 연결 기기 테스트는 `gradlew.bat connectedDebugAndroidTest`로 실행한다. 테스트는 테스트 모드 기록을 초기화하므로 개발용 기기에서 실행한다.

- [검증 기록](docs/VERIFICATION.md)
- [독립 검토](docs/REVIEW.md)
- [개발 하네스](docs/HARNESS.md)

## 주요 코드

| 경로 | 역할 |
| --- | --- |
| `MainActivity.java`, `res/layout/activity_main.xml` | 시작, 코스, 도감, 기록 |
| `MissionActivity.java`, `res/layout/activity_mission.xml` | 발견부터 완료까지 미션 흐름 |
| `ArDiscoveryActivity.java`, `ar/ArRenderer.java` | 공간 추적 AR 및 카드 대체 |
| `location/` | 전경 위치 수신, 도착 판정 |
| `sensors/`, `engine/` | 조도, 입력 판정 |
| `data/`, `assets/missions.json` | 후보 콘텐츠, Room 기록, 설정 |

## 사용 라이브러리

AndroidX/Material, CameraX 1.4.2, Room 2.7.2(Java annotationProcessor), ARCore 1.48.0. 앱의 흐름·판정·콘텐츠와 AR 렌더러는 이 프로젝트에서 구현했다. 라이브러리 사용 조건과 출처는 [DEPENDENCIES.md](docs/DEPENDENCIES.md)를 참고한다.
