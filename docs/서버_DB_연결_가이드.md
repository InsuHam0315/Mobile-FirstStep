# 첫발자국 서버·DB 연결 가이드

확인일: 2026-11-03. 실제 MySQL DBMS와 Android 앱을 연결했다. 현재 사용 가능한 로그인은 **개발용 계정**이며, Google·학교 계정 인증과 구분한다.

## 지금 앱에서 사용하는 방법

1. 첫발자국 실행 → 하단 **내 기록** → **계정으로 로그인**을 누른다. 처음 시작 화면에도 같은 버튼이 있다.
2. 개발용 아이디, 비밀번호, 닉네임을 입력하고 **개발용 계정 등록**을 누른다. 아이디는 영문·숫자·밑줄·마침표·하이픈 3~64자, 비밀번호는 10~128자다. 이미 등록했다면 **개발용 계정 로그인**을 누른다.
3. 기존 비로그인 탐방 기록을 옮기려면 내 기록에서 **이 기기의 기존 기록 가져오기** → **가져오기**를 선택한다. 자동으로 합치지 않으며 원래 기기 기록은 유지한다.
4. 로그인 후 완료한 미션은 서버 동기화를 시도한다. **서버와 동기화**로 다시 실행하고 화면의 결과 메시지를 확인할 수 있다. 통신 실패 시 기기 기록을 유지하며, 동기화가 성공하기 전에는 서버 백업 완료로 보지 않는다.
5. 같은 계정으로 다른 설치본에서 로그인한 뒤 동기화하면 완료 기록과 사진을 받는다. 로그아웃하면 로그인 전에 쓰던 기기 기록으로 돌아간다.

테스트 모드와 현장 모드, 다른 계정의 기록은 분리한다. 로그인해도 미실측 장소의 현장 시작 제한은 유지된다. 미완료 입력 단계·체크포인트는 기기에만 보관하며 완료 기록과 사진을 서버에 저장한다.

## 실제 저장 위치

| 구분 | 위치·역할 |
| --- | --- |
| 서버 DBMS | MySQL Server 8.0, `127.0.0.1:3307`, 스키마 `firststep` |
| DB 데이터 파일 | `C:\Users\InsuHam\FirstStep\.local\mysql\data` |
| 서버 프로그램 | `C:\Users\InsuHam\FirstStep\backend\app.py` |
| API | `http://127.0.0.1:8787` |
| API 확인 페이지 | `http://127.0.0.1:8787/docs` |
| DB 연결 확인 | `http://127.0.0.1:8787/health` → `status: ok`, `database: mysql` |
| 앱 내부 저장 | 게스트용 Room DB와 계정별 Room DB, 앱 전용 사진 파일 |

서버는 사용자, 비밀번호 해시, 세션 토큰 해시, Google 일회용 인증 요청, 모드별 초기화 세대, 미션, 완료 기록을 관리한다. 완료 사진은 `completions`의 JPEG 바이너리로 MySQL에 보관한다. 앱에는 사진 캐시와 Room 기록을 따로 유지한다. 다른 앱이 MySQL에 직접 접속하지 않으며 인증된 API를 통해 접근한다.

이 PC의 기존 MySQL80 서비스(3306)와 별도 인스턴스로 실행한다. 별도 클라우드 서버를 구축하거나 인터넷에 공개한 상태는 아니다. PC를 끄면 이 서버도 사용할 수 없다.

## PC를 다시 켰을 때

프로젝트 폴더에서 PowerShell로 실행한다.

```powershell
cd C:\Users\InsuHam\FirstStep
.\scripts\start-server.ps1
```

이미 실행 중이면 기존 서버 상태를 확인한다. DB 데이터는 그대로 사용한다. API 접속이 안 되면 `.local/api-stderr.log`, DB 시작 실패는 `.local/mysql/server.log`를 확인한다.

Android Studio 에뮬레이터의 기본 API 주소는 `http://10.0.2.2:8787`이다. BlueStacks 등 다른 실행기의 PC 연결 주소는 해당 실행기 네트워크에 따라 다르므로 실제 로그인 화면의 **서버 연결 다시 확인** 결과로 확인한다. 공개 서버·실기기로 확장할 때는 HTTPS API 주소를 별도로 설정해야 한다. 디버그 버전에서만 지정한 로컬 주소의 HTTP를 허용한다.

