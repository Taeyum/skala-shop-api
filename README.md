# SKALA-SHOP API

온라인 쇼핑몰 백엔드 REST API. Spring Boot 3.3.0 / Java 17 / Gradle / JPA + PostgreSQL 16 / JWT.

## 실행

```bash
docker compose up
```

이 한 줄이면 PostgreSQL과 애플리케이션이 함께 뜬다. 별도 설치·설정은 필요 없다.
필요한 것은 Docker(Compose v2 포함)뿐이며, 애플리케이션은 <http://localhost:8080> 에서 응답한다.

```bash
docker compose down      # 중지
docker compose down -v   # 중지 + DB 데이터까지 삭제
```

DB만 먼저 띄우려면 `docker compose up -d postgres`.

> **동작 확인** — 기동 후 `bash docs/verify/e2e.sh` 가 **48/48** 이면 정상이다 (`python3` 필요).

## Windows에서 실행할 때

macOS·리눅스라면 위 한 줄로 끝난다. **Windows에서는 두 가지를 먼저 확인한다.**
아래 항목들은 2026-08-08에 실제로 이 저장소를 Windows로 옮기며 겪은 것이다
(경위: `docs/REVIEW.md` "머신 이전 검증").

**① Docker Desktop이 "설치"가 아니라 "실행 중"이어야 한다**

```bash
docker info      # 이 명령이 성공해야 한다
```

실패하면 Docker Desktop을 먼저 띄운다. WSL2 백엔드는 첫 기동에 1~2분 걸린다.

```
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

**② 오래된 클론이라면 줄바꿈을 다시 적용한다**

저장소가 `.gitattributes`로 `gradlew`·`*.sh`를 LF로 고정하므로 **새로 클론하면 문제가 없다.**
다만 그 파일이 추가되기 전에 클론했다면 작업본이 CRLF인 채로 남아 있고, 이렇게 죽는다:

```
#15 [build 5/7] RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
/bin/sh: 1: ./gradlew: not found        (exit 127)
```

파일은 있는데 "not found"다 — 셰뱅이 `#!/bin/sh\r`가 되어 `\r`까지 인터프리터 이름으로 읽힌다.

```bash
git ls-files --eol gradlew        # w/crlf 로 나오면 아래를 실행
rm gradlew && git checkout -- gradlew
```

**③ 검증 스크립트는 `cmd.exe`가 아니라 Git Bash에서 실행한다**

`cmd.exe`나 PowerShell에서 `bash docs/verify/e2e.sh`를 치면 **WSL의 bash가 잡혀** 이렇게 죽는다.

```
<3>WSL (131810 - Relay) ERROR: CreateProcessCommon:818: execvpe(/bin/bash) failed: No such file or directory
```

같은 이름의 실행 파일이 셋 있고 PATH 순서가 WSL 쪽을 먼저 고르기 때문이다.

```
C:\Program Files\Git\usr\bin\bash.exe     ← 이것을 써야 한다
C:\Windows\System32\bash.exe              ← WSL. cmd 에서는 이게 먼저 잡힌다
```

**시작 메뉴에서 `Git Bash`를 열고 실행한다.** 굳이 `cmd.exe`에서 하려면 전체 경로를 쓴다:

```
"C:\Program Files\Git\bin\bash.exe" docs/verify/e2e.sh
```

> Gradle은 예외다 — `cmd.exe`에서는 `gradlew.bat build`가 그대로 동작한다.

**④ 검증 스크립트를 돌릴 때만 — `python3`**

앱 실행(`docker compose up`)에는 파이썬이 필요 없다. `docs/verify/`의 도구에만 쓰인다.

Windows는 `python3`이 **Microsoft Store 앱 실행 별칭(스텁)** 으로 잡히는 일이 있다.
스텁은 `Python ` 한 줄만 찍고 `exit 49`로 끝나며 **스크립트를 실행하지 않는다** —
아무 일도 없이 성공처럼 보인다. 저장소의 `pyguard.sh`가 이 경우를 잡아 안내와 함께 중단하므로
**조용히 잘못된 결과가 나오지는 않는다.** 안내대로 조치하면 된다.

```bash
which -a python3        # 경로에 WindowsApps 가 보이면 스텁이다
```

## 로컬 개발

애플리케이션은 호스트에서, DB만 컨테이너로 띄우는 방식.

```bash
docker compose up -d postgres
./gradlew bootRun
```

프로파일을 지정하지 않으면 `local`로 동작하고, 접속 정보 기본값은 위 compose의 postgres 설정과 일치한다.

## 프로파일

