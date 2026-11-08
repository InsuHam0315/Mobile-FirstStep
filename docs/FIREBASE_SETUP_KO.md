# 첫발자국 Firebase 설정 안내

2026-11-08 기준. 앱 패키지와 이 PC의 디버그 인증서에서 직접 확인한 값입니다. 현재 Google 로그인·첫 프로필 모듈 개발을 준비하고 있으며 실제 Firebase 연결 성공을 뜻하지 않습니다.

## 1. 프로젝트 만들기

[Firebase Console](https://console.firebase.google.com/)에서 프로젝트를 추가합니다. 표시 이름은 `FirstStep`으로 해도 됩니다. 프로젝트 ID는 콘솔이 제안한 고유값을 사용해도 됩니다. **무료 Spark 요금제를 유지**하고 결제 계정을 연결하지 않습니다. Google Analytics는 이번 구현에 필요하지 않아 꺼도 됩니다.

## 2. Android 앱 등록

프로젝트 개요에서 Android 앱 추가를 선택합니다.

| 항목 | 입력값 |
| --- | --- |
| Android 패키지 이름 | `com.example.firststep` |
| 앱 닉네임 | `첫발자국 Android` 등 구분 가능한 이름 |
| 디버그 서명 SHA-1 | 아래 값 |

SHA-1:

```text
DA:E3:D0:49:6A:6E:B5:6B:FB:9D:E0:2F:28:CF:FD:B9:AE:A7:30:07
```

프로젝트 설정 → 내 앱 → Android 앱 → 인증서 지문 추가에서 SHA-256도 등록합니다.

```text
68:B3:8D:C0:8C:18:69:85:BF:C3:12:C9:6A:43:4E:AD:33:DB:F0:BB:78:6F:94:F0:78:DB:4B:BA:97:AF:88:5E
```

이 값은 현재 PC의 `C:/Users/InsuHam/.android/debug.keystore` 기준입니다. 팀원의 다른 PC나 배포용 서명 키로 빌드하면 해당 인증서도 별도로 등록해야 합니다. 개인 키 파일 자체를 공유할 필요는 없습니다.

## 3. Google 로그인 켜기

Authentication → 시작하기 → 로그인 방법(Sign-in method) → Google을 활성화합니다. 요청되면 프로젝트 지원 이메일을 본인 Google 이메일로 선택하고 저장합니다. 콘솔 메뉴 위치는 언어/버전에 따라 달라질 수 있습니다.

Google 활성화와 지문 등록을 끝낸 뒤 **최신 `google-services.json`을 다시 다운로드**합니다. OAuth 정보가 포함된 파일을 사용해야 합니다. 다운로드한 파일은 다음 위치에 둡니다.

```text
C:\Users\InsuHam\FirstStep\app\google-services.json
```

파일명 뒤에 `(1)` 등이 붙었다면 정확히 `google-services.json`으로 바꿉니다. 이 경로는 Git 제외 규칙에 추가했습니다. 구현용 작업 폴더의 설정 반영과 Gradle 연결은 관리·구현 채팅에서 처리합니다. 콘솔 안내의 SDK 코드를 직접 붙여 넣을 필요는 없습니다.

## 4. Firestore 만들기

Cloud Firestore → 데이터베이스 만들기에서 **Standard edition**, 기본 데이터베이스 `(default)`, 지역 **서울 `asia-northeast3`**, **프로덕션 모드**를 선택합니다. 지역은 생성 후 변경할 수 없으므로 확인하고 만듭니다.

처음에는 모든 클라이언트 접근이 차단됩니다. 정상이며, 구현한 계정별 접근 규칙을 검증한 후 적용해야 프로필 저장이 됩니다. 전체 공개 테스트 규칙으로 바꾸지 않습니다. 컬렉션이나 프로필을 손으로 만들 필요는 없습니다.

## 5. 설정 후 알려줄 것

- Firebase 프로젝트 ID
- Google 활성화·SHA 지문 등록·Firestore 생성 여부
- 위 경로에 `google-services.json`을 저장했는지

Google 비밀번호나 서비스 계정 비공개 키는 보내지 않습니다. 이 파일이 있어도 실제 OAuth와 Firestore 규칙 적용은 따로 검증해야 합니다. 실제 로그인은 Google Play 서비스가 있는 에뮬레이터에서 본인이 계정을 선택해 확인할 수 있습니다.

## 개발 순서와 비용 범위

먼저 Google 로그인 → 이름·학번·학과 → 본인 프로필 저장/복원 → 로그아웃·계정 전환을 구현하고 검증합니다. 그다음 탐방 기록 동기화를 별도 단계로 진행합니다. 사진 업로드·Cloud Functions·유료 요금제 전환은 이번 범위에 없습니다. Spark도 무료 할당량이 있으므로 무제한 서비스는 아닙니다.

## 확인한 공식 자료

- [Android 프로젝트 등록](https://firebase.google.com/docs/android/setup)
- [Android Google 로그인과 지문·설정 파일](https://firebase.google.com/docs/auth/android/google-signin)
- [Firestore 만들기와 접근 규칙](https://firebase.google.com/docs/firestore/quickstart)
- [Firestore 지역](https://firebase.google.com/docs/firestore/locations)
- [요금제](https://firebase.google.com/pricing)
