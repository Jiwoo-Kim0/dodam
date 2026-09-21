# 시연용 seed 데이터

`backend/database/seeds`의 SQL은 시연·K-Means 학습용 데이터다. Flyway가 자동 실행하지 않으며, 마이그레이션이 끝난 MySQL 8.x에
`mysql` 클라이언트로 직접 적재한다. 모든 파일은 재실행해도 같은 결과가 되게(멱등) 작성되어 있고, 선행 조건 검사(preflight)가
실패하면 영구 테이블에 아무것도 쓰지 않는다.

## 적재 순서

| 순서 | 파일 | Jira | 선행 조건 |
| --- | --- | --- | --- |
| 1 | `DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql`, `DODAM_FINANCIAL_PRODUCTS_BATCH_05_BIG4_CARDS_COMPAT_FIXED.sql` | 525 | V10 |
| 2 | `DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql` | 491 | V1~V3, 애플리케이션이 만든 계좌번호 암호문·해시 fixture 테이블(일반 테이블) |
| 3 | `DODAM_CARD_INSTALLMENT_SEED.sql` | 522 | 491, V11~V12, 카드번호 암호문 fixture 테이블(일반 테이블) |
| 4 | `DODAM_LOAN_MINUS_ACCOUNT_SEED.sql` | 523 | 491, V13~V14 |
| 5 | `DODAM_DEPOSIT_SAVING_SEED.sql` | 524 | 491, V15 |
| 6 | `DODAM_TRANSACTION_SEED.sql` | 492 | 491, 522, V6 (카테고리 기준 행 20개) |
| 7 | `DODAM_GOAL_SEED.sql` | 493 | 491, 524, V5·V7·V8·V9 |
| 8 | `DODAM_SUBSCRIPTION_SEED.sql` | 492 | 491, 522, 6번, V30 (`subscription`·`subscription_payment_history`, 2026-09-11 추가) |
| 9 | `DODAM_NOTIFICATION_SEED.sql` | 519, 520 | 491, 523, 493, 8번, V32 (`notification`, 2026-09-14 추가). 시연 사용자 U0001~U0004의 알림함 최대 6건씩(원천 행이 없는 유형은 생략), `dedupe_key LIKE 'SEED-%'`로 재실행 시 지우고 다시 만든다 |
| 10 | `DODAM_DEMO_PASSWORD_SEED.sql` | 521 | 491, V1. 시연 사용자 U0001~U0004에만 시연 비밀번호의 BCrypt 해시를 넣는다(2026-09-15 추가). 나머지 사용자는 NULL 유지, 재실행 안전 |
| 11 | `DODAM_DEMO_SEPTEMBER_SEED.sql` | 625 | 6번, 8번, V6·V30. 시연 사용자 U0001~U0004에만 2026-09-01~09-27 거래 90건과 9월 구독 결제 이력 6건을 추가하고 구독 `next_payment_date`를 10월로 옮긴다(2026-09-16 추가). 6번·8번을 다시 실행하면 이 파일도 다시 실행한다 |
| 12 | `DODAM_USER_DEMOGRAPHICS_BACKFILL.sql` | 632 | V39. 2026-09-17 이전에 491로 적재한 DB에서 seed 사용자 4,000명의 `birth_date`·`gender`만 채운다(fixture·재적재 불필요, 멱등, 실회원 미접촉). 491 최신판을 새로 돌린 DB에는 필요 없다 |
| 13 | `DODAM_DEMO_LOAN_SEED.sql` | 633 | 491, 1번(`KB_KOOKMIN` `KB 국민ONE대출`), V13, `backend/scripts/make_demo_loan_fixture.py`가 만든 fixture 1행(일반 테이블 `tmp_dodam_demo_loan_crypto`). 시연 사용자 U0003에만 고금리 신용대출 1건(연 8.5%, 잔액 12,998,492원)과 상환 8건을 넣는다. 재실행 멱등. 파일 끝 cleanup이 fixture도 지우므로 재실행 전에 fixture를 다시 만든다 |
| 14 | `DODAM_DEMO_U0003_STORY_SEED.sql` | 636 | 6번(492)·8번·11번(625) 이후, V6. fixture 불필요. U0003 이름을 `김싸피`로 바꾸고 U0003의 변동 소비 거래(2026-03-01~09-27)만 지우고 월별 스토리(5월 여행 아웃라이어 1개월 + 카테고리 변화)로 다시 만든다(약 439건). 수입·고정비·구독·저축 행은 그대로. 재실행 멱등. 6번 또는 11번을 다시 돌리면 이 파일도 다시 실행한다 |
| 15 | `DODAM_DEMO_SUBSCRIPTION_SEED.sql` | 655 | 6번(492)·8번·11번(625) 이후, V6·V30. fixture 불필요. 시연 사용자 U0001~U0004의 구독만 지우고 `SubscriptionServiceCatalog` 이름·요금의 구독 5~7건(23건)과 2026-03~09 구독 결제 거래 165건·결제 이력 158건으로 다시 만든다(2026-09-20 추가). 다른 3,996명은 8번 그대로. 재실행 멱등. 6번·8번·11번 중 하나를 다시 돌리면 이 파일도 다시 실행하고, 그 뒤 9번(알림)도 다시 돌린다 |

