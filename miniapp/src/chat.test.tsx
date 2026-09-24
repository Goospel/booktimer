import { readFileSync } from 'node:fs';

import { TDSMobileProvider } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatMessage, ChatRoomSummary, ProfileResponse } from './api';
import { ApiError, NetworkError } from './api';
import {
  CHAT_PAGE_LIMIT,
  INBOX_POLL_MS,
  InboxView,
  ROOM_POLL_MS,
  RoomView,
  initialChat,
  lockText,
  mergeMessages,
  needsCatchUp,
  pollIntervalMs,
  roomGone,
  sanctionText,
} from './screens/Chat';
import { BookshopHeader } from './screens/Bookshop';
import { ProfileCard, canMessage } from './screens/Profile';
import { bigShadow, tagWith } from './soft-guard';
import { userAgent } from './test-fixtures';

/**
 * 맞팔 DM 미니앱 화면(설계 §5-4·§6 PR-3). 하니스가 정적 렌더라 폴링·클릭·effect는 안 돈다(T-149) —
 * 판정은 순수 함수로 꺼내 전수로 재고, 렌더는 「그 판정이 마크업에 실제로 걸렸는가」만 본다.
 */

vi.mock('./toss', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./toss')>()),
  notificationAgreementSupported: vi.fn(() => true),
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

function render(node: ReactNode) {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

function message(id: number, extra: Partial<ChatMessage> = {}): ChatMessage {
  return { id, mine: false, body: `${id}번 메시지`, flagged: false, createdAt: '2026-09-19T03:00:00Z', ...extra };
}

function room(roomId: number, extra: Partial<ChatRoomSummary> = {}): ChatRoomSummary {
  return {
    roomId,
    partner: { loginId: `user${roomId}`, nickname: `상대${roomId}` },
    writable: true,
    lockReason: null,
    lastMessage: { body: `마지막 ${roomId}`, mine: false, createdAt: '2026-09-19T03:00:00Z' },
    unread: 0,
    lastMessageId: roomId * 10,
    ...extra,
  };
}

describe('폴링 간격 (pollIntervalMs)', () => {
  it('보이는 방에서 보낼 수 있으면 3초', () => {
    expect(pollIntervalMs(true, true)).toBe(3_000);
    expect(ROOM_POLL_MS).toBe(3_000);
  });

  it('잠긴 방은 대화함과 같은 10초 — 양쪽 다 못 보내니 풀렸는지만 본다', () => {
    expect(pollIntervalMs(true, false)).toBe(10_000);
    expect(INBOX_POLL_MS).toBe(10_000);
  });

  it('문서가 숨겨지면 멈춘다(null) — 보낼 수 있든 없든', () => {
    expect(pollIntervalMs(false, true)).toBeNull();
    expect(pollIntervalMs(false, false)).toBeNull();
  });
});

describe('메시지 합치기 (mergeMessages)', () => {
  it('새로 온 것을 뒤에 붙인다', () => {
    expect(mergeMessages([message(1)], [message(2), message(3)]).map((m) => m.id)).toEqual([1, 2, 3]);
  });

  it('같은 id는 한 번만 — 폴링이 겹쳐도 말풍선이 두 번 서지 않는다', () => {
    expect(mergeMessages([message(1), message(2)], [message(2), message(3)]).map((m) => m.id)).toEqual([1, 2, 3]);
  });

  it('도착 순서가 뒤섞여도 id 오름차순이다', () => {
    expect(mergeMessages([message(5)], [message(3), message(4)]).map((m) => m.id)).toEqual([3, 4, 5]);
  });

  it('빈 쪽이 있어도 그대로다', () => {
    expect(mergeMessages([], [message(1)]).map((m) => m.id)).toEqual([1]);
    expect(mergeMessages([message(1)], []).map((m) => m.id)).toEqual([1]);
    expect(mergeMessages([], [])).toEqual([]);
  });

  it('같은 id면 새로 받은 것이 이긴다', () => {
    expect(mergeMessages([message(1, { body: '옛' })], [message(1, { body: '새' })])[0].body).toBe('새');
  });
});

describe('잠김 문구 (lockText)', () => {
  it('보낼 수 있으면(null) 문구가 없다', () => {
    expect(lockText(null)).toBeNull();
  });

  it('서버 사유마다 다른 말을 한다', () => {
    expect(lockText('NOT_MUTUAL')).toBe('맞팔이 풀려 대화가 잠겼어요');
    expect(lockText('BLOCKED')).toBe('대화할 수 없는 상대예요');
    expect(lockText('UNREACHABLE')).toBe('상대가 아직 앱에서 대화를 쓸 수 없어요');
    expect(lockText('RESTRICTED')).toBe('지금은 대화를 보낼 수 없어요');
    expect(lockText('CLOSED')).toBe('종료된 대화예요');
  });

  it('모르는 사유도 잠김은 잠김이다 — 입력이 열린 것처럼 보이지 않게', () => {
    expect(lockText('SOMETHING_NEW' as never)).toBe('지금은 대화를 보낼 수 없어요');
  });
});

describe('딥링크 (initialChat)', () => {
  it('?chat=inbox면 대화함', () => {
    expect(initialChat('?chat=inbox')).toBe('inbox');
    expect(initialChat('?utm_source=toss&chat=inbox')).toBe('inbox');
  });

  it('없거나 모르는 값이면 null — 기본 화면으로 간다', () => {
    expect(initialChat('')).toBeNull();
    expect(initialChat('?chat=')).toBeNull();
    expect(initialChat('?chat=room')).toBeNull();
    expect(initialChat('?tab=history')).toBeNull();
  });
});

describe('제재 문구 (sanctionText)', () => {
  const NOW = Date.parse('2026-09-19T00:00:00Z');

  it('제재가 없거나 모르면 없다', () => {
    expect(sanctionText(null, NOW)).toBeNull();
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: null, banned: false }, NOW)).toBeNull();
  });

  it('영구 정지', () => {
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: null, banned: true }, NOW)).toBe(
      '대화 기능이 정지됐어요. 새 메시지를 보낼 수 없어요.',
    );
  });

  it('일시 정지는 끝나는 날짜를 말한다', () => {
    const text = sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-26T00:00:00Z', banned: false }, NOW);
    expect(text).toContain('9월 26일');
    expect(text).toContain('보낼 수 없어요');
  });

  it('이미 끝난 정지는 말하지 않는다(경계: 지금과 같으면 끝난 것)', () => {
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-18T00:00:00Z', banned: false }, NOW)).toBeNull();
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-19T00:00:00Z', banned: false }, NOW)).toBeNull();
  });
});

