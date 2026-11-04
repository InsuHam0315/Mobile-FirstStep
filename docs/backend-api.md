# 첫발자국 로컬 MySQL API

실제 MySQL 8.0에 계정·세션·미션·완료 기록·사진을 저장하는 FastAPI 서버입니다. SQLite나 로그인 성공 모의 코드를 운영 경로로 사용하지 않습니다. Google 프로젝트가 없는 현재 환경에서는 개발용 계정으로 연결하며, Google 및 학교 인증으로 표시하지 않습니다.

## 실행 환경

- Python 3.13, 고정 버전은 `backend/requirements.txt`를 따릅니다.
- 프로젝트 전용 MySQL: `127.0.0.1:3307`, 데이터베이스 `firststep`, 사용자 `firststep_api`.
- API: `127.0.0.1:8787`. Android 에뮬레이터에서는 호스트 PC를 `10.0.2.2:8787`로 접근합니다.
- 기존 MySQL 3306 서비스와 별도 인스턴스입니다. DB 경로와 비밀번호, 가상환경은 로컬 설정이며 저장소에 올리지 않습니다.

`backend/.env` 또는 프로세스 환경변수를 사용합니다.

| 환경변수 | 의미 | 기본값 |
| --- | --- | --- |
| `DATABASE_URL` | `mysql+pymysql://` 형식의 실제 MySQL 연결 문자열 | 필수 |
| `DEVELOPMENT_LOGIN_ENABLED` | `true`일 때 개발용 계정 등록·로그인 허용 | `false` |
| `GOOGLE_WEB_CLIENT_ID` | 실제 Google OAuth 웹 클라이언트 ID | 빈 문자열, Google 비활성화 |

예시 연결 형식은 `mysql+pymysql://firststep_api:<비밀번호>@127.0.0.1:3307/firststep?charset=utf8mb4`입니다. 실제 비밀번호·Bearer 토큰·ID 토큰을 명령 출력이나 문서에 붙여 넣지 않습니다.

```powershell
cd backend
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8787
```

서버 시작 시 테이블을 생성하고 `app/src/main/assets/missions.json`의 미션을 `missions`에 반영합니다. 미션 내용이나 좌표 검증 상태를 변경했다면 서버를 다시 시작합니다. `create_all`은 최초 개발 스키마 생성용이며 향후 스키마 변경을 자동 마이그레이션하는 기능은 아닙니다.

## 앱과의 API 계약

모든 본문은 UTF-8 JSON입니다. 인증 이후 요청은 `Authorization: Bearer <access_token>`을 사용합니다. 사용자 ID는 세션에서 결정하며, 클라이언트가 다른 사용자 ID를 본문으로 지정할 수 없습니다.

| 메서드와 경로 | 입력 | 응답 |
| --- | --- | --- |
| `GET /health` | 없음 | 실제 DB 연결 확인 후 `{"status":"ok","database":"mysql"}` |
| `GET /v1/config` | 없음 | `google_client_id`, `google_enabled`, `development_login_enabled`, `school_sso_enabled:false` |
| `POST /v1/auth/development/register` | `username`, `password`, `nickname` | `access_token`, `user` |
| `POST /v1/auth/development/login` | `username`, `password` | `access_token`, `user` |
| `POST /v1/auth/google/challenge` | 없음 | `challenge_id`, `nonce` |
| `POST /v1/auth/google` | `id_token`, `challenge_id` | `access_token`, `user` |
| `GET /v1/me` | Bearer 세션 | 사용자 객체 |
| `POST /v1/auth/logout` | Bearer 세션 | 현재 세션 폐기 후 `{"status":"ok"}` |
| `GET /v1/progress/{mode}` | Bearer 세션 | 현재 모드의 스냅샷 |
| `POST /v1/progress/{mode}/sync` | `generation`, `completions` | 병합 후 스냅샷 |
| `DELETE /v1/progress/{mode}?generation=N` | 현재 세대 번호 | 세대 번호를 1 증가시킨 빈 스냅샷 |

`user`는 `{id, nickname, provider, email?}`입니다. 개발용 계정의 `provider`는 `DEVELOPMENT`, 검증된 Google 계정은 `GOOGLE`입니다. 학교 SSO는 제공하지 않습니다.

개발용 계정 규칙:

- 사용자명은 영문·숫자·밑줄·마침표·하이픈 3~64자이며 소문자로 통일합니다.
- 등록 비밀번호는 10~128자이며 Argon2 해시로 저장합니다.
- 닉네임은 1~40자이고 앞뒤 공백을 제거한 결과가 비어 있으면 거부합니다.

세션은 임의의 불투명 토큰이며 DB에는 SHA-256 해시만 저장합니다. 유효기간은 30일이며 로그아웃한 토큰과 만료 토큰은 거부합니다. 로그인 실패는 계정 존재 여부와 무관한 일반 오류를 반환합니다. 입력 검증 오류도 비밀번호·토큰 원문을 응답에 포함하지 않습니다.

## 진행 기록과 사진

`mode`는 대문자 `DEMO`와 `FIELD`만 허용합니다. 새 모드는 `generation:0`부터 시작합니다.

```json
{
  "generation": 0,
  "completions": [
    {
      "mission_id": "M01",
      "saved_at": 1793620000000,
      "discovery_method": "CARD",
      "completion_method": "PHOTO",
      "photo_base64": "<JPEG 파일의 원시 Base64>"
    }
  ]
}
```

