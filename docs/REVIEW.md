# 자체 점검 기록

Phase 완료 시점마다 아래 체크리스트로 점검하고 결과를 **누적**한다. 지난 기록은 수정하지 않는다.
체크 실패 항목은 지우지 말고 남긴 뒤, 조치 결과를 다음 Phase 점검에 다시 적는다.
"발견한 문제"에 적은 내용 중 과정에서 배운 것은 `JOURNAL.md`에도 남긴다.

## 점검 항목 (템플릿)

```markdown
## Phase N — YYYY-MM-DD

### 계약 준수
- [ ] SPEC.md의 URI·JSON 필드가 변경되지 않았는가
- [ ] E2E 시나리오(SPEC.md 5절)가 여전히 통과하는가

### 절대 규칙
- [ ] 엔티티에 public setter가 없는가
- [ ] Controller에 엔티티가 노출되지 않았는가
- [ ] 도메인 간 Repository 직접 참조가 없는가
- [ ] Service가 웹 타입(HttpServletRequest, Cookie)에 의존하지 않는가
- [ ] 금액이 전부 BigDecimal인가 (compareTo 사용, equals 금지)

### 품질
- [ ] ./gradlew build 통과
- [ ] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가
- [ ] 근거 없는 설정값(풀 크기, TTL 등)이 남아 있지 않은가

### 발견한 문제
(있으면 기록, 없으면 "없음")
```

---

_(첫 점검은 Phase 0 완료 시)_