function roomView(extra: Partial<Parameters<typeof RoomView>[0]> = {}) {
  return render(
    <RoomView
      partner={{ loginId: 'nabi', nickname: '나비독서' }}
      messages={[message(1), message(2, { mine: true })]}
      writable
      lockReason={null}
      draft=""
      busy={false}
      error={null}
      notice={null}
      safety={null}
      onDraft={() => {}}
      onSend={() => {}}
      onReport={() => {}}
      onBlock={() => {}}
      onLeave={() => {}}
      {...extra}
    />,
  );
}

describe('대화방 (RoomView)', () => {
  it('상단에 신고·차단·나가기가 있다', () => {
    const html = roomView();
    expect(html).toContain('신고');
    expect(html).toContain('차단');
    expect(html).toContain('나가기');
  });

  it('열린 방은 잠김 문구가 없고 입력이 살아 있다(대조군)', () => {
    const html = roomView({ draft: '안녕' });
    expect(html).not.toContain('대화가 잠겼어요');
    expect(html).not.toMatch(/<textarea[^>]*disabled/);
  });

  it('잠긴 방은 사유를 말하고 입력·보내기를 막는다', () => {
    const html = roomView({ writable: false, lockReason: 'NOT_MUTUAL', draft: '안녕' });
    expect(html).toContain('맞팔이 풀려 대화가 잠겼어요');
    expect(html).toMatch(/<textarea[^>]*disabled/);
    expect(html).toMatch(/<button[^>]*disabled[^>]*>(?:(?!<\/button>).)*보내기/s);
  });

  it('상대의 표시된(flagged) 메시지가 있으면 안내 배너를 띄운다', () => {
    const html = roomView({ messages: [message(1, { flagged: true })] });
    expect(html).toContain('연락처·외부 링크·금전을 요구하는 상대는 신고해 주세요');
  });

  it('내가 보낸 표시 메시지뿐이면 배너가 없다 — 배너는 수신자에게만(대조군)', () => {
    const html = roomView({ messages: [message(1), message(2, { mine: true, flagged: true })] });
    expect(html).not.toContain('신고해 주세요');
  });

  it('복호화 못 한 본문(null)은 빈 말풍선 대신 안내로 선다', () => {
    const html = roomView({ messages: [message(1, { body: null })] });
    expect(html).toContain('표시할 수 없는 메시지예요');
  });

  it('메시지가 없으면 첫 인사를 권한다', () => {
    expect(roomView({ messages: [] })).toContain('첫 메시지를 보내 보세요');
  });

  /** 보내는 중(busy)에 입력창을 disabled로 만들면 포커스가 빠져 모바일 키보드가 내려간다 — 연타 방지는 버튼만. */
  it('보내는 중엔 보내기만 막고 입력창은 살려 둔다', () => {
    const html = roomView({ busy: true, draft: '안녕' });
    expect(html).not.toMatch(/<textarea[^>]*disabled/);
    expect(html).toMatch(/<button[^>]*disabled[^>]*>(?:(?!<\/button>).)*보내기/s);
  });

  it('첫 응답 전(loading)엔 입력창을 막고 빈 방 안내도 띄우지 않는다', () => {
    const html = roomView({ messages: [], loading: true });
    expect(html).toMatch(/<textarea[^>]*disabled/);
    expect(html).not.toContain('첫 메시지를 보내 보세요');
  });
});

