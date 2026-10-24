// AI 안내 · 동의 시트 문구(v1 §5.2.1 그대로 — REQ-39 가드 대상, 한 글자도 바꾸지 않는다). 화면은 이 상수만 쓴다.
// 항목 앞의 「· 」는 화면이 점으로 그린다. 항목 이름(첫 「: 」까지)의 굵기도 화면이 나눠 그린다 — 글자는 그대로다.

export const CONSENT_TITLE = 'AI 질문을 쓰기 전에 알려 드려요';

export const CONSENT_ITEMS: readonly string[] = [
  '질문은 생성형 AI(Anthropic의 Claude)가 만들어요. AI는 글을 대신 쓰지 않고, 내 글을 고치지도 않아요 — 질문만 해요.',
  '보내는 것: 이 책의 제목·저자, 여백(옮긴 문장·메모·작성일), 지금 쓰는 글, 지금 답하는 질문과 이 글에서 앞서 받은 질문',
  '보내지 않는 것: 이메일·아이디·닉네임 같은 계정 정보, 독서 시간 기록, 공부 서재의 필기·백지복습',
  '받는 곳: Anthropic PBC(미국) · privacy@anthropic.com — 질문을 만드는 데만 써요',
  '언제·어떻게: 「질문 받기」「꼬리 질문」을 누를 때마다 암호화(HTTPS)해서 보내요',
  '보관: Anthropic은 받은 날부터 30일 안에 지워요. 다만 이용정책 위반으로 표시된 건은 최대 2년까지 보관될 수 있어요',
  '동의하지 않아도 돼요: AI 질문만 받을 수 없고 타이머·서재·여백·글쓰기는 그대로예요. 동의는 설정에서 언제든 거둘 수 있어요',
];

/** 제28조의8 ② 다섯 항목 + AI 고지(v1 REQ-39). */
export const CONSENT_LITERALS: readonly string[] = [
  'Anthropic',
  '미국',
  'privacy@anthropic.com',
  '누를 때',
  'HTTPS',
  '30일',
  '최대 2년',
  '동의하지 않아도',
  '거둘 수',
  '대신 쓰지 않',
  '질문만 해요',
];

export function missingLiterals(text: string): string[] {
  return CONSENT_LITERALS.filter((l) => !text.includes(l));
}

export const CONSENT_BUTTONS = { agree: '동의하고 계속', later: '나중에', privacy: '처리방침 자세히' } as const;

/** 항목 이름(첫 「: 」까지)과 나머지 — 첫 항목은 이름이 없다. 이어 붙이면 원문 그대로다. */
export function splitItem(item: string): { name: string; rest: string } {
  const at = item.indexOf(': ');
  return at < 0 ? { name: '', rest: item } : { name: item.slice(0, at + 2), rest: item.slice(at + 2) };
}
