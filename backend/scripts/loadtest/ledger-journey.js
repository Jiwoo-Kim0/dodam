import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

/*
 * 도담 가계부·거래 조회 부하테스트 (k6)
 *
 * `user-journey.js`와 **별도 파일인 이유**: 그쪽은 #5~#10 측정의 기준선이라
 * 시나리오를 바꾸면 이전 기록과 비교가 끊긴다. 거래 조회는 성격이 다르므로 따로 잰다.
 *
 * 무엇을 재나 — 24개월(139만 행)로 늘어난 `transaction` 테이블을 실제로 읽는 경로다.
 * `user-journey.js`의 8개 API는 파생·요약 테이블만 읽어서 데이터 증가에 둔감했다(#9).
 *
 * 프런트엔드의 실제 호출 패턴을 그대로 따랐다(2026-09-25 `frontend/src/` 확인).
 *   - 가계부 화면(useLedgerData): 7개 요청을 Promise.all로 **동시** 발사,
 *     그중 거래 목록 2개는 `fetchAllPages`로 **전체 페이지 순회**(size=100)
 *   - 일일 브리핑(useDailyBriefing): 하루치 거래(startDate=endDate)
 *   - 발견 화면(useCategoryMonthlySpending): 카테고리별 월간 거래
 *   - 조회 범위는 **월 단위**(`YYYY-MM-01` ~ 월말)
 *
 * 알려진 한계 — 결과를 읽을 때 반드시 함께 본다:
 *   로그인 가능한 계정이 4개뿐이고(U0001~U0004) 각자 거래가 **246~608건**이다.
 *   `idx_transaction_user_date (user_id, transaction_date)` 인덱스가 있어
 *   기간 조회는 해당 사용자·기간만 읽는다. 즉 **전체 139만 행 중 우리가 읽는 것은 수백 건**이며,
 *   이 측정은 "테이블이 커졌을 때 실사용 조회 경로가 어떤가"를 답하지
 *   "대용량 조회가 느린가"를 답하지 않는다.
 *
 * 사용법:
 *   # 경로·응답 확인
 *   docker run --rm -i --user "$(id -u):$(id -g)" --network infra_app-net \
 *     -e BASE_URL=http://backend:8080/api -e PROFILE=verify \
 *     -v "$PWD/backend/scripts/loadtest:/work" -w /work grafana/k6:latest run ledger-journey.js
 *
 *   # 한계 탐색 (4분 20초)
 *   ... -e PROFILE=probe -e PROBE_VUS=120 ... run ledger-journey.js
 *
 *   # 지속 테스트 (22분)
 *   ... -e PROFILE=load -e LOAD_VUS=50 -e THINK_TIME=0 ... run ledger-journey.js
 *
 * 제약은 `user-journey.js`와 같다(로그인 20r/m, /api 20r/s, access token 30분).
 */

/*
 * =========================================================
 * 1. 실행 환경 설정
 * =========================================================
 */

const PROFILE = (__ENV.PROFILE || 'verify').toLowerCase();

// 스모크 스크립트(be-a-server-smoke.sh)와 같은 규칙: BASE_URL은 `/api`까지 포함하고 경로는 `/v1/...`로 시작한다.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');

if (!BASE_URL) {
    throw new Error('BASE_URL 환경변수가 필요합니다. 예: https://j15D105.p.ssafy.io/api');
}

const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || '10s';

// 화면을 읽는 대기 시간의 배율. 1 = 실사용 흉내, 0 = 대기 없음(한계 탐색).
// 0.5처럼 중간값도 쓸 수 있다. 음수는 0으로 본다.
// 기본값이 프로필마다 다르다: probe는 한계를 재는 것이 목적이라 대기가 있으면
// VU를 아무리 올려도 동시 요청이 늘지 않아 0으로 둔다. 나머지는 실사용 흉내가 기본이다.
const THINK_TIME_DEFAULT = PROFILE === 'probe' ? '0' : '1';
const THINK_TIME = Math.max(0, parseFloat(__ENV.THINK_TIME ?? THINK_TIME_DEFAULT) || 0);
const VERBOSE = (__ENV.VERBOSE || 'false').toLowerCase() === 'true';

