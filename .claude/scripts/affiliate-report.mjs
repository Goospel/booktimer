#!/usr/bin/env node
// LinkPrice 어필리에이트 실적 조회 (YES24) — 무인 조회 스크립트.
// api.linkprice.com/affiliate/translist.php 를 호출해 거래 배열(order_list)을 집계·요약한다.
//
// 사용:
//   node .claude/scripts/affiliate-report.mjs                 # 이번 달(YYYYMM)
//   node .claude/scripts/affiliate-report.mjs --date 20260704 # 특정 일자(YYYYMMDD)
//   node .claude/scripts/affiliate-report.mjs --date 202607    # 특정 월(YYYYMM)
//   node .claude/scripts/affiliate-report.mjs --test          # API test=Y 스모크(테스트 실적 10건)
//   node .claude/scripts/affiliate-report.mjs --merchant <id> # 특정 머천트만
//   node .claude/scripts/affiliate-report.mjs --json          # 집계 JSON 원본 출력
//
// auth_key(인증키): 환경변수 LINKPRICE_AUTH_KEY 우선, 없으면
//   gitignored 파일 .claude/.secrets/linkprice-auth (한 줄) 에서 읽는다.
//   키는 절대 커밋·로그에 남기지 않는다.
// a_id: 환경변수 AFFILIATE_A_ID, 기본값 A100705638.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const STATUS_LABELS = {
  '100': '정상',
  '200': '정산대기',
  '210': '정산완료',
  '300': '취소신청',
  '310': '취소완료',
};
const CANCELLED = new Set(['300', '310']); // active(취소제외) 계산에서 뺀다

const DEFAULT_A_ID = 'A100705638';
const API_URL = 'https://api.linkprice.com/affiliate/translist.php';

// translist.php result=101 "정상 page 번호 아님" — 실적 0건이면 반환할 page가 없어 이 코드가 온다.
// 에러가 아니라 "데이터 없음" 신호로 다룬다(가이드 실적_조회_오픈_API_v1.6). 100 a_id·200/210 날짜·300 인증키·400 통화는 실제 오류.
const NO_DATA_RESULT = '101';

