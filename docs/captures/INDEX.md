# 보고서용 캡처 색인

Phase 7 보고서에 넣을 자료. **각 파일이 어느 부에 쓰이는지** 표시한다.
전부 `git rev-parse HEAD` 시점을 파일 머리에 박아뒀다.

| 파일 | 내용 | 쓰이는 곳 |
|---|---|---|
| `c1-e2e-48.txt` | E2E 48건 전체 통과 | **1부** 실행 결과 · **2부** 결함 회귀 방어 |
| `c2-coverage.txt` | JaCoCo 라인 91.4% / 분기 86.3% (게이트 85/80) | **4부** 품질 확보 |
| `c3-tests.txt` | 테스트 160건 계층별 분포 | **0부** 범위표 · **4부** |
| `c4-defects-after.txt` | 스펙 결함 6건의 조치 후 동작 | **2부** (핵심) |
| `c5-swagger.txt` | OpenAPI 엔드포인트 13개 목록 | **1부** API 목록 |
| `c6-offset.txt` | `offset` = 페이지 번호임을 실측 | **3부** 설계 판단 · **7부** 미해결 |
| `c7-ha.txt` | 분산 / 무상태 / 장애 내성 / 헤더 비활성 | **6부** 운영 · **5부** 측정 |

## 이미 저장소에 있어 새로 찍지 않은 것

캡처를 새로 만들지 않고 **기존 증거 파일을 그대로 인용**한다.

| 자료 | 위치 | 쓰이는 곳 |
|---|---|---|
| N+1 개선 전/후 SQL 로그 (22 → 2) | `docs/evidence/n-plus-1.md` | **5부** |
| Lost Update 실측 (오차 88,000 → 0) | `docs/evidence/lost-update.md` | **5부** |
| 낙관적 vs 비관적 락 비교표 | `docs/evidence/lock-comparison.md` | **5부** |
| 부하·스트레스·스파이크·정합성 (macOS) | `docs/evidence/perf/before-*.txt` | **5부** |
| 설정 스윕 (풀·스레드, 두 머신) | `docs/evidence/perf/sweep-*.txt` | **5부** |
| 캐시 낡은 값 6/6 재현 | `docs/evidence/perf/ha-cache-risk.txt` | **5부** |
| 캐시 on/off · ETag −51% | `docs/evidence/perf/win-detail-*.txt` | **5부** |
| HA 처리량 (단일 vs LB+1 vs LB+2) | `docs/evidence/perf/win-ha-load*.txt` | **5부** |
| graceful shutdown (창 66ms) | `docs/evidence/perf/win-shutdown-*.txt` | **5부** |
| 409 사전 예측과 판정 | `docs/evidence/perf/ha-prediction.md` | **5부** 반증 |

> **캐시 낡은 값 재현은 이제 다시 찍을 수 없다.** `@Cacheable`을 제거했기 때문이다.
> `ha-cache-risk.txt`가 그 현상이 존재했다는 유일한 기록이다 —
> **결함을 고치면 그 증거는 기록에만 남는다.**

## ⚠️ 사용자가 직접 촬영해야 하는 것

브라우저 확장이 연결돼 있지 않아 **화면 스크린샷은 찍지 못했다.**
아래는 직접 촬영이 필요하다. 스택이 떠 있으면 바로 열린다.

| 무엇 | URL / 방법 | 쓰이는 곳 |
|---|---|---|
| **Swagger UI 전체 화면** | <http://localhost:8080/swagger-ui.html> | **1부** |
| **Swagger 에서 로그인 → 주문 실행** | 같은 화면에서 `POST /api/customers/login` 실행 후 `POST /api/customers/order` | **1부** (쿠키 인증이 브라우저에서 그대로 동작함을 보임) |

내용 자체는 `c5-swagger.txt`에 텍스트로 확보돼 있어 **없어도 보고서는 성립한다.**
스크린샷은 "브라우저만으로 호출 가능"을 시각적으로 보이기 위한 것이다.

## 캡처를 만들며 발견한 것

**`REVIEW.md`의 `offset` 서술이 틀렸다.** 미해결 항목 표에 "현행(건너뛸 개수)"이라고 적었는데,
코드·주석·Swagger·실제 동작이 모두 **페이지 번호**였다(`offset=1` → id 3,4).
`c6-offset.txt`로 실측해 확인하고 `REVIEW.md`를 정정했다.

> 마감 문서를 쓸 때 원본(`DECISIONS.md` 8절)을 다시 읽지 않고 기억으로 적은 결과다.
> **캡처를 만드는 작업이 문서 검증을 겸했다.**