2~5는 계좌·카드번호 원문을 파일에 두지 않기 위해 애플리케이션 코덱으로 만든 fixture 테이블(`tmp_dodam_*_crypto`, `*_account_keys`)이
같은 스키마에 있어야 한다. 각 파일 머리말에 정확한 계약이 있다. 6~8은 fixture 없이 자연키(사용자 이메일, 계좌 유형·이름)로 참조한다.

**작업 테이블은 TEMPORARY가 아니다.** MySQL은 한 문장에서 같은 TEMPORARY 테이블을 두 번 참조할 수 없다(`ERROR 1137 Can't reopen table`).
2·3·6·7·8번은 자릿수 교차곱과 preflight 서브쿼리가 그 제약에 걸리므로 `tmp_dodam_*` 작업 테이블과 2·3번의 fixture 테이블을 일반 테이블로 만들고
파일 끝의 cleanup 절에서 모두 지운다. 4·5번은 파생 테이블과 단일 참조 문장으로 작성돼 TEMPORARY fixture로도 동작한다(머리말 참고).
파일이 중간에 멈추면 작업 테이블이 남는데, 다음 실행의 `DROP TABLE IF EXISTS`가 정리한다.

## 공통 규약

- 사용자: `seed.kmeans.u0001@dodam.invalid` ~ `u4000`. 성향 코드 `MOD(순번-1, 4)`: 0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE.
  이 코드는 생성 규칙에만 쓰이고 어떤 테이블에도 저장되지 않는다(K-Means 라벨 누출 방지).
- (632, 2026-09-17) 491이 `user.birth_date`·`gender`(V39)를 채운다. 나이 22~59세(`22 + MOD(순번×7, 38)`, 생일은 `MOD(순번×11, 365)`일 분산, 2026-09-07 기준),
  성별은 8명 주기 `F M F M M F M F`(`MOD(순번 + FLOOR((순번−1)/4), 2)`)라 각 성별·나이대에 성향 4종이 모두 들어가고(성별·나이대가 군집 신호를 갖지 않게) 시연 계정 U0001~U0004는 여·남·여·남이다. V39 이전에 적재한 DB는 12번 `DODAM_USER_DEMOGRAPHICS_BACKFILL.sql`(fixture 불필요, 두 컬럼만 갱신)로 채운다. 재실행 시 `ON DUPLICATE KEY UPDATE`로
  기존 행도 채워지므로 V39 이전에 적재한 DB는 491만 다시 돌리면 된다(fixture 테이블은 다시 만들어야 한다 — 파일 끝 cleanup이 지운다). 나이대별 약 20S 842·30S~50S 1,053명, 성별 2,000명씩.
- 기준 시각: 적재 행의 `created_at`은 `2026-09-07 09:00:00`(목표·구독은 창 시작일 `2026-03-01 09:00:00`). 거래·이력 창은 `2026-03-01 ~ 2026-08-31`.
  시연 사용자 U0001~U0004만 11번이 `2026-09-27`까지 확장한다(`created_at = '2026-09-16 09:00:00'`).
- `source_type = 'SEED'`인 계좌·거래는 금융망 동기화(ACCOUNT scope)가 덮어쓰지 않는다.
- 각 파일 끝의 검증 SELECT로 기대 건수·계약 위반 0건을 확인한다.

## 492 거래 seed (`DODAM_TRANSACTION_SEED.sql`)

사용자당 월 14건(급여 1, 고정 3, 구독 1~2, 저축 이체 1, 변동 8, 시연 사용자 U0001~U0004는 변동 16) × 6개월 ≈ 348,000건.
성향별 변동 소비·저축 이체 비율은 파일 머리말 표를 따른다. 거래 ID는 `SEED-U0001-202603-VAR-03` 형식이고, 재실행 시
`SEED-U%` 거래를 지우고 다시 만든다. 적재 후 주거래 계좌의 `last_transaction_date`를 채운다(491이 위임한 첫 갱신).

## 493 목표 seed (`DODAM_GOAL_SEED.sql`)