// load 프로필의 최대 VU. Nginx를 거치는 실행에서는 20r/s 제한 때문에 50 전후가 의미 있는 상한이다.
const LOAD_VUS = parseInt(__ENV.LOAD_VUS || '50', 10);

// probe 프로필의 마지막 계단. 기본 40은 DB 커넥션 풀(10)의 4배로,
// 풀이 병목이라면 훨씬 앞 계단에서 이미 pending이 오른다. 40에서도 안 오르면
// 병목이 DB가 아니라는 뜻이므로 그때 값을 올려 다시 탐색한다.
const PROBE_VUS = parseInt(__ENV.PROBE_VUS || '40', 10);

const PAGE_QUERY = 'page=0&size=5';

/*
 * =========================================================
 * 2. 테스트 계정 로딩
 * =========================================================
 *
 * 1순위: data/users.json  [{ "email": "...", "password": "..." }, ...]
 * 2순위: DEMO_EMAIL / DEMO_PASSWORD 환경변수
 */

const users = new SharedArray('load-test-users', function () {
    try {
        return JSON.parse(open('./data/users.json'));
    } catch (error) {
        if (__ENV.DEMO_EMAIL && __ENV.DEMO_PASSWORD) {
            return [{ email: __ENV.DEMO_EMAIL, password: __ENV.DEMO_PASSWORD }];
        }
        throw new Error(
            'data/users.json이 없고 DEMO_EMAIL/DEMO_PASSWORD도 없습니다. data/users.example.json을 복사해 만드세요.',
        );
    }
});

if (users.length === 0) {
    throw new Error('data/users.json에 테스트 계정이 없습니다.');
}

for (const user of users) {
    if (!user.email || !user.password) {
        throw new Error('data/users.json의 각 항목에는 email과 password가 필요합니다.');
    }
}

/*
 * =========================================================
 * 3. 사용자 정의 지표
 * =========================================================
 */

// 사용자 스토리 실패 여부 (0: 정상, 1: 실패)
const journeyFailed = new Rate('journey_failed');

// 사용자 스토리 전체 수행 시간(로그인은 setup에서 하므로 포함되지 않는다)
const journeyDuration = new Trend('journey_duration', true);

// 성공적으로 끝난 사용자 스토리 수
const journeysCompleted = new Counter('journeys_completed');

// Nginx limit_req(429) 응답 수. 백엔드 오류와 구분하기 위해 따로 센다.
const rateLimited = new Counter('rate_limited');

/*
 * =========================================================
 * 4. 실행 프로필
 * =========================================================
 */

