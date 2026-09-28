# 예상 신용점수 모델 — DODAM_CREDIT_V1 (S15P21D105-680)

## 목적과 한계

`DODAM_CREDIT_V1`은 4,000명 시연 사용자의 대출·카드·상환·예적금 데이터를 일관된 1~1,000점으로 표현하는 합성
개인신용점수다. 실제 KCB/NICE 점수가 아니며 화면과 API에서는 `ESTIMATED`, 저장값에서는 `SYNTHETIC`으로 식별한다.
CB사가 보유하는 장기간의 전체 금융기관 이력과 비공개 통계모형이 없으므로 KCB/NICE 상표나 실제 조회점수로 표시하지 않는다.

국내 개인신용평가는 2021년부터 1~1,000점 점수제를 사용한다. 모델의 다섯 영역과 비중은 KCB가 공개한 일반고객 평균
비중을 따른다. 세부 구간과 감점은 도담 seed 데이터로 계산 가능하도록 정의한 서비스 초기값이며 공인 산식이 아니다.

- 금융위원회 점수제 전환: <https://www.fsc.go.kr/no010101/74473>
- KCB 주요 평가부문: <https://www.allcredit.co.kr/screen/sc0682112929>
- NICE 개인신용평점체계: <https://www.credit.co.kr/ib20/mnu/BZWDMPMFP00>

| 영역 | 비중 | 도담 입력 |
| --- | ---: | --- |
| 상환이력 | 21% | 대출 상환·할부·마이너스통장 현재 연체 |
| 부채수준 | 24% | 소득 대비 월 상환액, 부채/연소득, 한도 사용률, 활성 대출 수 |
| 신용거래기간 | 9% | 최초 대출 시작일 또는 최초 카드 거래일 |
| 신용거래형태 | 38% | 카드 한도 사용률, 할부, 무담보·고금리 대출, 마이너스통장 사용 |
| 비금융/마이데이터 | 8% | 적금 납입률, 저축성 자산, 비상자금 충족, 소득 유입 지속성 |

성별·생년월일·나이·이름과 K-Means 생성 성향은 입력하지 않는다. 소득 절대금액은 직접 가점하지 않고 상환부담의
분모와 비상자금 목표 비교에만 쓴다.

## 점수식

각 영역은 0~100점이다.

```text
creditIndex = repayment × 0.21
            + debt × 0.24
            + history × 0.09
            + creditType × 0.38
            + myData × 0.08

creditScore = min(1000, 300 + round(7 × creditIndex))
```

현재 대출 또는 마이너스통장 연체가 있으면 649점, 할부 연체만 있으면 699점으로 상한을 둔다. 현재 스키마에는
90일 이상의 과거 장기연체 이력이 없으므로 장기연체 상한은 적용하지 않는다. 향후 이력 데이터가 생기면 모델 버전을 올린다.

세부 구간과 감점값의 단일 기준은
[`DODAM_USER_CREDIT_SCORE_SEED.sql`](../database/seeds/DODAM_USER_CREDIT_SCORE_SEED.sql)이다.

## 데이터 기준

- 산정 시각: `2026-09-27 23:59:59`(Asia/Seoul)
- 카드 이용과 소득 지속성: 전 사용자에게 완결 데이터가 있는 `2026-03-01~2026-08-31` 6개월
- 부채·연체·계좌 잔액: 산정 시점에 저장된 현재 상태
- 없는 정보: 보증채무, 현금서비스·카드론, 외부 금융기관 거래, 장기 과거 연체, 통신·공과금 납부

없는 정보는 정상으로 추정하지도, 무작위로 만들어 감점하지도 않는다. 해당 정보가 추가되면 `DODAM_CREDIT_V2`로
버전을 올리고 기존 산정 결과와 구분한다.

## 스키마와 API

V42가 `user`에 현재 점수와 메타데이터를 추가한다.

| 컬럼 | 설명 |
| --- | --- |
| `credit_score` | 1~1,000점. 없으면 NULL |
| `credit_score_source` | `SYNTHETIC`, `KCB`, `NICE` |
| `credit_score_model_version` | 합성 모델 버전. V1은 `DODAM_CREDIT_V1` |
| `credit_score_updated_at` | 마지막 산정·조회 시각 |

`GET /api/v1/users/me`는 다음 필드를 추가한다.

```json
{
  "creditScore": 768,
  "creditScoreType": "ESTIMATED",
  "creditScoreSource": "SYNTHETIC",
  "creditScoreModelVersion": "DODAM_CREDIT_V1",
  "creditScoreUpdatedAt": "2026-09-27T23:59:59"
}
```

`SYNTHETIC`은 `ESTIMATED`, 실제 KCB/NICE 조회값은 `VERIFIED`로 응답한다. 기존 `creditRating`은 SSAFY 금융망의
자산 기준 A~E 값이라 삭제하지 않는다.

월간 리포트 `efficiency-v2`의 CREDIT 항목은 `credit_score / 10`을 반올림해 0~100점으로 사용한다. 예상 점수가
없는 실제 회원은 기존 `credit_rating`을 호환 입력으로 사용하며, 둘 다 없으면 `CREDIT_SCORE_MISSING`이다. 이미 저장된
`efficiency-v1` 리포트는 변경하지 않는다.

## 적재와 검증

점수 seed는 491·522·523·524·492 이후, 시연 보정 seed를 사용한다면 가장 마지막에 실행한다. 다음을 검증한 뒤에만
영구 테이블을 갱신한다.

- seed 사용자·프로필·카드·거래 사용자 각각 4,000명
- 다섯 영역 모두 0~100, 최종 점수 1~1,000
- 대상은 `seed.kmeans.u0001@dodam.invalid`~`u4000`뿐
- 재실행 결과 동일
- 연체군과 비연체군 평균, 성별 평균, 점수 구간 분포 출력
- U0003은 예외값 없이 동일 산식으로 계산하고 입력·영역별 점수를 별도 출력

점수 구간별 인원은 강제로 맞추지 않는다. 금융상황을 바꿨을 때 점수가 반대 방향으로 움직이는지 검토하고, 분포를
바꾸려면 점수 자체가 아니라 원천 seed 시나리오를 조정한다.