모으기 목표 4,000개(연결 계좌: 홀수 사용자 예금, 짝수 사용자 적금, BALANCED는 주거래 추가), 줄이기 목표 2,000개(SPENDING_FOCUSED·VOLATILE),
월말 스냅샷 이력. 524가 CLOSED로 만든 예·적금만 가진 사용자 1,000명은 주거래 계좌를 대신 연결한다(Goal API가 CLOSED 연결을 400으로 막음).
연결 4,750건 = 예금 1,500 + 적금 1,500 + 주거래 1,750. `goal.current_amount`는 연결 계좌 잔액 합으로, Goal API(509·510)가 재계산하는 값과 같다.
seed 목표는 `created_at = '2026-03-01 09:00:00'`으로 식별해 재실행 시 지우고 다시 만든다.

## 625 시연 사용자 9월 거래 (`DODAM_DEMO_SEPTEMBER_SEED.sql`)

492의 창은 8월에 끝나지만 앱 홈·가계부는 이번 달을 기본으로 조회하므로, 최종 발표일(2026-09-28)에 시연 계정으로 로그인하면 9월이 비어 있다.
11번은 로그인 가능한 U0001~U0004에만 `2026-09-01 ~ 2026-09-27`(발표 전날까지) 거래를 추가한다. 나머지 3,996명은 6개월 창 그대로다.

- 생성 규칙은 492와 같고(`month_no = 7`, 같은 해시·금액식·카테고리표), 변동 소비 일자만 `1 + MOD(h, 27)`로 27일 안에 가둔다. 월 변동 예산은 27일로 비례 축소하지 않는다.
- 급여일(10/15/20/25)·고정비(1·5·20일)·구독(12·25일)·저축 이체(급여일+1)는 모두 27일 이전이다.
- 거래 ID는 `SEED-SEP-U0001-202609-VAR-03`. 492의 `SEED-U%`와 접두사를 다르게 둬서 492 재실행이 이 행을 지우거나 창 위반으로 세지 않는다.
- 구독: `subscription_payment_history`에 9월 결제 6건(SUB 거래와 같은 날짜·금액)을 넣고, ACTIVE 구독 6개의 `next_payment_date`를 `2026-10-12`/`2026-10-25`로 옮긴다.
  8번이 넣은 9월 값(`2026-09-12`/`09-25`)은 발표일 기준 이미 지난 날짜라 정기결제 요약의 "다음 결제"가 비게 되기 때문이다.
- 주거래 계좌 `last_transaction_date`를 9월 값으로 갱신한다.
- 재실행: `SEED-SEP-U%` 거래와 9월 이력을 지우고 다시 만든다(멱등). 6번(492) 재실행은 `last_transaction_date`를 8월로, 8번 재실행은 `next_payment_date`를 9월로 되돌리고 9월 이력을 지우므로 그 뒤에 11번을 다시 실행한다.
- preflight: 시연 사용자 4명·주카드 4장·카테고리 20·구독 6개(금액이 492 식과 일치)·492의 8월 행 존재. 하나라도 어긋나면 아무것도 쓰지 않는다.
- 기대 건수: INC 4, FIX 12, SUB 6, SAV 4, VAR 64 = 90건. 9월 소비 규모는 같은 사용자의 7·8월과 같은 수준이다.

## 시연 계정 로그인 (`DODAM_DEMO_PASSWORD_SEED.sql`)

491 seed는 `password_hash`를 NULL로 두므로 seed 사용자는 API 로그인이 불가하다. 10번이 시연 사용자 4명에게만 비밀번호를 설정한다.

| 계정 | 이메일 | 비밀번호 |
| --- | --- | --- |
| U0001~U0004 | `seed.kmeans.u0001@dodam.invalid` ~ `u0004` | `dodam1234` |

- 시연·테스트 전용 값이다. 회원가입 규칙(8~72자, 영문+숫자, 공백 없음)을 만족하며, 해시는 애플리케이션의 `BCryptPasswordEncoder`와 같은 형식(`$2a$`, cost 10)이다.
- 비밀번호를 바꾸려면 새 해시를 만들어 파일의 UPDATE 값만 교체한다(예: gradle 캐시의 `spring-security-crypto` jar로 `BCrypt.hashpw` 실행).
- 적용 후 `backend/scripts/be-a-server-smoke.sh`로 BE-A 8개 API(로그인 → 계좌·거래·소비·목표·정기결제·패턴·알림)를 서버에서 한 번에 점검한다(S15P21D105-521).
- 시연 종료 후 되돌리려면 같은 4개 이메일 조건으로 `password_hash = NULL`.

## 시연 계정 U0003 고금리 대출 (`DODAM_DEMO_LOAN_SEED.sql`)

523은 짝수 사용자에게만 대출을 만들어 U0003(홀수)은 대출이 없고, 발견 화면에 `LOAN/HIGH_INTEREST_RATE`가 나올 수 없었다.
시연 계정 데이터 수정 요청(S15P21D105-633, "3번 계정이 고금리 대출을 보유한 상태로 비효율 항목에 노출")에 따라 13번이 U0003에만 대출 1건을 추가한다.

