/**
 * rushmore-seat / queue smoke test (Tier 1: 10,000 attempts)
 *
 * 흐름: mock 로그인(access token 발급) -> 큐 진입 -> me() 폴링(ADMITTED까지)
 *       -> goal() (admission token 검증) -> dwell -> leave() (90%) / 방치(10%)
 *
 * 실행 전 서버 설정 확인 (application.yml):
 *   rushmore-seat.queue.admission-scheduler-performance-ids: "<PERFORMANCE_ID>"
 *   rushmore-seat.queue.admission-scheduler-target-capacity: 500   (기본 100은 스모크엔 너무 작음)
 *   rushmore-seat.queue.admission-scheduler-delay-ms: 5000
 *
 * 실행:
 *   k6 run -e BASE_URL=http://localhost:8080 -e PERFORMANCE_ID=1 smoke-queue.js
 */

import http from 'k6/http';
import {check, sleep} from 'k6';
import exec from 'k6/execution';
import {Counter, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PERFORMANCE_ID = __ENV.PERFORMANCE_ID || '1';

// poll/timeout 튜닝값 — target-capacity, dwell time과 맞물리니 서버 설정 바꾸면 같이 조정
const POLL_INTERVAL_SECONDS = 1;
const ADMISSION_TIMEOUT_MS = 120_000; // 이 시간 안에 ADMITTED 안 되면 실패로 기록
const DWELL_MIN_SECONDS = 1;
const DWELL_MAX_SECONDS = 5;
const LEAVE_PROBABILITY = 0.9; // 나머지 10%는 leave() 없이 세션 방치 -> TTL 회수 경로 검증

// 커스텀 메트릭
const loginDuration = new Trend('rushmore_login_duration');
const enterDuration = new Trend('rushmore_enter_duration');
const timeToAdmission = new Trend('rushmore_time_to_admission');
const goalDuration = new Trend('rushmore_goal_duration');
const admittedTotal = new Counter('rushmore_admitted_total');
const admissionTimeoutTotal = new Counter('rushmore_admission_timeout_total');
const goalRejectedTotal = new Counter('rushmore_goal_rejected_total');
const abandonedTotal = new Counter('rushmore_abandoned_total');

export const options = {
    scenarios: {
        smoke: {
            executor: 'shared-iterations',
            vus: 300,
            iterations: 10_000,
            maxDuration: '30m',
        },
    },
    thresholds: {
        // 큐 진입 자체는 Redis 왕복 1번짜리 쓰기라 지연이 작아야 함
        rushmore_enter_duration: ['p(95)<300'],
        // goal()은 admission 토큰 검증(읽기 1~2번)만 하니 이것도 가벼워야 함
        rushmore_goal_duration: ['p(95)<300'],
        // 정상 흐름에서 goal()이 거부되면 안 됨 (대기열 우회/오검증 버그)
        rushmore_goal_rejected_total: ['count==0'],
        // 정상 트래픽에서 admission 타임아웃은 거의 없어야 함 (target-capacity가 너무 낮으면 여기서 터짐)
        rushmore_admission_timeout_total: ['count<50'],
    },
};

function randomBetween(min, max) {
    return min + Math.random() * (max - min);
}

// VU/iteration 조합으로 유일한 memberId 생성 (실제 회원 DB와 무관, mock 로그인 전용)
function nextMemberId() {
    return 10_000_000 + exec.vu.idInTest * 100_000 + exec.vu.iterationInInstance;
}

export default function () {
    const memberId = nextMemberId();

    // 1) mock 로그인 -> access token 발급
    const loginRes = http.post(
        `${BASE_URL}/internal/auth/access-tokens`,
        JSON.stringify({ memberId }),
        { headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } },
    );
    loginDuration.add(loginRes.timings.duration);
    const loginOk = check(loginRes, { 'login: 200': (r) => r.status === 200 });
    if (!loginOk) return;

    const accessToken = loginRes.json('accessToken');
    const authHeaders = { headers: { Authorization: `Bearer ${accessToken}` } };

    // 2) 큐 진입
    const enterRes = http.post(
        `${BASE_URL}/performances/${PERFORMANCE_ID}/queue`,
        null,
        { ...authHeaders, tags: { step: 'enter' } },
    );
    enterDuration.add(enterRes.timings.duration);
    const enterOk = check(enterRes, { 'enter: 200': (r) => r.status === 200 });
    if (!enterOk) return;

    // 3) me() 폴링 -> ADMITTED 될 때까지
    const pollStart = Date.now();
    let admissionToken = null;

    while (Date.now() - pollStart < ADMISSION_TIMEOUT_MS) {
        const meRes = http.get(
            `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/me`,
            { ...authHeaders, tags: { step: 'me' } },
        );

        if (meRes.status === 200) {
            const body = meRes.json();
            if (body.status === 'ADMITTED') {
                admissionToken = body.admissionToken;
                break;
            }
        }
        sleep(POLL_INTERVAL_SECONDS);
    }

    if (!admissionToken) {
        admissionTimeoutTotal.add(1);
        return;
    }
    timeToAdmission.add(Date.now() - pollStart);
    admittedTotal.add(1);

    // 4) goal() - admission token 검증 (좌석 hold의 임시 대역)
    const goalRes = http.post(
        `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/goal`,
        null,
        {
            headers: { Authorization: `Bearer ${accessToken}`, 'X-Admission-Token': admissionToken },
            tags: { step: 'goal' },
        },
    );
    goalDuration.add(goalRes.timings.duration);
    const goalOk = check(goalRes, { 'goal: 200': (r) => r.status === 200 });
    if (!goalOk) {
        goalRejectedTotal.add(1);
        return;
    }

    // 5) dwell time - 실제 좌석 선택/결제 체류 시간의 대역
    sleep(randomBetween(DWELL_MIN_SECONDS, DWELL_MAX_SECONDS));

    // 6) leave() 90% / 방치 10% (TTL 자동 회수 경로 검증)
    if (Math.random() < LEAVE_PROBABILITY) {
        http.post(
            `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/leave`,
            null,
            { ...authHeaders, tags: { step: 'leave' } },
        );
    } else {
        abandonedTotal.add(1);
    }
}