`firststep.local.properties`는 선택 설정이며 Git에서 제외한다.

```properties
apiBaseUrl=http://10.0.2.2:8787
# 실제 서비스 배포 시 설정
# releaseApiBaseUrl=https://실제-API-도메인
# googleWebClientId=실제-웹-클라이언트-ID.apps.googleusercontent.com
```

앱 설정 변경 후에는 APK를 다시 빌드·설치한다. 서버 설정은 `backend/.env`에 보관한다. 실제 DB 비밀번호는 문서나 저장소에 복사하지 않는다.

## MySQL Workbench에서 직접 확인할 때

새 연결의 Host는 `127.0.0.1`, Port는 `3307`, Username은 `firststep_api`, Default Schema는 `firststep`을 입력한다. 비밀번호는 이 PC의 `backend/.env` 연결 문자열 또는 `.local/mysql/admin.json`의 `api_password`에서 사용자가 확인한다. 두 파일은 Git에서 제외되어 있다.

스키마의 `users`, `tokens`, `google_challenges`, `mode_states`, `missions`, `completions` 테이블을 확인할 수 있다. 개발·자동 테스트로 생성된 계정도 포함된다. 실제 사용자가 만든 계정이나 데이터를 임의로 정리하지 않는다.

## Google 로그인은 이후 이 설정이 필요

Google Cloud/Firebase 프로젝트가 아직 없으므로 지금 Google 버튼은 비활성화한다. 앱의 Credential Manager 연결 코드와 서버의 Google ID 토큰 검증 코드는 준비되어 있다. 현재 MySQL 구성을 위해 Firebase DB를 추가할 필요는 없다.

1. Google Cloud 프로젝트에서 OAuth 동의 화면과 테스트 사용자를 설정한다.
2. Android OAuth 클라이언트를 등록한다. 패키지는 `com.example.firststep`, 현재 PC 디버그 서명의 SHA-1은 아래와 같다.
3. 같은 프로젝트에 웹 OAuth 클라이언트를 등록하고 **웹 클라이언트 ID**를 `backend/.env`의 `GOOGLE_WEB_CLIENT_ID`에 넣는다. 필요하면 앱의 `googleWebClientId`도 동일하게 설정한다. 클라이언트 비밀키를 앱에 넣지 않는다.
4. API를 다시 시작하고 실제 Google 계정으로 로그인·로그아웃·동기화를 검증한다. 서버가 Google 설정을 반환하면 앱에서 버튼을 활성화한다. 현재 실제 Google 계정 검증은 수행하지 않았다.

```text
패키지: com.example.firststep
디버그 SHA-1: DA:E3:D0:49:6A:6E:B5:6B:FB:9D:E0:2F:28:CF:FD:B9:AE:A7:30:07
```

출시 서명이나 다른 PC 서명을 사용하면 해당 서명도 별도 등록한다. Google 로그인 성공과 학교 재학생 인증은 별개다. `@kunsan.ac.kr` 주소만으로 군산대 포털 SSO 연결이나 재학생 확인이 완료되지는 않는다. 학교 로그인은 학교에서 제공·승인한 연동 방식이 확인된 뒤 별도로 구현해야 한다.

공식 참고: [Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Google 서버 토큰 검증](https://developers.google.com/identity/sign-in/web/backend-auth).

## 이번 검증 범위

- Java 단위 테스트 35개 통과, APK 빌드 성공.
- Android API 35 계측 테스트 8개 통과: 실제 서버 계정 등록·로그인·세션 폐기, MySQL 동기화, 로컬 계정 기록 제거 후 서버에서 사진·기록 복원, 다른 계정·게스트 기록 분리 포함.
- 실제 MySQL 백엔드 테스트 22개 통과: 인증·유효성 검사, 사진, 계정·모드 분리, 초기화 경합, 중복·오래된 동기화 방지 포함.
- Android Lint 오류 0개, 경고 88개. 경고가 모두 제거된 상태는 아니다.
- Google 실제 계정 로그인, 학교 SSO, 실기기·현장 GPS/AR/센서는 검증 완료 범위에 포함하지 않는다.

최신 APK SHA-256:
`C5BC898A1023880D658F8E4D91E9253E0738AEE56DD0C26D1760FD4147E36172`

APK 복사 위치: `C:\Users\InsuHam\Desktop\2026-2_모바일\Project\첫발자국_최신.apk`.