- 사용자·모드별 행 잠금과 트랜잭션으로 병합·초기화를 직렬화합니다.
- `(사용자, 모드, 미션)`은 유일합니다. 먼저 저장한 완료 시각·입력 방식·사진을 유지하고 중복 재시도로 변경하지 않습니다.
- 서버 기록과 같은 배치에 포함된 선행 미션을 함께 확인합니다. 순서가 역전된 배치도 코스 순서로 검사하지만 선행 미션 자체가 빠져 있으면 전체 요청을 거부합니다.
- 초기화하면 해당 모드 기록만 삭제하고 `generation`을 증가시킵니다. 오래된 기기가 이전 세대 기록을 보내면 409로 거부해 삭제된 기록이 되살아나지 않게 합니다.
- 새 `LEGACY` 완료는 허용하지 않습니다. 방향 미션의 실제 입력은 `COMPASS`, 대체 입력은 `HOLD`입니다.
- 사진 미션에서 실제 카메라 입력은 `PHOTO`, 개발용 사진 대체는 `DEMO_PHOTO`로 구분합니다. `DEMO_PHOTO`는 DEMO 모드의 `PHOTO_QUIZ`에서만 허용합니다.
- 새로운 `PHOTO`·`DEMO_PHOTO` 완료는 유효한 JPEG 사진이 필요합니다. 이미 서버에 있는 같은 미션을 재전송할 때는 사진을 `null`로 보내도 기존 사진을 유지합니다. 다른 미션은 사진이 `null`이며 불필요한 사진을 보내면 거부합니다.
- 사진은 파일 경로 대신 MySQL `MEDIUMBLOB`에 저장합니다. 원시 Base64를 엄격하게 해석하고 JPEG 형식·전체 디코딩·크기 조건을 검사합니다. 사진은 개당 2 MiB, 3천만 픽셀 이하이며 전체 요청은 12 MiB 이하입니다.
- FIELD 완료는 서버 기획 데이터의 좌표가 검증되었고 유한한 위도·경도 범위 안에 있어야 합니다. 현재 미션 좌표는 미실측이므로 FIELD 신규 완료는 차단됩니다.

이 프로토콜은 물리적 도착이나 실제 카메라 촬영의 진위를 증명하는 장치 인증을 제공하지 않습니다. 서버는 인증된 계정, 코스·입력 방식, 검증된 좌표 설정, 선행 조건과 세대 번호를 검사합니다.

## Google 인증 상태

실제 Google 웹 클라이언트 ID가 비어 있으면 Google 버튼을 활성화하지 않습니다. 프로젝트가 설정되면 서버가 발급한 nonce로 ID 토큰을 받아 공식 `google-auth`로 서명·발급자·대상 클라이언트·만료 시각을 확인합니다. 이메일 검증 여부와 nonce도 확인하고, challenge는 DB에서 5분 동안 한 번만 사용하도록 처리합니다. Google 인증서 요청에는 10초 제한을 적용합니다.

현재 Google 프로젝트·실제 계정으로의 연결은 검증하지 않았습니다. 테스트에서만 검증 결과를 모의하며 개발용 계정 로그인을 실제 Google·학교 인증으로 바꾸어 표시하지 않습니다.

## 오류와 운영 범위

| 상태 | 의미 |
| --- | --- |
| 400 | 허용되지 않는 미션·입력 방식·사진·선행 조건·FIELD 좌표 |
| 401 | 인증 실패, 만료·폐기 세션, 잘못된 Google 증명 |
| 404 | 비활성화된 로그인 방법 또는 알 수 없는 모드 |
| 409 | 사용할 수 없는 계정명 또는 진행 기록 세대 충돌 |
| 413 | 전체 요청 12 MiB 초과 |
| 422 | 입력 형식·길이 오류, 허용하지 않는 필드 |
| 429 | 인증 요청 제한 초과 |
| 503 | DB 사용 불가 |

인증 경로는 IP당 1분에 20회로 제한합니다. 제한은 현재 프로세스 안에서만 공유하므로 다중 프로세스·공개 배포 시에는 별도 공용 제한 장치와 HTTPS를 구성해야 합니다. 현재 실행 범위는 개발 PC의 로컬 서버입니다. SQL 값은 바인딩하고 DB 오류에는 SQL 파라미터를 노출하지 않습니다.

## 검증

실제 프로젝트 전용 MySQL을 대상으로 테스트 계정을 만들고 정리합니다. 테스트용 Google ID 검증은 해당 테스트에서만 대체하며 실제 Google 로그인 검증과 구분합니다. 서버의 기본 설정을 바꾸지 않고 테스트 프로세스에서만 개발용 로그인과 테스트용 Google 클라이언트 ID를 켭니다.

```powershell
cd backend
.\.venv\Scripts\python.exe -c "from dotenv import load_dotenv; load_dotenv('.env'); import pytest; raise SystemExit(pytest.main(['-q', 'test_app.py']))"
```

2026년 11월 3일 실제 MySQL 테스트 **22개 통과**. 사진의 DB 저장·조회와 최초 값 보존, 계정·모드 분리, 초기화·동기화 경합, 토큰·nonce 만료 및 nonce 동시 재사용, 입력 검증의 비밀값 비노출, 인증 요청 제한, 잘린 JPEG·픽셀 과다 이미지, 청크 요청 크기 제한을 포함합니다. Google 실제 계정 연동은 미검증입니다.

고정 의존성의 공식 배포 정보: [FastAPI](https://pypi.org/project/fastapi/0.162.0/), [SQLAlchemy](https://pypi.org/project/SQLAlchemy/2.1.6/), [PyMySQL](https://pypi.org/project/PyMySQL/1.2.3/), [google-auth](https://pypi.org/project/google-auth/2.62.1/), [Pillow](https://pypi.org/project/Pillow/13.0.0/). 전체 고정 버전은 `backend/requirements.txt`에 기록되어 있습니다.