| 프로파일 | 용도 | DB |
|---|---|---|
| `local` (기본) | 호스트에서 직접 실행 | `localhost:5433` (개발용 기본값 내장) |
| `docker` | 컨테이너 실행 | compose가 환경변수로 주입. **기본값 없음 — 누락 시 기동 실패** |
| `test` | 자동화 테스트 | Testcontainers가 컨테이너를 띄우고 `@ServiceConnection`이 접속 정보를 연결 |

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/shopdb` | JDBC URL (`docker` 프로파일에서는 필수) |
| `DB_USERNAME` | `shop` | DB 계정 (`docker` 프로파일에서는 필수) |
| `DB_PASSWORD` | `shoppw` | DB 비밀번호 (`docker` 프로파일에서는 필수) |
| `SERVER_PORT` | `8080` | 애플리케이션 포트 |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | `shopdb` / `shop` / `shoppw` / `5433` | compose의 postgres 서비스 설정 |
| `JWT_SECRET` | `dev-only-do-not-use-in-production-...` | JWT 서명 키. **운영에서는 반드시 교체** |
| `COOKIE_SECURE` | `false` | 인증 쿠키의 `Secure` 플래그. **HTTPS 뒤에 배포하면 `true`** |

> **DB 포트가 5433인 이유** — 개발 머신에 이미 PostgreSQL이 5432를 쓰고 있으면,
> Docker는 와일드카드 바인딩에 성공해버려서 충돌 에러 없이 `localhost:5432`가 기존 로컬 DB로 간다.
> 이걸 피하려고 호스트에는 5433으로 내보낸다. 컨테이너 내부와 `docker` 프로파일은 그대로 5432를 쓴다.

## 포트가 이미 사용 중이라면

5433이나 8080이 다른 프로세스에 잡혀 있으면 `.env`로 바꾼다. 5433도 절대 안전한 포트는 아니다.

```bash
cp .env.example .env
# .env에서 POSTGRES_PORT=15432 처럼 비어 있는 포트로 수정
docker compose up
```

`docker compose`는 `.env`를 자동으로 읽는다. 무엇이 포트를 잡고 있는지는 이렇게 확인한다:

```bash
lsof -nP -iTCP:5433 -sTCP:LISTEN   # macOS / Linux
```

앱을 컨테이너가 아니라 호스트에서(`bootRun`) 띄운다면 `.env`는 읽히지 않으므로 같은 값을 직접 넘긴다:

```bash
POSTGRES_PORT=15432 ./gradlew bootRun
```

기본값은 **개발 전용**이다. 실제 크리덴셜은 저장소에 두지 않고 환경변수나 `.env`로 주입한다 (`.env`는 gitignore 대상).

> **`JWT_SECRET`의 기본값은 애플리케이션이 아니라 `docker-compose.yml`이 준다.**
> 애플리케이션의 `docker` 프로파일은 `${JWT_SECRET}`을 **필수**로 요구하며, 없으면 기동이 실패한다.
> 개발 편의(기본값 제공)는 실행 환경이 책임지고, 애플리케이션은 "시크릿 없이는 뜨지 않는다"는
> 계약을 지킨다. 덕분에 `docker compose up` 한 줄 실행은 유지되면서도, 컨테이너 밖에서
> 직접 배포할 때는 시크릿을 빠뜨릴 수 없다.
>
> 키 길이가 서명 알고리즘을 결정한다 — 32바이트 이상 HS256, 48 이상 HS384, 64 이상 HS512.
> **32바이트 미만이면 기동 시 `WeakKeyException`으로 실패한다.**

> **인증 쿠키는 `HttpOnly` + `SameSite=Strict` + `Path=/`로 발급된다.**
> `Secure`는 기본 `false`다 — 이 스택은 HTTP로 서비스하므로 켜면 브라우저·Postman이 쿠키를
> 보내지 않아 로그인이 동작하지 않는다. **TLS 종단 뒤에 배포하면 `COOKIE_SECURE=true`로 켠다.**
> 현재 정책은 기동 로그 첫머리에 그대로 출력된다.

`.env`로 교체하려면:

```bash
cp .env.example .env
# .env의 JWT_SECRET을 32바이트 이상의 임의 문자열로 교체
docker compose up
```

## 테스트

```bash
./gradlew test
```

Testcontainers가 PostgreSQL 컨테이너를 자동으로 띄우므로 Docker가 실행 중이어야 한다.
매 실행마다 컨테이너를 새로 만들면 느리니, 재사용을 켜두는 것을 권장한다:

```bash
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```

재사용 설정은 개발자별 opt-in이라 저장소에 포함할 수 없다.

## API 문서 (Swagger)

앱을 띄운 뒤 브라우저에서 **<http://localhost:8080/swagger-ui.html>** 을 연다.

1. `2. 고객` → `POST /api/customers` 로 가입한다
2. `POST /api/customers/login` 을 실행한다 — **쿠키가 브라우저에 저장되므로 그다음부터는 그냥 호출하면 된다**
3. `3. 주문` 의 주문·취소·조회를 그대로 실행한다

자물쇠 표시가 있는 5개는 인증이 필요하다. 쿠키가 `HttpOnly`라 `Authorize` 버튼으로는 값을 넣을 수 없고,
넣을 필요도 없다 — 로그인 API를 실행하는 것이 곧 인증이다.

끄려면 `SWAGGER_ENABLED=false` 로 띄운다.

## 운영 엔드포인트

| 경로 | 용도 |
|---|---|
| `/actuator/health` | 전체 상태 |
| `/actuator/health/readiness` | 트래픽을 받을 수 있는가 (DB 포함). 실패 시 로드밸런서에서 제외 |
| `/actuator/health/liveness` | 프로세스가 살아 있는가. 실패 시 재시작 |

그 밖의 Actuator 엔드포인트(`env`·`configprops`·`heapdump` 등)는 **의도적으로 닫혀 있다**(404).
`env`에는 `JWT_SECRET`과 DB 비밀번호가, `heapdump`에는 메모리의 평문이 그대로 담긴다.

모든 응답에는 `X-Trace-Id` 헤더가 붙는다. 오류를 신고할 때 이 값을 함께 알려주면
서버 로그에서 해당 요청 전체(SQL 포함)를 찾을 수 있다.

## 네트워크가 필요하다 (폐쇄망이라면)

`docker compose up`은 **인터넷 접속을 전제로 한다.**

1. 베이스 이미지 풀 — `eclipse-temurin:17-jdk`, `eclipse-temurin:17-jre`, `postgres:16-alpine`
2. Gradle 의존성 다운로드 — 빌드 단계의 `./gradlew dependencies`

**폐쇄망에서는 아래처럼 이미지를 미리 만들어 옮긴다.**

```bash
# 인터넷이 되는 머신에서
docker compose build
docker save skala-shop-api:local postgres:16-alpine -o shop-images.tar