function buildScenarios() {
    // 검증: VU 1명이 스토리를 3회 수행한다. 경로·계정·응답 형식이 맞는지 확인하는 용도.
    if (PROFILE === 'verify') {
        return {
            verify_ledger_journey: {
                executor: 'per-vu-iterations',
                exec: 'ledgerJourney',
                vus: 1,
                iterations: 3,
                maxDuration: '3m',
            },
        };
    }

    // 부하: LOAD_VUS의 75% → 87.5% → 100% 3단계 계단. 총 21분으로 access token 30분 안에 끝난다.
    if (PROFILE === 'load') {
        const step1 = Math.max(1, Math.round(LOAD_VUS * 0.75));
        const step2 = Math.max(1, Math.round(LOAD_VUS * 0.875));
        return {
            load_ledger_journey: {
                executor: 'ramping-vus',
                exec: 'ledgerJourney',
                startVUs: 0,
                stages: [
                    { duration: '3m', target: step1 },
                    { duration: '3m', target: step1 },
                    { duration: '1m', target: step2 },
                    { duration: '5m', target: step2 },
                    { duration: '1m', target: LOAD_VUS },
                    { duration: '5m', target: LOAD_VUS },
                    { duration: '3m', target: 0 },
                ],
                gracefulRampDown: '30s',
                gracefulStop: '30s',
            },
        };
    }

    // 한계 탐색: 짧은 계단으로 "어디서 막히는가"만 빠르게 본다. 총 4분 20초.
    //
    // load(21분)와 달리 오래 버티는지는 보지 않는다. DB 커넥션 풀이 10이라
    // 포화는 수십 초면 드러나며, 매번 21분을 쓰는 것은 시행착오 비용이 너무 크다.
    //
    // 계단을 5 → 10 → 20 → PROBE_VUS(40)로 두 배씩 올린다. 풀 크기(10)를
    // 가운데 두어 그 앞뒤에서 pending이 언제 생기는지 끼워 볼 수 있다.
    //
    // 한 계단을 1분(ramp 20초 + 유지 40초)으로 잡은 이유는 Prometheus scrape이
    // 15초 간격이라 유지 구간에서 표본이 2~3개는 나와야 하기 때문이다.
    if (PROFILE === 'probe') {
        const steps = [5, 10, 20, Math.max(1, PROBE_VUS)];
        const stages = [];
        for (const target of steps) {
            stages.push({ duration: '20s', target });
            stages.push({ duration: '40s', target });
        }
        stages.push({ duration: '20s', target: 0 });
        return {
            probe_ledger_journey: {
                executor: 'ramping-vus',
                exec: 'ledgerJourney',
                startVUs: 0,
                stages,
                gracefulRampDown: '10s',
                gracefulStop: '10s',
            },
        };
    }

    throw new Error(`지원하지 않는 PROFILE입니다: ${PROFILE}`);
}

/*
 * 임계는 프로필마다 뜻이 다르다.
 *
 * - load  : 합격/불합격 판정. 넘으면 그 버전은 기준 미달이다.
 * - probe : 판정이 아니라 **중단 조건**이다. 한계를 넘기는 것이 목적이므로
 *           임계 초과는 실패가 아니라 찾던 답이다. 다만 한 번 무너진 뒤로는
 *           더 돌려도 얻을 것이 없으므로 abortOnFail로 즉시 끊어 시간을 아낀다.
 * - verify: 경로·응답 형식 확인용이라 지연 임계를 걸지 않는다.
 */
// 부하 중 호출하는 엔드포인트. probe 요약에 엔드포인트별 줄을 띄우는 데 쓴다.
const MEASURED_ENDPOINTS = [
    'transactions', 'transactions_hidden', 'transactions_day', 'transactions_category',
    'tx_categories', 'monthly_summary', 'daily_spending', 'category_spending', 'cashflow_events',
];

function buildThresholds() {
    if (PROFILE === 'probe') {
        const thresholds = {
            // 서버가 5xx를 5% 넘게 뱉기 시작하면 그 지점이 한계다. 더 밀 이유가 없다.
            http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
            // p95 3초는 "사실상 사용 불가" 선이다. 여기까지 왔으면 병목이 이미 드러났다.
            http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '30s' }],
        };

        // k6는 **임계가 걸린 sub-metric만** 요약에 출력한다. probe에는 엔드포인트별 판정 기준이
        // 없으므로 그냥 두면 어느 API가 느린지 전혀 보이지 않는다 — 실제로 첫 실행(#4)에서
        // 전체 p95 84ms만 얻고 개선 대상을 고를 근거가 없었다.
        //
        // 그래서 **절대 실패하지 않는 임계**(min>=0)를 걸어 출력만 끌어낸다.
        // 응답 시간은 언제나 0 이상이므로 이 조건은 깨지지 않는다.
        // 숫자 임계(p(95)<60000 같은 것)를 쓰지 않는 이유는 그 값이 판정 기준처럼 읽히기 때문이다.
        //
        // count>=0이 더 직관적이지만 k6가 거부한다 — Trend 메트릭이 지원하는 집계는
        // avg·min·max·med·p 뿐이고 count는 없다(2026-09-23에 실행해 보고 확인).
        for (const name of MEASURED_ENDPOINTS) {
            thresholds[`http_req_duration{name:${name}}`] = ['min>=0'];
        }
        return thresholds;
    }

    if (PROFILE === 'verify') {
        return {
            http_req_failed: ['rate<0.01'],
            checks: ['rate>0.99'],
            journey_failed: ['rate<0.01'],
        };
    }

    return {
        // 429는 responseCallback에서 실패로 세지 않으므로 여기서는 백엔드 오류(5xx·타임아웃)만 잡힌다.
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        journey_failed: ['rate<0.01'],

        http_req_duration: ['p(95)<800', 'p(99)<1500'],

        // API별 기준. 집계·분석 API는 목록보다 느슨하게 둔다.
        // 거래 목록은 페이지 순회라 목록 API가 여러 번 호출된다. 집계 API는 더 느슨하게 둔다.
        'http_req_duration{name:transactions}': ['p(95)<700'],
        'http_req_duration{name:transactions_hidden}': ['p(95)<700'],
        'http_req_duration{name:transactions_day}': ['p(95)<500'],
        'http_req_duration{name:transactions_category}': ['p(95)<700'],
        'http_req_duration{name:tx_categories}': ['p(95)<500'],
        'http_req_duration{name:monthly_summary}': ['p(95)<700'],
        'http_req_duration{name:daily_spending}': ['p(95)<700'],
        'http_req_duration{name:category_spending}': ['p(95)<700'],
        'http_req_duration{name:cashflow_events}': ['p(95)<700'],
    };
}

