# Day 11 실제 진행 기록

## 2026-09-17

### 1. 시연 서버 9월 거래 시드(625) 적재 여부 확인 — **미적재 확인**

요청: `DODAM_DEMO_SEPTEMBER_SEED.sql`의 9월 거래가 시연 서버에 적재됐는지, 그리고 이 데이터가
U0001~U0004에서만 조회되는 것이 맞는지.

#### 확인 방법

시연 서버(`ssafy-ec2`)에 SSH로 붙어 MySQL 컨테이너에 **읽기 전용 SELECT만** 실행했다.
비밀번호는 `MYSQL_PWD` 환경변수로 넘겨 명령줄·프로세스 목록에 노출되지 않게 했다.

```sql
SELECT COUNT(*) FROM transaction WHERE transaction_id LIKE 'SEED-SEP-U%' AND source_type='SEED';
SELECT COUNT(*), MAX(transaction_date) FROM transaction WHERE source_type='SEED';
```

#### 결과 — 적재되지 않았다

| 확인 | 값 |
| --- | --- |
| `SEED-SEP-U%` 거래 | **0건** |
| 전체 SEED 거래 | 348,192건, 최종일 **2026-08-28** |
| 9월 `subscription_payment_history` | **0건** |

`README.md` 105행의 "시연 서버에는 아직 적재하지 않았다"가 **현재도 정확하다.**
전체 SEED 거래가 8월에서 끊기는 것까지 일치한다(492 시드의 창이 `2026-03-01 ~ 2026-08-31`).

→ 지금 시연 계정으로 앱을 열면 **9월 화면이 비어 보인다.** 이 파일이 존재하는 이유가 정확히 그것이다
(파일 머리말: "the home / ledger screens ... would show an empty September").

#### 시연 계정별 현황

| 계정 | 거래 수 | 최종 거래일 |
| --- | --- | --- |
| U0001 | 132 | 2026-08-28 |
| U0002 | 138 | 2026-08-27 |
| U0003 | 132 | 2026-08-27 |
| U0004 | 142 | **2026-09-16** |

#### 발견 — U0004에 손으로 만든 9월 거래 1건이 있다

U0004만 9월 거래가 있어 출처를 확인했더니 `source_type = MANUAL`, `2026-09-16` 1건이었다.
시드가 아니라 **누군가 수동 거래 등록 API로 만든 테스트 데이터**다.
DB 전체에서 9월 거래는 이 1건이 전부다.

- 625 시드는 `SEED-SEP-U%` + `source_type='SEED'`만 지우고 다시 만들므로 **이 MANUAL 행과 충돌하지 않는다.**
- 다만 U0004는 깨끗한 기준선이 아니다. 진단·패턴 결과를 계정 간 비교할 때 염두에 둘 것.

### 2. U0001~U0004 한정 여부 — **맞다 (파일 구조로 보장)**

`DODAM_DEMO_SEPTEMBER_SEED.sql:160`이 대상 사용자를 이렇게 잡는다.

```sql
FROM (SELECT d.digit AS sequence_no FROM tmp_dodam_625_digit d WHERE d.digit BETWEEN 1 AND 4) seed
JOIN `user` user_row
  ON user_row.email = CONCAT('seed.kmeans.u', LPAD(seed.sequence_no, 4, '0'), '@dodam.invalid')
```

`BETWEEN 1 AND 4`로 범위를 닫고 이메일로 조인하므로 `u0001`~`u0004` 외에는 행이 만들어지지 않는다.
머리말도 "The other 3,996 K-Means users keep the six-month window untouched"로 같은 말을 한다.

**주의**: "조회가 제한된다"가 아니라 "**데이터가 네 명에게만 생성된다**"는 뜻이다. 조회 API에 계정별
필터가 따로 있는 것이 아니라, 나머지 3,996명에게는 9월 행 자체가 없어서 안 보이는 것이다.
K-Means 학습은 `2026-03 ~ 2026-08` 창을 쓰므로 9월 행이 네 명에게만 있어도 군집 결과가 틀어지지 않는다.

### 남은 것

- [ ] **625 시드 적재** (시연 전 필수). 6·8번이 이미 적재된 서버이므로 11번만 실행하면 된다
      (`README.md` 105행). 적재 후 U0001~U0004의 최종 거래일이 `2026-09-27`이 되는지 확인
- [ ] 적재 후 `README.md` 105행의 "아직 적재하지 않았다" 문구를 갱신할 것
- [ ] U0004의 MANUAL 9월 거래 1건 — 시연 전 지울지 남길지 판단 (기준선 비교에 영향)
