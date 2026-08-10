/**
 * rushmore-seat / open-run burst test (탐색/시연용 — 회귀 검증용 아님)
 *
 * smoke-queue.js와의 차이: smoke-queue.js는 "동시 300명 처리"라는 닫힌 모델(closed
 * workload)이라 유입 속도가 처리 속도에 종속된다. 실제 오픈런은 열린 모델(open
 * workload)이라 앞사람 처리 여부와 무관하게 유입 속도 자체가 독립 변수다.
 * ramping-arrival-rate executor로 "초당 몇 명이 새로 enter()를 시도하는가"를
 * 직접 통제해서, 짧은 시간에 대기열이 수천 단위로 쌓였다가 서서히 빠지는 걸 재현한다.
 *
 * 목적이 다르므로 threshold도 다르다: 정합성 관련(goal 오검증, me() 이상응답,
 * leave 실패)만 count==0으로 강하게 검증하고, 지연시간/타임아웃은 의도적으로
 * 과부하를 주는 시나리오라 관찰만 하고 강제 실패시키지 않는다.
 *
 * 실행 전 서버 설정은 smoke-queue.js와 동일 (performance-ids, target-capacity=500 등).
 * 이 시나리오는 capacity를 안 건드리고 "유입 속도"만 폭발시켜서 자연스럽게
 * 대기열이 쌓이는 그림을 만드는 게 목적이다.
 *
 * 주의: 피크 시점에 수천 개의 VU가 동시에 폴링 루프에 머무를 수 있어
 * 로컬 머신 리소스(메모리/파일 디스크립터)를 smoke-queue.js보다 훨씬 많이 씀.
 *
 * 실행:
 *   k6 run -e BASE_URL=http://localhost:8080 -e PERFORMANCE_ID=1 open-run-burst.js
 */

import http from 'k6/http';
import {check, sleep} from 'k6';
import exec from 'k6/execution';
import {Counter, Trend} from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PERFORMANCE_ID = __ENV.PERFORMANCE_ID || '1';

const POLL_INTERVAL_SECONDS = 1;
// 대기열이 수천 단위로 쌓이니 개인 대기시간도 길어질 수 있음. smoke-queue.js보다 넉넉하게.
const ADMISSION_TIMEOUT_MS = 180_000;
const DWELL_MIN_SECONDS = 1;
const DWELL_MAX_SECONDS = 5;
const LEAVE_PROBABILITY = 0.9;

const loginDuration = new Trend('rushmore_login_duration');
const enterDuration = new Trend('rushmore_enter_duration');
const timeToAdmission = new Trend('rushmore_time_to_admission');
const goalDuration = new Trend('rushmore_goal_duration');
const admittedTotal = new Counter('rushmore_admitted_total');
const admissionTimeoutTotal = new Counter('rushmore_admission_timeout_total'); // 관찰용, threshold 없음
const goalRejectedTotal = new Counter('rushmore_goal_rejected_total');
const abandonedTotal = new Counter('rushmore_abandoned_total');
const meUnexpectedTotal = new Counter('rushmore_me_unexpected_total');
const leaveFailedTotal = new Counter('rushmore_leave_failed_total');