function num(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

/**
 * translist.php의 result 코드를 3분류. (순수 함수 — 단위테스트 대상)
 *   'ok'      = '0'   (정상 — 데이터 있음)
 *   'no-data' = '101' (유효 page 없음 = 실적 0건 / 페이지 끝) — 에러 아님
 *   'error'   = 그 외 (100 a_id 없음·200/210 날짜·300 인증키·400 통화 등 실제 오류)
 */
export function classifyResult(result) {
  const r = String(result ?? '');
  if (r === '0') return 'ok';
  if (r === NO_DATA_RESULT) return 'no-data';
  return 'error';
}

/**
 * 거래 배열(order_list)을 합계·상태별·머천트별로 집계한다. (순수 함수 — 단위테스트 대상)
 *   total*  = 전체 합계(취소 포함)
 *   active* = 취소(300·310) 제외 합계 (실질 예상 수익)
 */
export function summarize(orderList) {
  const s = {
    count: 0,
    totalSales: 0,
    totalCommission: 0,
    activeSales: 0,
    activeCommission: 0,
    byStatus: {},
    byMerchant: {},
  };
  for (const o of orderList ?? []) {
    const sales = num(o.sales);
    const commission = num(o.commission);
    const status = String(o.status ?? '');
    const merchant = String(o.m_id ?? '(unknown)');

    s.count += 1;
    s.totalSales += sales;
    s.totalCommission += commission;
    if (!CANCELLED.has(status)) {
      s.activeSales += sales;
      s.activeCommission += commission;
    }

    const st = (s.byStatus[status] ??= {
      label: STATUS_LABELS[status] ?? `기타(${status})`,
      count: 0, sales: 0, commission: 0,
    });
    st.count += 1; st.sales += sales; st.commission += commission;

    const mm = (s.byMerchant[merchant] ??= { count: 0, sales: 0, commission: 0 });
    mm.count += 1; mm.sales += sales; mm.commission += commission;
  }
  return s;
}

// ───────────────────────── CLI (부수효과 — 테스트 대상 아님) ─────────────────────────

/**
 * 비밀키 파일 경로를 스크립트 디렉터리 기준으로 해석한다. (순수 함수 — 단위테스트 대상)
 * 스크립트는 `.claude/scripts/` 에 있고 비밀 파일은 `.claude/.secrets/` 에 둔다 —
 * 그래야 `.gitignore` 의 `.claude/.secrets/` 로 커버돼 커밋 사고가 원천 차단된다.
 * 따라서 스크립트 폴더의 **상위**(`..`)로 올라가 `.secrets/` 를 잡는다.
 */
export function secretPathFor(scriptDir) {
  return resolve(scriptDir, '..', '.secrets', 'linkprice-auth');
}

function readAuthKey() {
  const env = process.env.LINKPRICE_AUTH_KEY;
  if (env && env.trim()) return env.trim();
  const here = dirname(fileURLToPath(import.meta.url));
  const secretPath = secretPathFor(here);
  try {
    const v = readFileSync(secretPath, 'utf8').trim();
    if (v) return v;
  } catch { /* 파일 없음 → 호출부에서 안내 */ }
  return null;
}

function parseArgs(argv) {
  const args = { date: null, test: false, merchant: null, json: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--date') args.date = argv[++i];
    else if (a === '--test') args.test = true;
    else if (a === '--merchant') args.merchant = argv[++i];
    else if (a === '--json') args.json = true;
  }
  return args;
}

function currentYyyymm() {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function fmtWon(n) {
  return `${n.toLocaleString('ko-KR')}원`;
}

export function formatReport(summary, { period, aId }) {
  const lines = [];
  lines.push(`📊 LinkPrice 실적 — a_id=${aId} · 기간 ${period}`);
  lines.push('─'.repeat(48));
  lines.push(`거래건수         ${summary.count}건`);
  lines.push(`판매액(전체)     ${fmtWon(summary.totalSales)}`);
  lines.push(`커미션(전체)     ${fmtWon(summary.totalCommission)}`);
  lines.push(`커미션(취소제외) ${fmtWon(summary.activeCommission)}  ← 실질 예상 수익`);
  if (summary.count > 0) {
    lines.push('', '상태별:');
    for (const code of ['100', '200', '210', '300', '310']) {
      const st = summary.byStatus[code];
      if (st) lines.push(`  ${st.label.padEnd(6)} ${st.count}건 · 커미션 ${fmtWon(st.commission)}`);
    }
    for (const [code, st] of Object.entries(summary.byStatus)) {
      if (!STATUS_LABELS[code]) lines.push(`  ${st.label} ${st.count}건 · 커미션 ${fmtWon(st.commission)}`);
    }
    const merchants = Object.entries(summary.byMerchant);
    if (merchants.length > 1) {
      lines.push('', '머천트별:');
      for (const [mid, mm] of merchants) {
        lines.push(`  ${mid.padEnd(12)} ${mm.count}건 · 판매 ${fmtWon(mm.sales)} · 커미션 ${fmtWon(mm.commission)}`);
      }
    }
  } else {
    lines.push('', '(해당 기간 실적 없음 — 자가 클릭·구매는 실적에서 제외됩니다)');
  }
  return lines.join('\n');
}

async function fetchPage({ aId, authKey, date, test, merchant, page }) {
  const u = new URL(API_URL);
  u.searchParams.set('a_id', aId);
  u.searchParams.set('auth_key', authKey);
  u.searchParams.set('yyyymmdd', date);
  if (test) u.searchParams.set('test', 'Y');
  if (merchant) u.searchParams.set('merchant_id', merchant);
  u.searchParams.set('per_page', '1000');
  u.searchParams.set('page', String(page));
  const res = await fetch(u, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
  return res.json();
}

async function fetchAllOrders(opts) {
  const all = [];
  for (let page = 1; page <= 100; page++) { // page>100 안전 상한
    const data = await fetchPage({ ...opts, page });
    const kind = classifyResult(data.result);
    // no-data(101) = 유효 page 없음 = 실적 0건(또는 페이지 끝) → 지금까지 모은 것으로 정상 종료
    if (kind === 'no-data') return { result: '0', orders: all, listCount: all.length };
    // 실제 오류(a_id·날짜·인증키·통화)는 코드를 그대로 올려 호출부가 안내
    if (kind === 'error') return { result: String(data.result ?? ''), orders: [], listCount: 0 };
    const orders = data.order_list ?? [];
    all.push(...orders);
    const listCount = num(data.list_count);
    if (all.length >= listCount || orders.length === 0) {
      return { result: '0', orders: all, listCount };
    }
  }
  return { result: '0', orders: all, listCount: all.length };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const aId = (process.env.AFFILIATE_A_ID || DEFAULT_A_ID).trim();
  const authKey = readAuthKey();
  if (!authKey) {
    console.error([
      '❌ auth_key(인증키)가 없습니다.',
      '   LinkPrice 어필리에이트 담당자에게 "실적 조회 오픈 API 인증키" 발급을 요청하세요.',
      '   발급 후 둘 중 하나로 등록:',
      '     - 환경변수:  LINKPRICE_AUTH_KEY=<키>',
      '     - 파일:      .claude/.secrets/linkprice-auth  (한 줄, gitignored)',
    ].join('\n'));
    process.exit(2);
  }
  const date = args.date || currentYyyymm();
  let fetched;
  try {
    fetched = await fetchAllOrders({ aId, authKey, date, test: args.test, merchant: args.merchant });
  } catch (e) {
    console.error(`❌ API 호출 실패: ${e.message}`);
    process.exit(1);
  }
  if (fetched.result !== '0') {
    console.error([
      `❌ API 오류 (result=${fetched.result}).`,
      '   흔한 원인: 잘못된 auth_key/a_id, yyyymmdd 형식 오류, 권한 미부여.',
    ].join('\n'));
    process.exit(1);
  }
  const summary = summarize(fetched.orders);
  console.log(args.json ? JSON.stringify(summary, null, 2) : formatReport(summary, { period: date, aId }));
}

// CLI로 직접 실행될 때만 main() 수행 (테스트가 import 할 때는 안 돎).
const invokedDirectly = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedDirectly) main();