describe('방 조회 판정', () => {
  it('서버 상한(200건)을 꽉 채워 받으면 곧바로 한 번 더 받는다', () => {
    expect(CHAT_PAGE_LIMIT).toBe(200);
    expect(needsCatchUp(200)).toBe(true);
    expect(needsCatchUp(199)).toBe(false);
    expect(needsCatchUp(0)).toBe(false);
  });

  it('404면 방이 사라진 것이다 — 폴링을 멈추고 대화함으로', () => {
    expect(roomGone(new ApiError(404, '대화를 찾을 수 없어요.'))).toBe(true);
    expect(roomGone(new ApiError(403, '서로 팔로우해야 메시지를 보낼 수 있어요.'))).toBe(false);
    expect(roomGone(new ApiError(429, ''))).toBe(false);
    expect(roomGone(new NetworkError())).toBe(false);
  });
});

function inbox(extra: Partial<Parameters<typeof InboxView>[0]> = {}) {
  return render(
    <InboxView
      rooms={[]}
      me={null}
      now={Date.parse('2026-09-19T00:00:00Z')}
      error={null}
      showAgreement={false}
      onAgree={() => {}}
      onOpen={() => {}}
      {...extra}
    />,
  );
}

describe('대화함 (InboxView)', () => {
  it('상대 이름·마지막 메시지·미읽음 수를 보여 준다', () => {
    const html = inbox({ rooms: [room(1, { unread: 3 })] });
    expect(html).toContain('상대1');
    expect(html).toContain('마지막 1');
    expect(html).toContain('>3<');
  });

  it('미읽음이 0이면 배지가 없다(대조군)', () => {
    expect(inbox({ rooms: [room(1, { unread: 0 })] })).not.toContain('aria-label="읽지 않은 메시지');
    expect(inbox({ rooms: [room(1, { unread: 2 })] })).toContain('aria-label="읽지 않은 메시지 2개"');
  });

  it('잠긴 방은 목록에서도 잠김을 말한다', () => {
    const html = inbox({ rooms: [room(1, { writable: false, lockReason: 'NOT_MUTUAL' })] });
    expect(html).toContain('잠김');
  });

  it('열린 방에는 잠김 표시가 없다(대조군)', () => {
    expect(inbox({ rooms: [room(1)] })).not.toContain('잠김');
  });

  it('빈 대화함은 여는 법을 말한다', () => {
    expect(inbox()).toContain('서로 팔로우한 친구의 책방에서 메시지를 보낼 수 있어요');
  });

  it('알림 동의는 화면 안 카드다 — 켜졌을 때만', () => {
    expect(inbox({ showAgreement: true })).toContain('새 메시지를 토스 알림으로 받아보세요');
    expect(inbox({ showAgreement: false })).not.toContain('토스 알림으로 받아보세요');
  });

  it('제재 중이면 대화함 위에서 말한다', () => {
    expect(inbox({ me: { unreadRooms: 0, restrictedUntil: null, banned: true } })).toContain('대화 기능이 정지됐어요');
  });
});

describe('책방 헤더의 대화함 진입', () => {
  it('대화를 쓸 수 있으면(inbox) 대화함 버튼과 미읽음 배지', () => {
    const html = render(<BookshopHeader onSearch={() => {}} inbox={{ unread: 4, onOpen: () => {} }} />);
    expect(html).toContain('aria-label="대화함"');
    expect(html).toContain('>4<');
    // 헤더의 수는 미읽음 **방** 수(`unreadRooms`)다 — 메시지 수로 읽히지 않게.
    expect(html).toContain('aria-label="읽지 않은 대화 4개"');
  });

  it('미읽음이 없으면 배지 없이 버튼만', () => {
    const html = render(<BookshopHeader onSearch={() => {}} inbox={{ unread: 0, onOpen: () => {} }} />);
    expect(html).toContain('aria-label="대화함"');
    expect(html).not.toContain('읽지 않은');
  });

  it('대화가 꺼져 있으면(inbox 없음) 진입이 아예 없다', () => {
    expect(render(<BookshopHeader onSearch={() => {}} />)).not.toContain('대화함');
  });
});