# 폐쇄망 머신에서
docker load -i shop-images.tar
APP_IMAGE=skala-shop-api:local docker compose up -d --no-build   # 빌드를 건너뛴다
```

`--no-build`가 핵심이다. 이것이 없으면 compose가 다시 빌드를 시도하고 의존성 다운로드에서 멈춘다.

> 이 절차를 **미리 수행해 두지는 않았다.** 채점 환경이 폐쇄망이라는 근거가 없고,
> 사전 빌드된 jar나 이미지를 저장소에 넣으면 "소스에서 빌드된다"는 성질을 잃는다.
> 근거 없는 대비 대신 **필요할 때 쓸 절차**를 남긴다 (`DECISIONS.md` 22절).

## 배포와 롤백

### 이미지 태그 전략

**`latest`를 배포에 쓰지 않는다.** `latest`는 "지금 무엇이 떠 있는지"를 알려주지 못하고,
롤백할 대상을 특정할 수도 없다. 같은 태그가 시점마다 다른 이미지를 가리키므로
"어제 것으로 되돌려라"가 불가능해진다.

```bash
# 빌드 — 커밋 해시를 태그로 쓴다. 코드와 이미지가 1:1로 대응된다
GIT_SHA=$(git rev-parse --short HEAD)
docker build -t skala-shop-api:$GIT_SHA .

# 사람이 읽을 버전이 필요하면 함께 붙인다 (교체가 아니라 추가)
docker tag skala-shop-api:$GIT_SHA skala-shop-api:v1.2.0
```

| 태그 | 용도 |
|---|---|
| `<git-sha>` | **배포에 쓰는 태그.** 불변이며 커밋과 1:1 |
| `v<semver>` | 릴리스 표시용 별칭 |
| `latest` | 로컬 편의용. **배포 금지** |

### 롤백 절차

```bash
# 1. 지금 무엇이 떠 있는지 확인한다 (추측하지 않는다)
docker compose ps
docker inspect --format '{{.Config.Image}}' $(docker compose ps -q app)

# 2. 되돌릴 대상을 고른다
docker images skala-shop-api --format '{{.Tag}}\t{{.CreatedAt}}'

# 3. 태그를 바꿔 다시 올린다
APP_IMAGE=skala-shop-api:<이전-git-sha> docker compose up -d --no-deps app

# 4. 확인 — health가 healthy가 될 때까지 기다린 뒤 스모크 테스트
docker inspect --format '{{.State.Health.Status}}' $(docker compose ps -q app)
curl -fsS http://localhost:8080/actuator/health/readiness
```

`stop_grace_period: 30s`와 graceful shutdown 덕분에 **교체 시 처리 중이던 요청은 끝까지 처리된다.**

### 롤백이 안 되는 경우 — 스키마

애플리케이션은 되돌릴 수 있지만 **DB 스키마는 되돌아가지 않는다.**
현재 `ddl-auto: create`이므로 재기동 시 스키마가 새로 만들어지고 **데이터가 사라진다**.
학습 프로젝트라 이 설정을 유지하지만, 실서비스라면 다음이 전제다.

- `ddl-auto: validate` + Flyway/Liquibase로 마이그레이션을 버전 관리
- 컬럼 삭제·타입 변경은 **두 단계로** 나눈다(추가 → 이행 → 제거). 한 번에 바꾸면 롤백 불가
- 롤백 대상 버전이 읽을 수 있는 스키마인지 배포 **전에** 확인한다

## 문서

| 문서 | 내용 |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | API 계약 (변경 금지) |
| [docs/PLAN.md](docs/PLAN.md) | Phase별 구현 계획 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 설계 의사결정 근거 |
| [docs/JOURNAL.md](docs/JOURNAL.md) | 작업 과정 기록 |
| [docs/REVIEW.md](docs/REVIEW.md) | Phase별 자체 점검 |
