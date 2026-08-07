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

> **현재 상태** — Phase 0(스펙 구현)이 끝나기 전이라 엔티티가 없다.
> 그래서 `app` 서비스는 시드(`data.sql`)가 참조할 테이블을 찾지 못해 아직 기동하지 않는다.
> DB만 먼저 띄우려면 `docker compose up -d postgres`.
> Phase 0 완료 시 이 문단은 삭제한다.

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
| `local` (기본) | 호스트에서 직접 실행 | `localhost:5432` (개발용 기본값 내장) |
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

## 문서

| 문서 | 내용 |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | API 계약 (변경 금지) |
| [docs/PLAN.md](docs/PLAN.md) | Phase별 구현 계획 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 설계 의사결정 근거 |
| [docs/JOURNAL.md](docs/JOURNAL.md) | 작업 과정 기록 |
| [docs/REVIEW.md](docs/REVIEW.md) | Phase별 자체 점검 |