| 항목 | 값 |
| --- | --- |
| 상품 | KB국민은행 `KB 국민ONE대출`(상품 DB `PERSONAL_CREDIT`, 공시 7.74~8.64%, 한도 2,000만원) → `financial_product_id` 연결 필수 |
| 조건 | 원금 20,000,000 / 60개월 / 원리금균등 / 연 8.5000% / 상환일 10일, 2024-09-10 ~ 2029-09-10 |
| 현재 | 24회 납입(2024-10-10 ~ 2026-09-10, 월 410,331원) → 잔액 **12,998,492원**, 다음 상환 2026-10-10, 잔여 36회 |
| 상환 이력 | 2026-03-10 ~ 2026-09-10 PAID 7건 + 2026-10-10 SCHEDULED 1건(원금·이자는 `LoanRepaymentScheduleCalculator`와 같은 식·반올림) |

- 판정(627 규칙 = API 11): 비교군 `CREDIT`(PERSONAL_CREDIT·CREDIT_REFINANCE, 은행권), 기간 밴드 25~60개월, 원리금균등 → 후보 11개 상품, 공시 최저금리 중앙값 **5.229%**. 편차 **+3.271%p** ≥ 2.0 → 심각도 **HIGH**.
- 대환 추정: 잔여 36회에 맞는 옵션 중 최저 카카오뱅크 `신용대출` 5.028% → 만기까지 이자 1,773,405 → 1,032,145원, **절감 741,260원**(월 20,590·연 247,080). 추천 `LOAN/REFINANCE_LOAN` "'KB 국민ONE대출' 대환 알아보기"가 만들어지고 발견 요약 `expectedAnnualCostSaving`에 247,080이 잡힌다.
- 금리 8.5%는 상품 공시 범위(7.74~8.64) 안의 값이라 "공시금리보다 높다"는 문구와 어긋나지 않는다(기준금리는 비교군 중앙값이지 이 상품의 금리가 아니다).
- 계좌번호 원문은 seed 규칙 `1100{순번6}{유형3}`(유형 101)이며 파일에 없다. fixture는 `make_demo_loan_fixture.py`가 `DATA_ENCRYPTION_KEY`·`NUMBER_HASH_KEY`(infra/.env와 같은 이름)로 만든다(FieldCryptor·SensitiveNumberHasher·AccountNumberMasker와 같은 형식, `cryptography` 필요).
- 출금계좌는 U0003의 유일한 ACTIVE 입출금 계좌(491)를 자연키로 찾으므로 account_keys fixture는 필요 없다. preflight(사용자 1·출금계좌 1·fixture 1·상품 1·해시 충돌 0)가 하나라도 어긋나면 아무것도 쓰지 않는다.
- 부수 효과: 현금흐름 예측(`CashflowEventCollector`)에 월 410,331원 `LOAN_REPAYMENT` 유출이 추가된다. 가계부 `transaction`에는 상환 거래를 넣지 않았다(523도 넣지 않는다). 1번·3번 계정 등 다른 사용자는 건드리지 않는다.
- 적용 뒤 U0003으로 발견 화면을 새로고침(`POST /diagnoses` → `POST /recommendations/generate`)해야 새 진단에 반영된다. 이전 진단은 그대로 남는다.

서버 적용(호스트에서 fixture 생성 → mysql 컨테이너로 적재. `infra/.env` 키 사용):

```bash
# 주의: infra/.env 에는 DB_USERNAME/DB_PASSWORD 가 없다.
#       그 이름은 compose가 backend 컨테이너에 넘길 때 쓰는 것이고,
#       .env 의 키는 MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE 다.
set -a; . infra/.env; set +a
python3 backend/scripts/make_demo_loan_fixture.py > /tmp/demo_loan_fixture.sql   # cryptography 없으면 설치
cd infra
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE"   < /tmp/demo_loan_fixture.sql
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE"   < ../backend/database/seeds/DODAM_DEMO_LOAN_SEED.sql
rm /tmp/demo_loan_fixture.sql
```

- MySQL은 컨테이너 안에만 있다(호스트에 mysql 클라이언트·3306 바인딩 없음). `docker compose exec -T`로 표준입력을 넘긴다.
- 비밀번호는 `-p`가 아니라 `MYSQL_PWD`로 넘긴다(`ps`에 남지 않는다).
- 한글이 들어간 즉석 점검 쿼리(`mysql -e "… product_name='KB 국민ONE대출'"`)를 쓸 때는 `--default-character-set=utf8mb4`를 반드시 붙인다.
  빠뜨리면 리터럴이 깨져 **상품이 없는 것처럼 0건**이 나온다. seed 파일 자체는 첫 줄이 `SET NAMES utf8mb4`라 영향이 없다.
