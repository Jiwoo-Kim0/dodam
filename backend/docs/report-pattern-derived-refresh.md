# U0003 리포트·패턴 파생 결과 정합성 복구

Jira: S15P21D105-699

## 문제와 판정

세 화면은 같은 숫자를 표현하지 않는다.

| 화면 | 값 | 기간 |
| --- | --- | --- |
| 금융 효율 점수 상세 | 원본 비율을 정책으로 환산한 0~100 관리 점수 | 직전 완료 월 |
| 소비 유형 상세 | 소비율·저축률·고정지출 비중 원본 비율 | 직전 완료 월까지 6개월 |
| 리포트 오각형 | 동년배 백분위. `LOWER_IS_BETTER` 축은 `100 - percentileInPeer` | 같은 6개월 분석 |

따라서 금융 효율의 `지출 33점`과 소비 유형의 `소비율 85%`는 직접 비교 대상이 아니다. 반면 설명에 표시된 고정지출
원본 비율이 단월·6개월 변화로 설명할 수 없을 만큼 다르고, 최신 679 원장과도 맞지 않으면 저장 시점이 다른 파생 결과가 섞인 것이다.

679는 U0003의 2026-03~09 급여·고정비·저축 이체를 바꾼다. 이미 생성된 READY 월간 리포트는 저장본을 그대로 반환하고,
패턴 분석은 같은 날 완료본을 재사용할 수 있어 원천 seed만 다시 실행해서는 화면이 갱신되지 않는다.

## 최종 원천 계약

개정된 678과 679를 적용한 2026년 8월 기대값은 다음과 같다.

- 수입 3,012,400원
- 소비 2,559,221원
- 저축성 이체 400,000원
- 고정지출 1,565,221원

패턴 분석 기간은 2026-03~08이다. 애플리케이션 반올림 기준으로 소비율 약 0.86, 저축률 약 0.13,
고정지출 비중 약 0.585이며 예상 군집은 `균형형`이다. 8월 단월 비율과 6개월 비율은 같을 필요가 없다.

## 복구 원칙

- 원천 거래는 699에서 수정하거나 삭제하지 않는다.
- 패턴·현금흐름·진단은 `forceRefresh`로 새 이력을 만들고 기존 이력을 보존한다.
- 추천은 진단을 참조하고 개선 행동까지 연결될 수 있으므로 삭제하지 않는다. 최신 진단을 만든 뒤 필요한 추천을 새로 생성한다.
- READY 월간 리포트는 재계산 API가 없으므로 U0003의 원천 변경 월만 자식 `monthly_report` → 부모 `report` 순으로 제거한다.
- 리포트 제거 SQL은 최종 679의 8월 합계가 정확히 일치할 때만 대상을 선택한다.
- RESET은 일반 작업 테이블을 쓰므로 동시에 두 번 실행하지 않고, 실행 중에는 U0003 월간 리포트를 요청하지 않는다.

## 실행 순서

1. 678 개정판과 679를 포함한 최종 seed를 순서대로 적용한다.
2. 읽기 전용 검증을 실행한다.

```bash
mysql --default-character-set=utf8mb4 < backend/database/seeds/DODAM_DEMO_U0003_DERIVED_VERIFY_699.sql
```

3. Q1의 `profile_contract_ready = 1`, Q2의 8월 합계가 최종 원천 계약과 같은지 확인한다.
4. U0003의 2026-03~08 월간 리포트만 초기화한다.

```bash
mysql --default-character-set=utf8mb4 < backend/database/seeds/DODAM_DEMO_U0003_DERIVED_RESET_699.sql
```

5. 출력의 `source_contract_ready = 1`, `delete_count_matches = 1`을 확인한다. `ready = 0`이면 원천 seed 순서를 바로잡고
   다시 실행한다. 스크립트는 이 경우 영구 데이터를 삭제하지 않는다.
6. U0003으로 인증한 뒤 파생 결과를 다음 순서로 새로 만든다. 오래된 같은 날 패턴을 진단이 재사용하지 않도록
   현금흐름 → 패턴 → 진단 순서를 지킨다.

```http
POST /api/v1/cashflow/forecasts
Content-Type: application/json

{"forceRefresh":true}
```

```http
POST /api/v1/patterns/analyze
Content-Type: application/json

{"forceRefresh":true}
```

새 패턴 분석을 만든 다음 진단을 강제 실행한다. 진단 내부의 패턴 보장 호출은 방금 만든 완료본을 재사용한다.

```http
POST /api/v1/diagnoses
Content-Type: application/json

{"forceRefresh":true}
```

필요하면 최신 진단 기준 추천을 생성한다. 기존 추천은 감사 이력으로 남는다.

```http
POST /api/v1/recommendations/generate
Content-Type: application/json

{}
```

7. `GET /api/v1/reports/monthly?yearMonth=2026-08`을 호출해 새 저장본을 만든다.
8. 검증 SQL을 다시 실행하고 Q4·Q5를 확인한다.

## 검증 기준

- Q2 8월 원천 합계가 최종 계약과 같다.
- Q3의 6개월 비율과 Q5 최신 패턴 `clusterFeatures`가 애플리케이션 반올림 범위에서 같다.
- Q5의 분석 창은 `2026-03`~`2026-08`, 군집은 `균형형`이다.
- Q4에서 새 8월 리포트의 효율 항목 `value`가 Q2의 단월 비율과 같다.
- 소비 유형 상세와 오각형은 같은 최신 `analysisId`를 사용한다.
- 단월과 6개월 값이 다르더라도 각 기간의 원천 집계와 일치하면 정상이다.

## 제한사항

이 절차는 시연 seed 정정에 따른 U0003 전용 복구다. 일반 사용자의 거래 추가·숨김·카테고리 변경에 따른 자동 캐시 무효화는
별도 이슈로 다룬다. 운영 전체 데이터에 이 SQL을 확대 적용하지 않는다.

SQL 문법·사용자 격리·멱등성·사전 조건 실패 시 무삭제 동작은 679 전용 일회용 DB에서 다음 스크립트로 회귀 검증한다.

```powershell
backend/scripts/test_demo_derived_reset.ps1
```