function profile(extra: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    loginId: 'nabi',
    nickname: '나비독서',
    profileCharacterCode: null,
    followerCount: 3,
    followingCount: 5,
    following: true,
    self: false,
    personality: null,
    personalityTags: [],
    books: [],
    followsMe: true,
    dmAvailable: true,
    ...extra,
  };
}

/** `onMessage: null` = 대화가 꺼진 App(프롭 자체를 안 준다). 기본값 인자라 `undefined`로는 끌 수 없어 null로 받는다. */
function card(p: ProfileResponse, onMessage: (() => void) | null = () => {}) {
  return render(
    <ProfileCard
      profile={p}
      books={[]}
      activeTag={null}
      now={0}
      busy={false}
      personalityStatus={null}
      adBusy={false}
      earnedRetry={false}
      personalityNotice={null}
      archiveOpen={false}
      onArchive={() => {}}
      onSelectPersonality={() => {}}
      onClaimPersonality={() => {}}
      onRetryPersonality={() => {}}
      onFollowToggle={() => {}}
      onSelectTag={() => {}}
      statusFilter={null}
      onSelectStatus={() => {}}
      onMore={() => {}}
      safety={null}
      onMessage={onMessage ?? undefined}
      tab="books"
      onSelectTab={() => {}}
      margins={null}
      marginsError={null}
      onRetryMargins={() => {}}
      expanded={new Set()}
      onToggleExpand={() => {}}
    />,
  );
}

const HINT = '서로 팔로우하면 메시지를 보낼 수 있어요';
/** 맞팔인데 잠긴 경우(주로 웹 전용 상대) — 사람에 대한 단정이 아니라 규칙 문장이라 제재·기타 사유에도 거짓이 아니다. */
const TOSS_HINT = '대화는 두 사람 모두 토스에서 북타이머를 쓸 때 열려요';

/** 종이비행기 버튼의 여는 태그 — 없으면 null. 눌리는지는 이 태그의 `disabled`로 가른다(정적 렌더라 클릭은 못 돈다). */
function planeButton(html: string): string | null {
  return html.match(/<button[^>]*aria-label="메시지 보내기"[^>]*>/)?.[0] ?? null;
}

describe('남의 책방 종이비행기 버튼 (canMessage)', () => {
  it('맞팔 + dmAvailable이면 눌리는 종이비행기가 선다(양성 대조군)', () => {
    expect(canMessage(profile())).toBe(true);
    const html = card(profile());
    expect(planeButton(html)).not.toBeNull();
    expect(planeButton(html)).not.toContain('disabled');
    // 이모지가 아니라 선 아이콘이다(기본 이모지 금지) — 버튼 바로 안에 svg.
    expect(html).toMatch(/aria-label="메시지 보내기"[^>]*><svg/);
    expect(html).not.toContain(HINT);
    expect(html).not.toContain(TOSS_HINT);
  });

  it('내가 팔로우만 하면(상대가 안 함) 버튼은 보이되 잠기고, 안내 한 줄', () => {
    const p = profile({ followsMe: false });
    expect(canMessage(p)).toBe(false);
    expect(planeButton(card(p))).toContain('disabled');
    expect(card(p)).toContain(HINT);
    expect(card(p)).not.toContain(TOSS_HINT);
  });

  it('상대만 나를 팔로우해도 잠긴다', () => {
    const p = profile({ following: false });
    expect(canMessage(p)).toBe(false);
    expect(planeButton(card(p))).toContain('disabled');
    expect(card(p)).toContain(HINT);
  });

  it('맞팔이어도 dmAvailable이 아니면(웹 전용 상대) 잠기고, 맞팔 안내 대신 토스 안내를 띄운다', () => {
    const p = profile({ dmAvailable: false });
    expect(canMessage(p)).toBe(false);
    expect(planeButton(card(p))).toContain('disabled');
    expect(card(p)).not.toContain(HINT); // 이미 맞팔이라 「서로 팔로우하면」은 거짓이다
    expect(card(p)).toContain(TOSS_HINT);
  });

  it('옛 서버(dmAvailable 없음)도 잠긴다', () => {
    expect(canMessage(profile({ dmAvailable: undefined }))).toBe(false);
  });

  it('대화가 꺼져 있으면(onMessage 없음) 버튼도 안내도 없다', () => {
    const html = card(profile({ followsMe: false }), null);
    expect(planeButton(html)).toBeNull();
    expect(html).not.toContain(HINT);
    expect(planeButton(card(profile(), null))).toBeNull();
    expect(card(profile({ dmAvailable: false }), null)).not.toContain(TOSS_HINT);
  });

  it('내 책방엔 둘 다 없다', () => {
    const html = card(profile({ self: true, following: false, followsMe: false }));
    expect(planeButton(html)).toBeNull();
    expect(html).not.toContain(HINT);
    expect(card(profile({ self: true, dmAvailable: false }))).not.toContain(TOSS_HINT);
  });
});