- 2026-09-18 인프라(김지우)가 이 절차로 시연 서버에 적재했다(아래 검증 상태). 09-18 이전 판의 `mysql -u"$DB_USERNAME" -p"$DB_PASSWORD"` 블록은 .env 키 이름이
  달라 인증에 실패한다.

되돌리려면 U0003의 `loan_repayment` → `loan` 행을 지운다(`account_name = 'KB 국민ONE대출'`, 사용자 이메일 조건).

## 시연 계정 U0003 소비 스토리·이름 변경 (`DODAM_DEMO_U0003_STORY_SEED.sql`)

492는 모든 seed 사용자에게 매달 같은 변동 예산(U0003: 소득 5,700,000의 20% = 1,140,000, 16건, 가맹점 `도담 식당` 등)을 주어
월별 그래프가 거의 직선이다. 시연 계정 소비 변동 조정 요청(S15P21D105-636, "이름 김싸피, 소비 데이터를 그래프에서 변화가 드러나게 실제처럼")에 따라 14번이
U0003의 **변동 소비(VARIABLE)만** 다시 만든다. 수입·고정비(주거 2,371,200 등)·구독·저축 이체는 492·625 행 그대로다.

| 월 | 변동 소비 합계 | 건수 | 이야기 |
| --- | --- | --- | --- |
| 2026-03 | 974,000 | 59 | 기준 |
| 2026-04 | 939,000 | 58 | 평이 |
| 2026-05 | 1,653,000 | 66 | 제주 여행(TRAVEL 480,000: 제주항공·숙소·렌터카·매표소) + 쇼핑 310,000(나이키·애플스토어 등) |
| 2026-06 | 1,015,000 | 60 | 치과 120,000(MEDICAL) |
| 2026-07 | 1,031,000 | 64 | 외식 증가 시작, 결혼식 축의금 80,000(ETC, 계좌) |
| 2026-08 | 994,000 | 71 | 외식 520,000·카페 145,000 정점, 마트·쇼핑은 줄어 총액은 평이 |
| 2026-09(~27일) | 978,000 | 61 | 진정 |

- 카테고리별 월 금액·건수는 파일 안 plan 표(`tmp_dodam_u3story_plan`)가 전부다. 합계는 100원 단위이며 건별 금액은 가중치(70~130)로 나눠 누적 반올림하므로
  월 합계가 plan과 정확히 같다(검증 `plan_gap 0`). 가맹점은 카테고리별 실제 브랜드명(배달의민족·스타벅스·이마트·카카오 T·올리브영·CGV …), 결제는 8/10 카드(주카드)·2/10 계좌.
- **627 카테고리 소비 증가**(지난달 vs 직전 3개월 평균, 20% 초과·1만 원 이상): FOOD +51.5%(HIGH), CAFE +45.0%(MEDIUM) 두 개만 걸리도록 맞췄다.
  나머지 카테고리는 8월에 같거나 감소(택시 −31%, 마트 −34%, 쇼핑 −62%). U0003 재진단 시 문제 3건(식비·카페·고금리 대출), 추천 3건이 나온다.
- **아웃라이어는 5월 한 달만**이다(약 +65%). 나머지 여섯 달은 1,000,000 기준 ±5% 안에서 움직인다.
- 저축 이체는 492 그대로(소득의 30%)라 K-Means 프로필은 유지된다(로컬 재분석 결과 아래 검증 상태).
- 총 소비(고정 포함) 월 합계는 5월 5.3백만, 나머지 4.6~4.7백만 원이다. 고정비가 소비의 70%대라 총액 그래프에서는 5월 봉우리 하나만 보이고, 카테고리 그래프에서 8월 외식·카페 증가가 보인다.
- 거래 ID는 3~8월 `SEED-U0003-2026MM-VAR-nn`, 9월 `SEED-SEP-U0003-202609-VAR-nn`(nn = 월 안 순번). 492·625의 삭제 패턴(`SEED-U%`, `SEED-SEP-U%`)에 걸리도록 둔 것이라
  **6번 또는 11번을 다시 돌리면 평평한 행으로 돌아가므로 14번을 다시 실행한다.** 14번만 재실행하면 U0003 VAR 행을 지우고 다시 만든다(멱등).
- preflight: U0003 1·주거래 계좌 1·주카드 1·변동 카테고리 11·월별 INCOME 7개월(492 6 + 625 1). 하나라도 어긋나면 이름 변경 포함 아무것도 쓰지 않는다.
- 이름은 `user.name`만 바꾼다(이메일·비밀번호 그대로). 되돌리려면 `name = '...'` UPDATE와 6번·11번 재실행.

서버 적용은 13번과 같은 방식(fixture 없이 seed 파일만):

```bash
set -a; . infra/.env; set +a
cd infra
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < ../backend/database/seeds/DODAM_DEMO_U0003_STORY_SEED.sql
```

