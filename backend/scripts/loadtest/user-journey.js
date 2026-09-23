import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

/*
 * 도담 사용자 스토리 부하테스트 (k6)
 *
 * 시연 계정으로 로그인한 뒤 앱의 대표 조회 화면(홈 → 카드 → 대출 → 진단·추천 → 시뮬레이션)을
 * 순서대로 여는 흐름을 반복한다. 공개 API의 GET만 호출하고, 쓰기 호출은 setup의 로그인과
 * teardown의 로그아웃뿐이다. 서버 DB를 직접 읽거나 쓰지 않는다.
 *
 * 사용법:
 *   BASE_URL=https://j15D105.p.ssafy.io/api PROFILE=verify k6 run backend/scripts/loadtest/user-journey.js
 *   BASE_URL=https://j15D105.p.ssafy.io/api PROFILE=load LOAD_VUS=50 k6 run backend/scripts/loadtest/user-journey.js
 *
 * 계정:
 *   backend/scripts/loadtest/data/users.json (gitignore, users.example.json 참고) 또는
 *   DEMO_EMAIL / DEMO_PASSWORD 환경변수(계정 1개). 시연 계정에는 DODAM_DEMO_PASSWORD_SEED.sql이 적용되어 있어야 한다.
 *
 * 제약(infra/nginx/conf.d/default.conf):
 *   - /api/v1/auth/login 은 IP당 20r/m(burst 10)이라 VU마다 로그인하면 곧바로 429가 난다.
 *     그래서 로그인은 setup()에서 계정 수만큼만 하고 토큰을 모든 VU가 나눠 쓴다.
 *   - /api/ 전체는 IP당 20r/s(burst 40)이다. 한 대에서 수십 VU 이상을 걸면 Nginx 429가 먼저 나오므로
 *     이 스크립트는 429를 `rate_limited` 지표로 따로 세고 http_req_failed에서는 제외한다(responseCallback).
 *     백엔드 자체 한계를 재려면 Nginx를 거치지 않는 BASE_URL(로컬 8080, 도커 네트워크 내부)로 실행한다.
 *   - access token 유효기간은 30분(application.yml)이다. load 프로필 총 시간을 그보다 짧게 유지한다.
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
const VERBOSE = (__ENV.VERBOSE || 'false').toLowerCase() === 'true';

// load 프로필의 최대 VU. Nginx를 거치는 실행에서는 20r/s 제한 때문에 50 전후가 의미 있는 상한이다.
const LOAD_VUS = parseInt(__ENV.LOAD_VUS || '50', 10);

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
            verify_user_journey: {
                executor: 'per-vu-iterations',
                exec: 'userJourney',
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
            load_user_journey: {
                executor: 'ramping-vus',
                exec: 'userJourney',
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

    throw new Error(`지원하지 않는 PROFILE입니다: ${PROFILE}`);
}

export const options = {
    scenarios: buildScenarios(),

    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],

    thresholds: {
        // 429는 responseCallback에서 실패로 세지 않으므로 여기서는 백엔드 오류(5xx·타임아웃)만 잡힌다.
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        journey_failed: ['rate<0.01'],

        http_req_duration: ['p(95)<800', 'p(99)<1500'],

        // API별 기준. 집계·분석 API는 목록보다 느슨하게 둔다.
        'http_req_duration{name:account_summary}': ['p(95)<500'],
        'http_req_duration{name:cards}': ['p(95)<500'],
        'http_req_duration{name:card_summary}': ['p(95)<700'],
        'http_req_duration{name:loans}': ['p(95)<500'],
        'http_req_duration{name:loan_summary}': ['p(95)<700'],
        'http_req_duration{name:diagnoses}': ['p(95)<700'],
        'http_req_duration{name:recommendation_summary}': ['p(95)<700'],
        'http_req_duration{name:simulations}': ['p(95)<700'],
    },
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
    sleep(minSeconds + Math.random() * (maxSeconds - minSeconds));
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

    console.log(`setup: 계정 ${tokens.length}개 로그인 완료, BASE_URL=${BASE_URL}, PROFILE=${PROFILE}`);
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
 * =========================================================
 * 8. 사용자 스토리
 * =========================================================
 *
 * VU 1 → tokens[0], VU 2 → tokens[1], … 계정 수보다 VU가 많으면 처음부터 순환한다.
 */

export function userJourney(data) {
    const startedAt = Date.now();
    const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];

    group('01. 홈 — 계좌 요약', function () {
        authenticatedGet(token, '/v1/accounts/summary', 'account_summary');
    });

    randomSleep(1, 2);

    group('02. 카드 — 목록·요약', function () {
        authenticatedGet(token, '/v1/cards', 'cards');
        authenticatedGet(token, '/v1/cards/summary', 'card_summary');
    });

    randomSleep(1, 2);

    group('03. 대출 — 목록·요약', function () {
        authenticatedGet(token, '/v1/loans', 'loans');
        authenticatedGet(token, '/v1/loans/summary', 'loan_summary');
    });

    randomSleep(1, 2);

    // 진단·추천은 "최신" 단건 API가 이력이 없으면 404이므로, 빈 결과도 200인 목록·요약을 쓴다.
    group('04. 진단·추천 — 이력·요약', function () {
        authenticatedGet(token, `/v1/diagnoses?${PAGE_QUERY}`, 'diagnoses');
        authenticatedGet(token, '/v1/recommendations/summary', 'recommendation_summary');
    });

    randomSleep(1, 2);

    group('05. 시뮬레이션 — 이력', function () {
        authenticatedGet(token, `/v1/simulations?${PAGE_QUERY}`, 'simulations');
    });

    // 사용자가 화면을 읽으며 머무르는 시간
    printStep('사용자 체류 시작');
    randomSleep(3, 5);

    const duration = Date.now() - startedAt;
    journeyDuration.add(duration);
    journeysCompleted.add(1);
    journeyFailed.add(0);

    printStep(`사용자 스토리 완료: ${duration}ms`);
}