/** 보내는 동안 입력창이 열려 있다 — 그 사이 친 글자를 성공 콜백이 통째로 지우면 안 된다(effect라 소스로 잠근다). */
describe('보내기 성공 뒤 입력창 비우기', () => {
  const flat = readFileSync(new URL('./screens/Chat.tsx', import.meta.url), 'utf8').replace(/\s+/g, ' ');

  it('보낸 글과 같을 때만 비운다', () => {
    expect(flat).toContain("setDraft((d) => (d.trim() === text ? '' : d));");
  });
});

/**
 * Soft 재테마(PR-4) — 대화 화면. 입력줄은 눌린 면(규칙 3 「입력 자리는 파인다」), 말풍선은 내 것 옅은 세이지 ·
 * 남의 것 눌린 바탕이고 <b>그림자가 없다</b>(반복 요소 — 그림자 예산 §6). 대화함 행도 반복 행이다.
 */
describe('Soft 표면 — 대화 (PR-4)', () => {
  const bubbles = (html: string) => html.match(/<div[^>]*data-bubble="(mine|theirs)"[^>]*>/g) ?? [];

  it('입력줄은 눌린 면이다 — 연필선이 아니다', () => {
    const textarea = tagWith(roomView(), '<textarea');
    expect(textarea).not.toBe('');
    expect(textarea).toContain('box-shadow:var(--dentShadow');
    expect(textarea).toContain('border-radius:20px'); // 입력 반경은 DENT 기본 20(설계 §3-1)
    expect(textarea).not.toContain('border-image');
  });

  it('잠긴 방의 입력줄은 파인 자리를 거둔다 — 평평한 흐린 면 + 흐린 글자', () => {
    const textarea = tagWith(roomView({ writable: false }), '<textarea');
    expect(textarea).toContain('disabled'); // 정말 잠긴 렌더다
    expect(textarea).toContain('background:var(--adaptiveGrey200');
    expect(textarea).toContain('box-shadow:none');
    expect(textarea).toContain('color:var(--adaptiveGrey600');
  });

  it('내 말풍선은 옅은 세이지, 남의 말풍선은 눌린 바탕이다', () => {
    const list = bubbles(roomView());
    expect(list).toHaveLength(2);
    expect(list.find((b) => b.includes('data-bubble="mine"'))).toContain('background:var(--adaptiveBlue50,');
    expect(list.find((b) => b.includes('data-bubble="theirs"'))).toContain('background:var(--softDent');
  });

  it('말풍선엔 큰 그림자가 없다 — 메시지 수만큼 반복되는 요소다', () => {
    const list = bubbles(roomView({ messages: [message(1), message(2, { mine: true }), message(3)] }));
    expect(list).toHaveLength(3);
    for (const b of list) expect(bigShadow(b)).toBe(false);
  });

  it('대화함 행은 옅은 실선 행이고 큰 그림자가 없다', () => {
    const html = inbox({ rooms: [room(1), room(2)] });
    const rows = (html.match(/<button[^>]*>/g) ?? []).filter((t) => t.includes('text-align:left'));
    expect(rows).toHaveLength(2);
    for (const row of rows) {
      expect(row).toContain('1.5px solid var(--adaptiveGrey200');
      expect(bigShadow(row)).toBe(false);
    }
  });

  it('종이비행기 버튼은 1.5px 세이지 실선이다', () => {
    const plane = planeButton(card(profile()));
    expect(plane).not.toBeNull();
    expect(plane).toContain('1.5px solid var(--adaptiveBlue700');
    expect(plane).not.toContain('border-image');
  });
});