적용 뒤 U0003으로 발견 화면을 새로 고쳐(`POST /diagnoses {"forceRefresh":true}` → 추천 생성) 새 소비 데이터 기준 진단을 만든다. 같은 날 만든 이전 진단은 `forceRefresh` 없이는 재사용된다.

## 시연 계정 구독 목록 (`DODAM_DEMO_SUBSCRIPTION_SEED.sql`)

8번은 사용자당 구독을 1건(짝수 사용자 2건), 이름은 `도담 뮤직`·`도담 비디오`·`도담 클라우드`·`도담 리딩` 4종으로만 만든다. 시연 계정 U0003(`seq ≡ 3 (mod 6)`)은
"도담 뮤직 9,900원" 한 줄이라 구독을 여러 개 골라 월 절감액을 보여주는 "내 구독 보기" 화면이 성립하지 않고, FE 브랜드 아이콘도 이름으로 매칭돼 전부 기타 아이콘이 된다
(`docs/subscription-backend-request.md` P1). 15번은 **U0001~U0004의 구독만** 백엔드 `SubscriptionServiceCatalog`의 실제 서비스명·요금으로 다시 만든다.

전체 4,000명을 바꾸지 않는 이유: 구독 결제는 `spending_type = FIXED` 거래라 K-Means 피처(`FIXED_EXPENSE_SHARE` 등)에 들어간다. 4,000명의 구독 건수·금액을 바꾸면
`train_cluster_model.py`로 학습한 군집 아티팩트와 또래 비교 수치가 함께 움직여 재학습이 필요하다. 시연 계정 4명만 바꾸면 모집단 통계에는 영향이 없다.

- 사용자별 구독: U0001 5건(월 54,590) / U0002 6건(83,680) / U0003 7건(94,490) / U0004 5건(ACTIVE 4건 37,690 + `유튜브 프리미엄` PAUSED, 6월까지만 결제).
  서비스: 넷플릭스 17,000·유튜브 프리미엄 14,900·쿠팡 와우 멤버십 7,890·스포티파이 10,900·밀리의서재 9,900·챗GPT 플러스 29,000·네이버플러스 멤버십 4,900·배민클럽 3,990·요기패스 2,900.
  결제일은 3~25일로 분산했고 모두 27일 이전이라 9월 결제도 625 창(9/1~9/27) 안에 들어간다. 파일 머리말의 plan 표가 전부다.
- U0003에는 **미등록** 반복결제 `디즈니+ 9,900`(27일)을 거래에만 7건 넣었다. `subscription` 행이 없으므로 `GET /subscriptions/candidates`가 U0003에서 정확히 이 1건을 돌려준다
  (후보 판정에 카테고리 `SUBSCRIPTION`·카탈로그 매칭 필터가 있는 버전 기준).
- 거래: 구독 1건당 월 1건, 주카드 결제(`payment_method CARD`, `spending_type FIXED`, 카테고리 `SUBSCRIPTION`, `merchant_name` = 서비스명). 거래 ID는 3~8월 `SEED-U0003-2026MM-SUB-nn`,
  9월 `SEED-SEP-U0003-202609-SUB-nn`으로 492·625의 삭제 패턴에 걸리게 두었다. 이력은 거래와 같은 날짜·금액의 `SUCCESS` 1건씩. ACTIVE 구독의 `next_payment_date`는 10월 결제일.
- 지우는 것: 네 사용자의 `SUB-U%` 구독·이력 전부(8번의 `SUB-U000x-01/02` 포함), 네 사용자의 `SEED-U%-SUB-%`·`SEED-SEP-U%-SUB-%` 거래 전부. 수입·고정비·변동·저축 행과 U0004의 MANUAL 거래는 건드리지 않는다.
- 순서: 6번·8번·11번 뒤. **6번 또는 8번 또는 11번을 다시 돌리면 15번을 다시 실행한다.** 15번 적용 뒤 11번만 단독 재실행하면 11번 preflight(`구독 6건·금액 492 식 일치`)가 막혀 아무것도 쓰지 않으므로
  9월을 다시 만들려면 8번 → 11번 → 15번 순으로 돌린다. 9번(알림 seed)은 가장 가까운 ACTIVE 구독 이름을 `PAYMENT_DUE` 문구에 복사하므로 15번 뒤에 9번을 다시 실행한다.
- preflight: 시연 사용자 4·주카드 4·`SUBSCRIPTION` 카테고리 1·plan 24·492의 8월 행 4명·625의 9월 급여 행 4명. 하나라도 어긋나면 아무것도 쓰지 않는다.
- 기대 건수: 구독 23, 거래 165(등록 158 + 디즈니+ 7), 이력 158, 이력-거래 불일치 0, 계약 위반 0, 다른 사용자 구독 5,994 유지.
- 되돌리려면 8번 → 11번을 다시 실행한다(15번의 행은 두 파일의 삭제 패턴에 걸린다).

