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

- [x] ~~625 시드 적재~~ — 2절, 90건/4명 적재 완료
- [x] ~~`README.md` 적재 상태 문구 갱신~~ — 2절
- [ ] U0004의 MANUAL 9월 거래 1건 — 시연 전 지울지 남길지 판단 (기준선 비교에 영향)

### 2. 625 시드 시연 서버 적재 완료

1절에서 미적재로 확인된 `DODAM_DEMO_SEPTEMBER_SEED.sql`을 시연 서버에 적재했다.
1~8번이 이미 들어간 서버라 11번만 실행했다.

#### 실행 전 확인한 안전장치

운영 DB에 **쓰는** 작업이라 파일이 무엇을 바꾸는지 먼저 읽었다.

| 확인 | 내용 |
| --- | --- |
| preflight | `CHECK` 제약 6개로 잠겨 있다(4명/카드 4/카테고리 20/구독 6/금액 불일치 0/8월 행 4명). 하나라도 어긋나면 INSERT가 실패하고 모든 영구 쓰기가 `ready=1` 조건에 걸려 **아무것도 안 들어간다** |
| DELETE 범위 | `source_type='SEED' AND transaction_id LIKE 'SEED-SEP-U%'` + 4명 조인 → U0004의 MANUAL 행은 대상 밖 |
| 되돌릴 수 없는 변경 | `subscription.next_payment_date`를 10월로 이동. 재실행해도 같은 값이라 멱등하지만 **원래 값은 복구되지 않는다**(README에 명시된 의도된 동작) |
| 롤백 지점 | 당일 04:00 자동 백업 존재(`/home/ubuntu/backups/mysql/dodam-20260917-040001.sql.gz`, 8.6MB) |

#### 실행

```bash
docker compose exec -T -e MYSQL_PWD="$DBP" mysql \
  mysql -h127.0.0.1 -u"$DBU" "$DBN" < .../DODAM_DEMO_SEPTEMBER_SEED.sql
```

비밀번호는 `MYSQL_PWD`로 넘겨 명령줄·프로세스 목록에 남지 않게 했다. `EXIT=0`.

#### 결과 (파일 자체 검증 + 독립 재확인)

| 항목 | 값 |
| --- | --- |
| `SEED-SEP-U%` 거래 | **90건 / 4명 / 2026-09-01~09-27** |
| 사용자별 증가 | 132→154, 138→161, 132→154, 142→165 (각 +22·+23·+22·+23) |
| 9월 구독 결제 이력 | 6건 (거래 불일치 0) |
| `next_payment_date` | 6건 모두 10월로 이동 |
| 계약 위반 | 0건 |
| **시연 계정 4명 밖의 9월 거래** | **0건** |
| U0004 MANUAL 거래 | 4건 그대로 보존 |
| `tmp_dodam_625_*` 작업 테이블 | 파일 끝 cleanup으로 모두 정리됨 |

#### 검증 중 내가 낸 실수 하나

범위 확인에 `email LIKE 'seed.kmeans.u000[1-4]@dodam.invalid'`를 썼는데, **MySQL의 `LIKE`는
`[1-4]` 문자 클래스를 지원하지 않는다**(그건 `REGEXP`). 패턴이 아무것도 매치하지 못해
`NOT LIKE` 쪽이 전부 참이 됐고, "시연 계정 밖 9월 거래 91건"이라는 **거짓 경보**가 나왔다.
`IN (...)`으로 바꿔 재확인하니 실제 값은 0건이었다.

데이터 문제로 보고하기 전에 쿼리부터 의심한 것이 맞았다. 운영 DB 확인에서는 조회 문법 하나가
멀쩡한 데이터를 사고처럼 보이게 만들 수 있다.

#### 문서 갱신

`backend/database/seeds/README.md`의 "시연 서버에는 아직 적재하지 않았다"를 적재 결과로 교체했다.