export const options = {
    scenarios: buildScenarios(),

    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],

    thresholds: buildThresholds(),
};

// 200~399만 성공으로 보고 429는 http_req_failed에서 제외한다. 429 건수는 rate_limited 지표로 본다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 429));

/*
 * =========================================================
 * 5. 공통 함수
 * =========================================================
 */

function printStep(message) {
    if (!VERBOSE) {
        return;
    }
    console.log(`[VU ${exec.vu.idInTest}] ${message}`);
}

function safeJson(response) {
    try {
        return response.json();
    } catch (error) {
        return null;
    }
}

function isJsonResponse(response) {
    const contentType = response.headers['Content-Type'] || response.headers['content-type'] || '';
    return contentType.toLowerCase().includes('application/json');
}

// ApiResponse 계약: { success: true, data: ... }
function isApiSuccess(response) {
    const body = safeJson(response);
    return Boolean(body && body.success === true);
}

function responseSnippet(response) {
    if (!response.body) {
        return '';
    }
    return String(response.body).substring(0, 300);
}

function abortJourney(stepName, response, reason) {
    journeyFailed.add(1);

    const message = [
        '스토리 실패',
        `step=${stepName}`,
        `reason=${reason}`,
        `status=${response?.status ?? '없음'}`,
        `duration=${response?.timings?.duration ?? 0}ms`,
        `body=${response ? responseSnippet(response) : ''}`,
    ].join(', ');

    console.error(message);
    fail(message);
}

function randomSleep(minSeconds, maxSeconds) {
    // THINK_TIME=0이면 아예 쉬지 않는다. sleep(0)도 이터레이션을 양보시키므로 호출 자체를 건너뛴다.
    if (THINK_TIME === 0) {
        return;
    }
    sleep((minSeconds + Math.random() * (maxSeconds - minSeconds)) * THINK_TIME);
}

/*
 * =========================================================
 * 6. 로그인·로그아웃 (setup / teardown)
 * =========================================================
 */