서버 적용은 13·14번과 같은 방식(fixture 없이 seed 파일만):

```bash
set -a; . infra/.env; set +a
cd infra
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < ../backend/database/seeds/DODAM_DEMO_SUBSCRIPTION_SEED.sql
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < ../backend/database/seeds/DODAM_NOTIFICATION_SEED.sql
```

## 검증 상태

2026-09-11 Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용하고 1~7번을 순서대로 실행했다(fixture는 SHA2 기반 더미로 형식만 충족).
상세 수치는 `backend/docs/remaining-work.md` 492·493 절에 있다.

- 1~7번 전부 오류 없이 종료, 총 43초(522 약 14초, 524 약 11초, 492 약 14초). 파일 끝 검증 SELECT의 위반·불일치·누락 항목 전부 0.
- 전체 재실행 후 모든 테이블 건수와 거래 금액 합·목표 금액 합이 같다(멱등).
- 8번은 V30이 생기기 전에는 Subscription 테이블 부재로 첫 DELETE에서 멈추고 아무것도 쓰지 않았다. V30 추가 후 결과는 `remaining-work.md` 514 절.
- 새 DB에 마이그레이션을 손으로 적용할 때는 V6이 V11의 `card`를 참조하므로 V1~V5 → V10~V11 → V6~V9 → V12~V17 순서가 필요하다(Flyway `out-of-order` 전제).
- 1번은 마이그레이션에 없는 `product_sync_history` INSERT를 제거했다(ERD에만 있는 테이블).

2026-09-14 **시연 서버(j15D105.p.ssafy.io, MySQL 8.0 컨테이너)에 1~8번을 순서대로 적재했다.** 사용자 4,000·계좌 9,001·카드 6,000·할부 3,000·대출 3,000·
예적금 4,000·거래 348,192·목표 6,000·구독 6,000·상품 198. 각 파일 끝 검증 SELECT의 위반 항목 전부 0.

- fixture(491·522·523·524)는 서버 `infra/.env`의 `DATA_ENCRYPTION_KEY`·`NUMBER_HASH_KEY`로 애플리케이션 코덱과 같은 형식으로 만들었다
  (`FieldCryptor`: 12바이트 IV + AES-256-GCM 128비트 태그, `SensitiveNumberHasher`: HMAC-SHA-256 소문자 hex, `AccountNumberMasker` 규칙, 카드는 앞4-****-****-뒤4).
  계좌번호 원문은 `1100{순번6}{유형3}` 형식의 합성값이며(유형: 주거래 001, 휴면 002, 대출 101/102, 마이너스 201, 예금 301, 적금 401) 파일에는 남기지 않았다.
  생성 스크립트는 서버 `~/seed-load/make_seed_fixtures.py`(Python 3, `cryptography`)이고 키는 읽기만 하며 출력하지 않는다.
- 523·524는 fixture를 `DROP TEMPORARY TABLE`로 정리하므로 일반 테이블로 만든 fixture 6개가 남는다. 적재 후 `DROP TABLE tmp_dodam_523_*, tmp_dodam_524_*`로 지운다.
- 컨테이너 안에서 적재할 때는 `mysql -h127.0.0.1`(TCP)로 붙는다. 초기화 중 임시 서버는 소켓 ping이 먼저 성공해 문장이 그쪽으로 들어갈 수 있다.
- seed 사용자는 `password_hash`가 NULL이라 API 로그인이 불가하다. 시연 계정으로 로그인하려면 별도 비밀번호 설정 절차가 필요하다.

2026-09-16 Docker `mysql:8.0`에 V1~V34를 적용하고 fixture(SHA2 더미) → 2번 → 3번 → 6번 → 8번 → **11번** 순으로 실행했다.
11번 preflight `1 / 4 / 4 / 20 / 6 / 0 / 4`, 거래 90건(창 위반 0), 9월 이력 6건(SUB 거래 불일치 0), `next_payment_date` 10월, `last_transaction_date` 9월. 두 번 실행해도 건수·금액 합이 같다.
**2026-09-17 시연 서버(j15d105.p.ssafy.io)에 11번을 적재했다.** 1~8번이 이미 들어간 서버라 11번만 실행했다.
적재 후 실측: `SEED-SEP-U%` 거래 **90건 / 4명 / 2026-09-01~09-27**, 9월 `subscription_payment_history` 6건,
`subscription.next_payment_date` 10월로 이동, 시연 계정 거래 수 132·138·132·142 → **154·161·154·165**(+90).
**시연 계정 4명 밖의 9월 거래는 0건**으로, 파일이 의도한 범위(`digit BETWEEN 1 AND 4`)가 지켜졌다.
작업 테이블 `tmp_dodam_625_*`는 파일 끝 cleanup으로 모두 정리됐다.

