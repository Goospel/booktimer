// affiliate-report.mjs 의 순수 집계함수 summarize() 단위테스트.
// 라이브 auth_key 없이 문서 샘플 스키마로 돈 계산 로직(합계·상태별·취소제외)을 못 박는다.
// 실행: node --test .claude/scripts/tests/affiliate-report.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { summarize, formatReport, secretPathFor, classifyResult } from '../affiliate-report.mjs';

test('빈 order_list → 0 요약', () => {
  const s = summarize([]);
  assert.equal(s.count, 0);
  assert.equal(s.totalSales, 0);
  assert.equal(s.totalCommission, 0);
  assert.equal(s.activeSales, 0);
  assert.equal(s.activeCommission, 0);
  assert.deepEqual(s.byStatus, {});
  assert.deepEqual(s.byMerchant, {});
});

test('단일 정상(100) 거래 합산 + merchant 집계', () => {
  const s = summarize([{ m_id: 'yes24', sales: 39900, commission: 1197, status: '100' }]);
  assert.equal(s.count, 1);
  assert.equal(s.totalSales, 39900);
  assert.equal(s.totalCommission, 1197);
  assert.equal(s.activeCommission, 1197); // 100은 취소 아님 → active 포함
  assert.equal(s.byStatus['100'].count, 1);
  assert.equal(s.byStatus['100'].commission, 1197);
  assert.equal(s.byStatus['100'].label, '정상');
  assert.equal(s.byMerchant['yes24'].sales, 39900);
});

test('취소(300·310)는 total엔 포함하되 active(취소제외)에서 빠진다', () => {
  const s = summarize([
    { m_id: 'yes24', sales: 10000, commission: 300, status: '210' }, // 정산완료
    { m_id: 'yes24', sales: 20000, commission: 600, status: '200' }, // 정산대기
    { m_id: 'yes24', sales: 5000, commission: 150, status: '300' },  // 취소신청
    { m_id: 'yes24', sales: 7000, commission: 210, status: '310' },  // 취소완료
  ]);
  assert.equal(s.count, 4);
  assert.equal(s.totalSales, 42000);
  assert.equal(s.totalCommission, 1260);
  // active = 210 + 200 만 (300·310 제외)
  assert.equal(s.activeSales, 30000);
  assert.equal(s.activeCommission, 900);
  assert.equal(s.byStatus['300'].count, 1);
  assert.equal(s.byStatus['300'].label, '취소신청');
  assert.equal(s.byStatus['310'].commission, 210);
  assert.equal(s.byStatus['310'].label, '취소완료');
  assert.equal(s.byStatus['210'].label, '정산완료');
  assert.equal(s.byStatus['200'].label, '정산대기');
});

test('sales/commission가 문자열이어도 숫자로 합산(방어적 캐스팅)', () => {
  const s = summarize([
    { m_id: 'yes24', sales: '39900', commission: '1197', status: '100' },
    { m_id: 'yes24', sales: '10000', commission: '300', status: '100' },
  ]);
  assert.equal(s.totalSales, 49900);
  assert.equal(s.totalCommission, 1497);
});

test('merchant별 그룹 분해', () => {
  const s = summarize([
    { m_id: 'yes24', sales: 10000, commission: 300, status: '100' },
    { m_id: 'yes24', sales: 20000, commission: 600, status: '100' },
    { m_id: 'clickbuy', sales: 5000, commission: 150, status: '100' },
  ]);
  assert.equal(Object.keys(s.byMerchant).length, 2);
  assert.equal(s.byMerchant['yes24'].count, 2);
  assert.equal(s.byMerchant['yes24'].sales, 30000);
  assert.equal(s.byMerchant['clickbuy'].commission, 150);
});

test('숫자 status(문서 예시는 문자열이나 방어)도 문자열 키로 정규화', () => {
  const s = summarize([{ m_id: 'yes24', sales: 1000, commission: 30, status: 100 }]);
  assert.equal(s.byStatus['100'].count, 1);
});

