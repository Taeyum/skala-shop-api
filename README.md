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

> **DB 포트가 5433인 이유** — 개발 머신에 이미 PostgreSQL이 5432를 쓰고 있으면,
> Docker는 와일드카드 바인딩에 성공해버려서 충돌 에러 없이 `localhost:5432`가 기존 로컬 DB로 간다.
> 이걸 피하려고 호스트에는 5433으로 내보낸다. 컨테이너 내부와 `docker` 프로파일은 그대로 5432를 쓴다.

기본값은 **개발 전용**이다. 실제 크리덴셜은 저장소에 두지 않고 환경변수나 `.env`로 주입한다 (`.env`는 gitignore 대상).

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