U0004에는 시드와 별개로 손으로 만든 `source_type='MANUAL'` 거래 4건(9월 1건 포함)이 있으며,
11번의 DELETE는 `source_type='SEED' AND transaction_id LIKE 'SEED-SEP-U%'`로 한정되어 그대로 보존됐다.

2026-09-17 (632) 로컬 Docker `mysql:8.0`에 V1~V40 적용 후 1·2(491 보강판)·3·6번을 적재해 사용자 4,000(생년월일·성별 결측 0)·거래 348,192건을 만들고
`train_cluster_model.py`로 v2 아티팩트를 학습했다. 491 재실행은 `account.last_transaction_date`를 NULL로 되돌리므로 6번(492)을 다시 돌려 채운다.

2026-09-18 (13번) 로컬 Docker `mysql:8.0`에 V1~V40, 1번, 2번(fixture는 로컬 테스트 키로 실제 코덱 생성), 10번을 적재한 뒤 `make_demo_loan_fixture.py` → 13번을 실행했다.
preflight `1 / 1 / 1 / 1 / 1 / 1 / 0`, 대출 1건(잔액 12,998,492·8.5000·`financial_product_id` 연결)·상환 8건(PAID 7·SCHEDULED 1), 다른 시연 계정 대출 0.
같은 DB로 백엔드를 기동해 U0003으로 로그인 → `GET /loans/high-interest` 대상 1건(기준 5.229%, 후보 11, +3.271%p) → `POST /diagnoses` `issueCount 1 / highSeverityCount 1`,
coverage `HIGH_INTEREST_RATE=EVALUATED`, 문제 `LOAN/HIGH_INTEREST_RATE HIGH` "'KB 국민ONE대출' 금리가 비교상품 공시금리보다 높아요", 대환 추정 `available=true`(카카오뱅크 신용대출 5.028%, 36회, 절감 741,260)
→ `POST /recommendations/generate` `created 1` `LOAN/REFINANCE_LOAN`(월 20,590·연 247,080), 요약 `discovery.expectedAnnualCostSaving = 247080`.
fixture를 다시 만들고 13번을 두 번째 실행해도 같은 `loan_id`·상환 8건이며 `tmp_dodam_demo_loan_*`는 남지 않는다. **서버 적용은 아직이다.**

**2026-09-18 시연 서버(j15d105.p.ssafy.io)에 13번을 적재했다**(인프라 김지우, 위 `docker compose exec` 절차). preflight `1 / 1 / 1 / 1 / 1 / 1 / 0`,
대출 1건·상환 8건. U0003 재진단에서 `LOAN/HIGH_INTEREST_RATE` HIGH(편차 +3.271%p), 대환 추천(만기까지 741,260원 절감)까지 로컬 검증 수치와 일치.
기록: `infra/todo/jiw/day-12.md`.

2026-09-18 (14번) 로컬 Docker `mysql:8.0`(V1~V41, 1·2·3·6·8·10·11·13번 적재)에서 14번을 두 번 실행했다. preflight `1 / 1 / 1 / 11 / 7 / 0`, 월별 변동 소비 합계
974,000 / 939,000 / 1,653,000 / 1,015,000 / 1,031,000 / 994,000 / 978,000(`plan_gap` 전부 0, 건수 59/58/66/60/64/71/61, 최소 금액 3,500), 계약 위반 0,
INC 7·FIX 21·SUB 7·SAV 7 유지, 재실행 시 같은 결과·작업 테이블 잔존 없음. 백엔드를 띄워 U0003 로그인 → `GET /users/me` 이름 `김싸피`,
`POST /diagnoses {"forceRefresh":true}` 문제 3건(식비 +51.5% HIGH, 카페 +45.0% MEDIUM, 고금리 대출 HIGH), `POST /recommendations/generate` 3건
(식비 줄이기 연 2,120,004·대환 247,080·카페 줄이기 540,000). **서버 적용은 아직이다.**

2026-09-20 (15번) 일회용 Docker `mysql:8.0`에 V1~V41 적용 + 최소 fixture(시연 사용자 4명·방관자 1명, 계좌·카드, 8번 모양 구독·거래, U0004 MANUAL 거래 1건)에서 15번을 두 번 실행했다.
preflight `1 / 4 / 4 / 1 / 24 / 4 / 4`, 구독 23(U0001 5·U0002 6·U0003 7·U0004 5), 거래 165·이력 158, 이력-거래 불일치 0·계약 위반 0·카드 불일치 0, 방관자·MANUAL 행 유지, 재실행 결과 동일·작업 테이블 잔존 없음.
전체 seed 체인(1~14번) 위의 재검증과 API 확인(`remaining-work.md` 512 "시연 seed" 절)은 남아 있다. **서버 적용은 아직이다.**