test('알 수 없는 status 코드도 집계·라벨 폴백', () => {
  const s = summarize([{ m_id: 'yes24', sales: 1000, commission: 30, status: '999' }]);
  assert.equal(s.byStatus['999'].count, 1);
  assert.ok(s.byStatus['999'].label && s.byStatus['999'].label.length > 0);
});

// 비밀키 경로 불변식 — 반드시 gitignored 위치(.claude/.secrets/)에서 읽어야 커밋 사고를 막는다.
// (스크립트는 .claude/scripts/ 에 있으므로 상위 .claude/.secrets/ 로 올라가야 함. 이 불변식이
//  깨지면 코드가 읽는 경로와 .gitignore 가 어긋나 조용히 실패하거나 키가 커밋된다.)
test('secretPathFor: 비밀키는 gitignored 위치 .claude/.secrets/ 에서 읽는다 (scripts/ 밑 아님)', () => {
  const p = secretPathFor('/repo/.claude/scripts').replaceAll('\\', '/');
  assert.ok(
    p.endsWith('/.claude/.secrets/linkprice-auth'),
    `비밀키 경로가 .claude/.secrets/linkprice-auth 로 끝나야 함(gitignore 일치): 실제=${p}`,
  );
  assert.ok(
    !p.includes('/scripts/'),
    `비밀키 경로가 scripts/ 밑이면 .gitignore(.claude/.secrets/) 밖이라 커밋 위험: 실제=${p}`,
  );
});

// 사용자 대면 출력 경로 스모크 — 정확한 레이아웃은 브리틀이라 안 박고 "크래시 없이 문자열을 낸다"만 방어.
test('formatReport: 빈 요약도 문자열 반환(크래시 없음, 기간 포함)', () => {
  const out = formatReport(summarize([]), { period: '202607', aId: 'A100705638' });
  assert.equal(typeof out, 'string');
  assert.ok(out.includes('202607'));
  assert.ok(out.includes('실적 없음'));
});

test('formatReport: 실적 있는 요약도 문자열 반환(금액·상태 라벨 포함)', () => {
  const out = formatReport(summarize([
    { m_id: 'yes24', sales: 39900, commission: 1197, status: '210' },
    { m_id: 'clickbuy', sales: 10000, commission: 300, status: '310' },
  ]), { period: '20260704', aId: 'A100705638' });
  assert.equal(typeof out, 'string');
  assert.ok(out.includes('정산완료'));
  assert.ok(out.includes('취소완료'));
  assert.ok(out.includes('머천트별')); // 2개 머천트 → 분해 노출
});

// result 코드 3분류 — 이 불변식이 양방향으로 중요:
//  ① 101(실적 0건)을 error로 두면 데이터 없을 때마다 "❌ API 오류"라는 오해 메시지가 뜬다.
//  ② 반대로 실제 설정 오류(100 a_id·300 인증키 등)를 no-data로 삼키면 깨진 설정이 조용히 "실적 없음"으로 숨는다.
test('classifyResult: 0=ok · 101=no-data(실적0건) · 실제 오류코드는 error 유지', () => {
  assert.equal(classifyResult('0'), 'ok');
  assert.equal(classifyResult('101'), 'no-data');
  assert.equal(classifyResult(101), 'no-data'); // 숫자 응답도 정규화
  // 실제 오류는 삼키지 않고 error로 — 무데이터로 감추면 설정 버그가 침묵한다
  assert.equal(classifyResult('100'), 'error'); // a_id 없음
  assert.equal(classifyResult('200'), 'error'); // 조회일자 없음
  assert.equal(classifyResult('210'), 'error'); // 조회일자 길이 오류
  assert.equal(classifyResult('300'), 'error'); // auth_key 불일치
  assert.equal(classifyResult('400'), 'error'); // 통화 미지원
  assert.equal(classifyResult(undefined), 'error'); // 빈 응답
});