function login(user) {
    const response = http.post(
        `${BASE_URL}/v1/auth/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        {
            headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
            timeout: HTTP_TIMEOUT,
            tags: { name: 'login', step: 'login' },
        },
    );

    const body = safeJson(response);
    const token = body?.data?.accessToken;

    const succeeded = check(
        response,
        {
            '로그인: 상태 코드가 200이다': (res) => res.status === 200,
            '로그인: accessToken이 존재한다': () => Boolean(token),
        },
        { step: 'login' },
    );

    if (!succeeded || !token) {
        // 비밀번호 seed 미적용(401)이나 로그인 레이트 리밋(429)이 흔한 원인이다.
        throw new Error(
            `로그인 실패: email=${user.email}, status=${response.status}, body=${responseSnippet(response)}`,
        );
    }

    return String(token);
}

export function setup() {
    const tokens = [];

    for (const user of users) {
        tokens.push(login(user));
        // 로그인 레이트 리밋(20r/m, burst 10)을 넘지 않도록 계정 사이에 간격을 둔다.
        if (users.length > 10) {
            sleep(3);
        }
    }

    // 측정 결과를 기록할 때 "어떤 조건이었는지"가 함께 남아야 비교가 성립한다.
    console.log(
        `setup: 계정 ${tokens.length}개 로그인 완료, BASE_URL=${BASE_URL}, PROFILE=${PROFILE}, ` +
            `LOAD_VUS=${LOAD_VUS}, THINK_TIME=${THINK_TIME}` +
            (THINK_TIME === 0 ? ' (대기 없음 — 한계 탐색 모드)' : ''),
    );
    return { tokens };
}

export function teardown(data) {
    for (const token of data.tokens) {
        http.post(`${BASE_URL}/v1/auth/logout`, '{}', {
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
            timeout: HTTP_TIMEOUT,
            tags: { name: 'logout', step: 'logout' },
        });
    }
}

/*
 * =========================================================
 * 7. 인증 GET 요청
 * =========================================================
 */

function authenticatedGet(token, path, requestName) {
    printStep(`${requestName} 요청 시작`);

    const response = http.get(`${BASE_URL}${path}`, {
        headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
        timeout: HTTP_TIMEOUT,
        tags: { name: requestName, step: requestName },
    });

    if (response.status === 429) {
        rateLimited.add(1);
        // Nginx가 막은 것이므로 스토리 실패로 세지 않고 이번 반복만 조용히 끝낸다.
        printStep(`${requestName}: 429 (Nginx limit_req)`);
        fail(`${requestName}: 429 rate limited`);
    }

    const succeeded = check(
        response,
        {
            [`${requestName}: 상태 코드가 200이다`]: (res) => res.status === 200,
            [`${requestName}: JSON 응답이다`]: (res) => isJsonResponse(res),
            [`${requestName}: success=true 이다`]: (res) => isApiSuccess(res),
        },
        { step: requestName },
    );

    if (!succeeded) {
        abortJourney(requestName, response, 'API 응답 검증 실패');
    }

    printStep(`${requestName} 성공: status=${response.status}, duration=${response.timings.duration}ms`);
    return response;
}

/*
/*
 * 동시 요청(http.batch)용 요청 객체. authenticatedGet과 헤더·태그 규칙을 맞춘다.
 */
function authRequest(token, path, requestName) {
    return {
        method: 'GET',
        url: `${BASE_URL}${path}`,
        params: {
            headers: { Authorization: `Bearer ${token}` },
            timeout: HTTP_TIMEOUT,
            tags: { name: requestName, step: requestName },
        },
    };
}

function checkResponse(response, requestName) {
    if (response.status === 429) {
        rateLimited.add(1);
        return;
    }
    check(response, {
        [`${requestName}: 상태 코드가 200이다`]: (r) => r.status === 200,
        [`${requestName}: JSON 응답이다`]: (r) => isJsonResponse(r),
        [`${requestName}: success=true 이다`]: (r) => isApiSuccess(r),
    });
}

/*
 * =========================================================
 * 8. 가계부 시나리오
 * =========================================================
 */

// 조회할 달. 시드가 2024-09 ~ 2026-09이므로 최근 달들을 돌아가며 본다.
// 한 달만 고정하면 MySQL 버퍼풀에 그 달만 올라가 실제보다 유리해진다.
const TARGET_MONTHS = ['2026-09', '2026-08', '2026-07', '2026-06'];

function monthRange(yearMonth) {
    const [y, m] = yearMonth.split('-').map(Number);
    const lastDay = new Date(y, m, 0).getDate();
    const today = new Date();
    // FE(useLedgerData)와 같은 처리: 현재 달이면 월말이 아니라 **오늘까지**만 조회한다.
    // 백엔드가 미래 종료일을 400으로 거부한다("종료일은 오늘 이후일 수 없습니다").
    // 이걸 빠뜨려 2026-09 조회가 전부 실패했다(2026-09-25 verify에서 발견).
    const isCurrentMonth = y === today.getFullYear() && m === today.getMonth() + 1;
    const endDay = isCurrentMonth ? today.getDate() : lastDay;
    return {
        yearMonth,
        startDate: `${yearMonth}-01`,
        endDate: `${yearMonth}-${String(endDay).padStart(2, '0')}`,
        // 일일 브리핑용. 현재 달이면 오늘을 넘지 않게 자른다.
        sampleDay: `${yearMonth}-${String(Math.min(15, endDay)).padStart(2, '0')}`,
    };
}

/**
 * FE의 fetchAllPages와 같은 동작 — last가 true일 때까지 페이지를 넘긴다.
 * MAX_PAGES는 안전장치다. 계정당 거래가 최대 608건이라 size=100이면 7페이지면 끝나지만,
 * 응답 형식이 바뀌어 last가 안 오면 VU가 영원히 도는 것을 막는다.
 */
const MAX_PAGES = 15;

function fetchAllPages(token, buildPath, requestName) {
    let page = 0;
    let total = 0;
    while (page < MAX_PAGES) {
        const body = authenticatedGet(token, buildPath(page), requestName);
        const content = body && body.data ? body.data.content : null;
        total += Array.isArray(content) ? content.length : 0;
        const isLast = body && body.data ? body.data.last : true;
        if (isLast !== false) {
            break;
        }
        page += 1;
    }
    return total;
}

export function ledgerJourney(data) {
    const startedAt = Date.now();
    const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];
    const month = monthRange(TARGET_MONTHS[exec.vu.iterationInInstance % TARGET_MONTHS.length]);

    group('01. 가계부 화면 — 월간 전체', function () {
        // FE는 이 7개를 Promise.all로 동시에 던진다. 순차로 부르면 서버가 받는
        // 동시 요청 수가 달라져 실제보다 가벼운 부하가 된다.
        const q = `startDate=${month.startDate}&endDate=${month.endDate}`;
        http.batch([
            authRequest(token, `/v1/transactions/categories`, 'tx_categories'),
            authRequest(token, `/v1/spending/monthly-summary?yearMonth=${month.yearMonth}`, 'monthly_summary'),
            authRequest(token, `/v1/spending/daily?${q}`, 'daily_spending'),
            authRequest(token, `/v1/spending/categories?yearMonth=${month.yearMonth}`, 'category_spending'),
            authRequest(token, `/v1/cashflow/events?${q}`, 'cashflow_events'),
        ]).forEach(function (response, index) {
            const names = ['tx_categories', 'monthly_summary', 'daily_spending', 'category_spending', 'cashflow_events'];
            checkResponse(response, names[index]);
        });

        // 거래 목록은 페이지를 끝까지 넘긴다(표시용 + 숨김 처리분).
        fetchAllPages(token, (p) => `/v1/transactions?${q}&page=${p}&size=100`, 'transactions');
        fetchAllPages(token, (p) => `/v1/transactions?${q}&hidden=true&page=${p}&size=100`, 'transactions_hidden');
    });
    randomSleep(1, 2);

    group('02. 일일 브리핑 — 하루치', function () {
        const day = month.sampleDay;
        fetchAllPages(
            token,
            (p) => `/v1/transactions?startDate=${day}&endDate=${day}&hidden=false&page=${p}&size=100`,
            'transactions_day',
        );
    });
    randomSleep(1, 2);

    group('03. 발견 — 카테고리별 월간', function () {
        fetchAllPages(
            token,
            (p) => `/v1/transactions?${`startDate=${month.startDate}&endDate=${month.endDate}`}&category=FOOD&hidden=false&page=${p}&size=100`,
            'transactions_category',
        );
    });
    randomSleep(3, 5);

    const duration = Date.now() - startedAt;
    journeyDuration.add(duration);
    journeysCompleted.add(1);
    journeyFailed.add(0);

    printStep(`가계부 시나리오 완료: ${duration}ms`);
}