export const options = {
    scenarios: {
        open_run_burst: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            // 넉넉하게 잡아야 함: 대기열이 쌓이면 폴링 중인 VU가 오래 붙잡혀 있어서
            // maxVUs가 부족하면 k6가 유입 자체를 드롭한다(dropped_iterations로 확인 가능).
            // preAllocatedVUs를 maxVUs에 가깝게 잡은 이유: 10초 만에 초당 1200명까지 치솟는
            // 급격한 램프에서는 k6가 필요할 때마다 VU를 새로 만드는 속도가 유입 속도를
            // 못 따라가서, maxVUs까지 다 쓰기도 전에 드롭이 나는 경우가 있었음.
            preAllocatedVUs: 15000,
            maxVUs: 20000,
            stages: [
                { target: 1200, duration: '10s' }, // 0 → 초당 1200명까지 램프업 (대략 6천명 유입)
                { target: 0, duration: '5s' },     // 뚝 끊김 — 오픈런 종료, 나머지 대략 3천명
            ],
            // ADMISSION_TIMEOUT_MS(3분) + dwell + 여유. 이게 짧으면 아직 폴링 중인
            // VU가 강제로 끊기고, 서버는 그 memberId를 계속 admit 시도하면서
            // 점유 슬롯을 TTL 만료 전까지 붙든 채로 "유령"이 남는다.
            gracefulStop: '4m',
        },
    },
    thresholds: {
        // 정합성 관련만 강하게 검증. 의도적 과부하라 지연/타임아웃은 여기서 실패 처리 안 함.
        rushmore_goal_rejected_total: ['count==0'],
        rushmore_me_unexpected_total: ['count==0'],
        rushmore_leave_failed_total: ['count==0'],
    },
};

function randomBetween(min, max) {
    return min + Math.random() * (max - min);
}

function nextMemberId() {
    // ramping-arrival-rate에서는 VU가 재사용되므로 iterationInInstance까지 섞어 충돌 방지
    return 10_000_000 + exec.vu.idInTest * 1_000 + exec.vu.iterationInInstance;
}

export default function () {
    const memberId = nextMemberId();

    const loginRes = http.post(
        `${BASE_URL}/internal/auth/access-tokens`,
        JSON.stringify({ memberId }),
        { headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } },
    );
    loginDuration.add(loginRes.timings.duration);
    if (!check(loginRes, { 'login: 200': (r) => r.status === 200 })) return;

    const accessToken = loginRes.json('accessToken');
    const authHeaders = { headers: { Authorization: `Bearer ${accessToken}` } };

    const enterRes = http.post(
        `${BASE_URL}/performances/${PERFORMANCE_ID}/queue`,
        null,
        { ...authHeaders, tags: { step: 'enter' } },
    );
    enterDuration.add(enterRes.timings.duration);
    if (!check(enterRes, { 'enter: 200': (r) => r.status === 200 })) return;

    const pollStart = Date.now();
    let admissionToken = null;

    while (Date.now() - pollStart < ADMISSION_TIMEOUT_MS) {
        const meRes = http.get(
            `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/me`,
            { ...authHeaders, tags: { step: 'me' } },
        );

        const meBody = meRes.status === 200 ? meRes.json() : null;
        const meOk = check(meRes, {
            'me: 200 with WAITING or ADMITTED': () =>
                meBody !== null && (meBody.status === 'WAITING' || meBody.status === 'ADMITTED'),
        });

        if (!meOk) {
            meUnexpectedTotal.add(1);
        } else if (meBody.status === 'ADMITTED') {
            admissionToken = meBody.admissionToken;
            break;
        }
        sleep(POLL_INTERVAL_SECONDS);
    }

    if (!admissionToken) {
        admissionTimeoutTotal.add(1);
        return;
    }
    timeToAdmission.add(Date.now() - pollStart);
    admittedTotal.add(1);

    const goalRes = http.post(
        `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/goal`,
        null,
        {
            headers: { Authorization: `Bearer ${accessToken}`, 'X-Admission-Token': admissionToken },
            tags: { step: 'goal' },
        },
    );
    goalDuration.add(goalRes.timings.duration);
    if (!check(goalRes, { 'goal: 200': (r) => r.status === 200 })) {
        goalRejectedTotal.add(1);
        return;
    }

    sleep(randomBetween(DWELL_MIN_SECONDS, DWELL_MAX_SECONDS));

    if (Math.random() < LEAVE_PROBABILITY) {
        const leaveRes = http.post(
            `${BASE_URL}/performances/${PERFORMANCE_ID}/queue/leave`,
            null,
            { ...authHeaders, tags: { step: 'leave' } },
        );
        if (!check(leaveRes, { 'leave: 200': (r) => r.status === 200 })) leaveFailedTotal.add(1);
    } else {
        abandonedTotal.add(1);
    }
}